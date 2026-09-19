package com.billding.uimodules

import com.billding.{NamedScene, SerializationFeatures}
import com.billding.physics.{PathExtent, Spatial}
import com.billding.svgRendering.Camera
import com.billding.traffic.{
  CompletionTally,
  IntelligentDriverModelImpl,
  Lane,
  MOBIL,
  NetworkScene,
  PilotedVehicle,
  RingScene,
  Scene,
  StreetScene,
  TrackRoad
}
import com.raquo.laminar.api.L.{Signal, Var}
import squants.motion.Distance
import squants.space.Meters
import squants.{QuantityVector, Time}
import squants.motion.{KilometersPerHour, Velocity}
import squants.time.{Milliseconds, Seconds}
import play.api.libs.json.Format

trait Serialization {
  val serializeScene: Var[Boolean] = Var(false)
  val deserializeScene: Var[Boolean] = Var(false)
}

/**
  * W8: the simulation advances in fixed 0.1s steps regardless of how often the page renders.
  *
  * A `requestAnimationFrame` callback reports wall-clock time, not simulation time, and it
  * fires however often the browser feels like - a smooth tab calls back every ~16ms, a
  * throttled or backgrounded one might not call back for seconds. Feeding that elapsed time
  * straight into the scene would make traffic throughput and merge decisions depend on frame
  * rate, which is exactly what the plan rules out.
  *
  * So wall time is banked in an accumulator instead, and the scene advances by whole fixed
  * steps drawn from the bank. [[accumulate]] is the pure arithmetic of that bank: how many
  * whole steps a given amount of newly-elapsed time (plus whatever was left over last time)
  * buys, and what's left over afterwards. It has no DOM and no animation frame in it, so it's
  * testable on its own.
  */
object Timestep {

  /** W8's fixed step. */
  val FixedStep: Time = Milliseconds(100)

  /** W8's bound on catch-up: however long a stall was, at most this many steps run for it. */
  val MaxStepsPerFrame: Int = 5

  /**
    * How many whole `step`s to run for this frame, and what remains for next time.
    *
    * `remainder` is the leftover from last time (always less than one `step`); `elapsed` is
    * the wall time reported for this frame. Together they're spent on as many whole steps as
    * they'll buy, up to `maxSteps` - a stall long enough to want more than that is not made
    * up later: the steps beyond the cap are spent, not banked, so the remainder coming back
    * out is always less than one `step` too. That's what keeps a backgrounded tab falling
    * behind by a bounded amount instead of either freezing on resume (running every missed
    * step at once) or slowly crawling back to real time over many later frames.
    */
  def accumulate(
    remainder: Time,
    elapsed: Time,
    step: Time = FixedStep,
    maxSteps: Int = MaxStepsPerFrame
  ): (Int, Time) = {
    val total = remainder + elapsed
    val rawSteps = math.floor(total / step).toInt.max(0)
    val steps = rawSteps.min(maxSteps)
    val spent = step * rawSteps.toDouble
    (steps, total - spent)
  }
}

case class Disruptions(
  disruptLane: Var[Boolean] = Var(false),
  disruptLaneExisting: Var[Boolean] = Var(false),
  forceLaneChange: Var[Boolean] = Var(false)
)

trait ModelTrait {
  def togglePause(): Unit
  def pause(): Unit

  /** `elapsed` is wall-clock time since the last frame callback; see [[Timestep]]. */
  def respondToAllInput(elapsed: Time)(implicit format: Format[StreetScene]): Unit
}

/**
  * Look into nesting more specific...Modules?
  * A 1-level list of Vars is already becoming unwieldy, and it's not sustainable.

  Thoughts about Vars/Rxs
    I think they should *only* relate to input, and not go down into any of the classes inside a
    given Scene, which does *not* interact directly with the user. The page model does, not the scene.
  */
case class Model(
  originalScene: Scene,
  preloadedScenes: List[NamedScene] = List(),
  serializationFeatures: SerializationFeatures,
  // These should probably be gleaned from the scene itself.
  speed: Var[Velocity] = Var(KilometersPerHour(50)),
  paused: Var[Boolean] = Var(false),
  resetScene: Var[Boolean] = Var(false),
  vehicleCount: Var[Int] = Var(0),
  disruptions: Disruptions = Disruptions(),
  /** 0 to 1, where 1 takes any gap that leaves the driver the least bit better off. */
  laneChangeEagerness: Var[Double] = Var(0.5),
  /** 0 to 1, how much of the cost to everyone else a driver counts against its own gain. */
  politeness: Var[Double] = Var(MOBIL.DefaultPoliteness),
  /**
    * 0 to 1, how often drivers change lanes for no reason at all.
    *
    * Off to begin with, so the road you land on is still one where every change that happens
    * is a change somebody could account for. It is worth turning up: it is the only control
    * here that gets the tightly packed presets moving, because it is the only one that does
    * not ask whether the move is worth making.
    */
  whimsy: Var[Double] = Var(0.0)
) extends Serialization
    with ModelTrait {
  // TODO Make this private
  val sceneVar: Var[Scene] = Var(originalScene)

  /**
    * The viewport onto a network scene (I3), kept beside `sceneVar` rather than folded into it
    * - see [[com.billding.svgRendering.Camera]]'s own doc for why: a camera that lived inside
    * the scene would be thrown away and rebuilt every tick, exactly the churn that would make a
    * pan or a pinch jump back to wherever the scene last refit itself.
    *
    * Only [[NetworkScene]] ever reads this - a ring or a street still lay themselves out fresh
    * every tick the way [[Scene.project]] always has, untouched by anything below. The value
    * here at construction is a placeholder; [[fitCameraToScene]] below replaces it with a real
    * fit the moment a network scene is actually loaded.
    */
  val camera: Var[Camera] = Var(
    Camera(QuantityVector[Distance](Meters(0), Meters(0), Meters(0)), 1.0)
  )

  /**
    * The canvas size last reported from the DOM, in the same pixel units [[Scene.project]] and
    * [[Camera.projection]] take. Model has no window of its own to measure, so [[Client]] hands
    * this over roughly once a frame from `Client`, via [[noteCanvasSize]]; [[fitCameraToScene]] borrows
    * whatever was last reported to frame a freshly loaded network. W7's 360 CSS px portrait
    * target until the page has reported anything at all.
    */
  private val lastCanvasSize: Var[(Int, Int)] = Var((360, 360))

  def noteCanvasSize(pixelWidth: Int, pixelHeight: Int): Unit =
    lastCanvasSize.set((pixelWidth, pixelHeight))

  /**
    * Frame `scene`'s own extent, if it is a network with one to fit - a ring or a street's
    * camera is never read, so there is nothing to do for those, and a network with no sections
    * at all has no extent to fit either.
    */
  private def fitCameraToScene(scene: Scene): Unit = scene match {
    case network: NetworkScene =>
      PathExtent.covering(network.network.sections.values.map(_.path)).foreach { extent =>
        val (pixelWidth, pixelHeight) = lastCanvasSize.now()
        camera.set(Camera.fitting(extent, pixelWidth, pixelHeight, NetworkScene.Padding))
      }
    case _ => ()
  }

  fitCameraToScene(originalScene)

  /**
    * Which touch (by the browser's own identifier) is at which pixel position, as of the last
    * touchstart/touchmove/touchend heard - see [[touchChanged]] and [[touchMoved]]. Two of
    * these drive the camera; any other count (0, 1, or 3+) drives nothing, which is what keeps
    * a spare finger from disturbing an ongoing pinch and keeps a single finger free for phase
    * J's editor gestures.
    */
  private val activeTouches: Var[Map[Int, (Double, Double)]] = Var(Map.empty)

  /**
    * A finger touching down or lifting off. Never moves the camera by itself - it only resets
    * the baseline [[touchMoved]] measures the next movement against, so gaining or losing a
    * finger changes how many fingers are down without ever being mistaken for the remaining
    * fingers having moved. That is the whole of what keeps a gesture from jumping when a third
    * finger lands or one of two lifts away.
    */
  def touchChanged(touches: List[(Int, Double, Double)]): Unit =
    activeTouches.set(touches.map { case (id, x, y) => id -> (x, y) }.toMap)

  /**
    * Two fingers moving drive the camera, matched up with where they were by the browser's own
    * touch identifier - never by position or by list order, so which finger is "first" never
    * matters and a slow finger can't be mistaken for a fast one. Any other current or previous
    * touch count leaves the camera alone: one finger on the background does nothing yet (phase
    * J claims it), and a third finger simply suspends the gesture rather than steering it, per
    * the plan's two-fingers-only camera rule.
    *
    * The arithmetic itself is [[Camera.followingTouches]]; this only turns this event's touches
    * and the last-known ones into the two coordinate pairs that takes.
    */
  def touchMoved(touches: List[(Int, Double, Double)], pixelWidth: Int, pixelHeight: Int): Unit = {
    val previous = activeTouches.now()
    val current = touches.map { case (id, x, y) => id -> (x, y) }.toMap

    if (previous.size == 2 && current.keySet == previous.keySet) {
      val ids = previous.keySet.toList
      val firstId = ids.head
      val secondId = ids(1)
      camera.set(
        Camera.followingTouches(
          camera.now(),
          previous(firstId),
          previous(secondId),
          current(firstId),
          current(secondId),
          pixelWidth,
          pixelHeight
        )
      )
    }

    activeTouches.set(current)
  }

  /**
    * How much traffic the road has got through, and how fast it is getting through it.
    *
    * Kept here rather than in the scene because only half of it belongs to the simulation. The
    * running total does - a lane knows when a car has left it - and the scene is asked for that
    * every tick. The rate does not: it is that total differenced against a stretch of time the
    * page picked, which makes it a reading rather than a fact about the traffic.
    */
  val tally: Var[CompletionTally] = Var(CompletionTally())

  val completedText: Signal[String] = tally.signal.map(_.total.toString)

  /*
  Blank rather than nought for the first few seconds. A rate needs some time behind it, and
  "0/min" on a road that has plainly just started is a claim rather than a reading.
   */
  val completionRateText: Signal[String] =
    tally.signal.map(_.perMinute.fold("--")(rate => f"$rate%.0f/min"))

  /*
  Readings rather than sentences. These sit on a control the size of a fingertip, next to the
  name of the thing they are a reading of, so "Current car speed 50.0 km/h" was mostly words
  the label beside it had already said.
   */
  val carSpeedText: Signal[String] = speed.signal.map(s => f"${s.toKilometersPerHour}%.0f km/h")

  val laneChangeEagernessText: Signal[String] =
    laneChangeEagerness.signal.map(e => f"${e * 100}%.0f%%")

  val politenessText: Signal[String] = politeness.signal.map(p => f"${p * 100}%.0f%%")

  /*
  Read out as what it does rather than as a percentage of an abstraction. "20%" of whimsy is
  20% of a number the reader has no way to picture; a rate per driver per hour is the thing
  itself, and is a number you can check against the road in front of you.
   */
  val whimsyText: Signal[String] = whimsy.signal.map { w =>
    val perHour = TrackRoad.whimsyFor(w) * 60
    if (perHour <= 0) "off" else f"$perHour%.0f/h each"
  }

  // A ring has no source, so it has no timing to report - fall back to a sane slider value.
  val carTiming: Var[Time] = Var(originalScene.sourceTiming.getOrElse(Seconds(3)))

  /**
    * How tightly packed the traffic is, in cars per kilometre of lane.
    *
    * Taken from whatever scene is on screen rather than kept at a value of its own, because
    * the alternative is a control that quietly overrules the scene picker: load a preset
    * chosen for its density and the dial would immediately drag it back to wherever it was
    * last left, which is a scene button that does not do what it says.
    */
  val density: Var[Double] =
    Var(originalScene.density.getOrElse(TrackRoad.Sparsest))

  val densityText: Signal[String] = density.signal.map(d => f"$d%.0f/km")

  val carTimingText: Signal[String] = carTiming.signal.map(t => f"${t.toSeconds}%.1f s")

  val pauseText: Signal[String] = paused.signal.map(p => if (p) "Play" else "Pause")

  /**
    * Which of the presets is on screen, so the picker can show you where you already are.
    *
    * Found by looking rather than passed in, because the scene the page starts on is one of
    * the presets and there is no sense in naming it twice.
    */
  val currentSceneName: Var[Option[String]] =
    Var(preloadedScenes.find(_.scene == originalScene).map(_.name))

  def togglePause(): Unit =
    paused.set(!paused.now())

  def pause(): Unit =
    paused.set(true)

  def loadNamedScene(name: String): Unit =
    preloadedScenes.find(_.name == name) match {
      case Some(named) =>
        loadScene(named.scene)
        currentSceneName.set(Some(name))
      case None =>
        println("couldn't find a matching scene for name: " + name)
    }

  /**
    * Picking a scene starts it, rather than laying it out and waiting to be told.
    *
    * Loading used to pause, which meant every preset opened as a still photograph and the
    * next thing you did was hunt for the button that made it move. Traffic that isn't moving
    * has nothing to say. Anything that genuinely wants a frozen scene pauses after loading
    * one, which is the honest way round: this is what picking a scene means.
    */
  def loadScene(scene: Scene): Unit = {
    sceneVar.set(scene)
    scene.sourceTiming.foreach(carTiming.set)
    scene.density.foreach(density.set)
    tally.set(CompletionTally())
    paused.set(false)
    fitCameraToScene(scene)
  }

  private def reset: Unit = {
    sceneVar.set(originalScene)
    originalScene.density.foreach(density.set)
    tally.set(CompletionTally())
    resetScene.set(false)
    fitCameraToScene(originalScene)
  }

  private def resetIfNecessary(): Unit =
    if (resetScene.now() == true) {
      reset
    }

  val car: PilotedVehicle =
    PilotedVehicle.commuter2(Spatial.BLANK, new IntelligentDriverModelImpl, Spatial.BLANK)

  private def disrupt(lane: Lane): Lane = {
    this.disruptions.disruptLane.set(false)
    lane.addDisruptiveVehicle(car)
  }

  def disruptLane(lane: Lane): Lane =
    if (this.disruptions.disruptLane.now() == true)
      disrupt(lane)
    else
      lane

  private def disruptExisting(lane: Lane): Lane = {
    this.disruptions.disruptLaneExisting.set(false)
    lane.disruptVehicles()
  }

  def disruptLaneExisting(lane: Lane): Lane =
    if (this.disruptions.disruptLaneExisting.now() == true)
      disruptExisting(lane)
    else
      lane

  private def updateLane(lane: Lane): Lane = {
    val laneAfterDisruption = disruptLane(lane)
    val laneAfterDisruptionExisting = disruptLaneExisting(laneAfterDisruption)

    val newSource =
      laneAfterDisruptionExisting.vehicleSource
        .copy(spacingInTime = this.carTiming.now())
        .updateSpeed(this.speed.now())
    laneAfterDisruptionExisting.copy(vehicleSource = newSource)
  }

  private def updateScene(speedLimit: Velocity) =
    sceneVar.set(sceneVar.now().updateWithSpeedLimit(speedLimit))

  /**
    * Feed this tick's control settings into the scene, which each shape of road takes
    * differently: a street passes them to the source that makes new cars, while a ring has
    * no source, so the speed control lands on the limit its fixed population drives to.
    */
  private def applyInputTo(scene: Scene): Scene = scene match {
    case street: StreetScene => street.updateAllStreets(this.updateLane)
    case ring: RingScene =>
      val disrupted = forceLaneChangeIfRequested(brakeOneCarIfRequested(ring))
      val settings = disrupted.road
        .withSpeedLimit(this.speed.now())
        .copy(
          mobil = this.laneChangeRules,
          whimsy = TrackRoad.whimsyFor(this.whimsy.now())
        )
      disrupted.copy(road = TrackRoad.approaching(settings, this.density.now()))
    // A network scene has no lanes to disrupt, and its `Source`s (card E1) carry their own
    // fixed arrival rate rather than reading one off a control - so, for now, the speed
    // control is the only dial that reaches it. `network.copy` here only ever touches
    // `speedLimit`; whatever `sources` the scene already has ride along unchanged, which is
    // what carries a `Source`'s `seed` forward from tick to tick rather than restarting it.
    case network: NetworkScene => network.copy(speedLimit = this.speed.now())
  }

  /**
    * The lane-change model the sliders currently describe.
    *
    * Rebuilt every tick rather than held onto, so moving a slider changes what the drivers
    * do from the next tick on - the whole point of the control is watching the traffic
    * respond to it without a reset.
    */
  private def laneChangeRules: MOBIL =
    MOBIL(
      politeness = this.politeness.now(),
      threshold = MOBIL.thresholdFor(this.laneChangeEagerness.now())
    )

  private def brakeOneCarIfRequested(ring: RingScene): RingScene =
    if (this.disruptions.disruptLaneExisting.now() == true) {
      this.disruptions.disruptLaneExisting.set(false)
      ring.brakeOneCar()
    } else ring

  private def forceLaneChangeIfRequested(ring: RingScene): RingScene =
    if (this.disruptions.forceLaneChange.now() == true) {
      this.disruptions.forceLaneChange.set(false)
      ring.forceLaneChange()
    } else ring

  private def updateLanesAndScene(): Unit = {
    val newScene = applyInputTo(this.sceneVar.now())
    this.sceneVar.set(newScene)
    this.updateScene(this.sceneVar.now().speedLimit)
    this.countFinishers()
  }

  /**
    * Wall time not yet spent on a fixed step, carried from frame to frame.
    *
    * Only grows while the scene is actually running - a paused scene doesn't accumulate a
    * backlog of steps to unleash the moment it's unpaused.
    */
  private val elapsedRemainder: Var[Time] = Var(Milliseconds(0))

  /**
    * Turn this frame's elapsed wall time into whole fixed steps and run them.
    *
    * The DOM-driven caller only has to measure elapsed time between callbacks and hand it
    * here; how many times the scene actually advances - zero, one, or up to
    * [[Timestep.MaxStepsPerFrame]] - is decided by the pure [[Timestep.accumulate]].
    */
  private def stepSimulation(elapsed: Time): Unit =
    if (this.paused.now() == false) {
      val (steps, remainder) = Timestep.accumulate(this.elapsedRemainder.now(), elapsed)
      this.elapsedRemainder.set(remainder)
      (1 to steps).foreach(_ => this.updateLanesAndScene())
    }

  /**
    * Bring the tally up to date with the scene, once the scene has been advanced.
    *
    * Only while running, because a paused road is not finishing cars and its clock is not
    * moving either - counting a stopped simulation would drag the rate towards nothing for as
    * long as you left it paused.
    */
  private def countFinishers(): Unit = {
    val scene = this.sceneVar.now()
    this.tally.update(_.observing(scene.completed, scene.t))
  }

  def respondToAllInput(elapsed: Time)(implicit format: Format[StreetScene]): Unit = {
    this.resetIfNecessary()
    this.stepSimulation(elapsed)
    serializationFeatures.serializeIfNecessary(this)
    serializationFeatures.deserializeIfNecessary(this)
  }

}
