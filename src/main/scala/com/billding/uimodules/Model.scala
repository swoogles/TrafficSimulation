package com.billding.uimodules

import com.billding.{NamedScene, SerializationFeatures}
import com.billding.physics.Spatial
import com.billding.traffic.{
  IntelligentDriverModelImpl,
  Lane,
  MOBIL,
  PilotedVehicle,
  RingScene,
  Scene,
  StreetScene,
  TrackRoad
}
import com.raquo.laminar.api.L.{Signal, Var}
import squants.Time
import squants.motion.{KilometersPerHour, Velocity}
import squants.time.Seconds
import play.api.libs.json.Format

trait Serialization {
  val serializeScene: Var[Boolean] = Var(false)
  val deserializeScene: Var[Boolean] = Var(false)
}

case class Disruptions(
  disruptLane: Var[Boolean] = Var(false),
  disruptLaneExisting: Var[Boolean] = Var(false),
  forceLaneChange: Var[Boolean] = Var(false)
)

trait ModelTrait {
  def togglePause(): Unit
  def pause(): Unit
  def respondToAllInput()(implicit format: Format[StreetScene]): Unit
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
    paused.set(false)
  }

  private def reset: Unit = {
    sceneVar.set(originalScene)
    resetScene.set(false)
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
      disrupted.copy(
        road = disrupted.road
          .withSpeedLimit(this.speed.now())
          .copy(
            mobil = this.laneChangeRules,
            whimsy = TrackRoad.whimsyFor(this.whimsy.now())
          )
      )
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

  private def updateLanesAndScene(): Unit =
    if (this.paused.now() == false) {
      val newScene = applyInputTo(this.sceneVar.now())
      this.sceneVar.set(newScene)
      this.updateScene(this.sceneVar.now().speedLimit)
    }

  def respondToAllInput()(implicit format: Format[StreetScene]): Unit = {
    this.resetIfNecessary()
    this.updateLanesAndScene()
    serializationFeatures.serializeIfNecessary(this)
    serializationFeatures.deserializeIfNecessary(this)
  }

}
