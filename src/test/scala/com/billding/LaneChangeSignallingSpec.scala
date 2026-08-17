package com.billding

import com.billding.svgRendering.LaneChangeSignal
import com.billding.traffic.{LaneChangeIntent, MOBIL, RingScene, TrackRoad}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import squants.motion.{KilometersPerHour, MetersPerSecondSquared}
import squants.space.Meters
import squants.time.{Milliseconds, Seconds}
import squants.{Time, Velocity}

/**
  * The warning a driver gives before it changes lanes.
  *
  * Sliding a car across the line made a change followable; it did nothing about finding one.
  * On a ring of twenty cars the crossing is over by the time you have worked out which car
  * to watch, so what was missing was something that happens before anything moves.
  */
class LaneChangeSignallingSpec extends AnyFlatSpec with Matchers {

  private val Circumference = Meters(400)
  private val limit: Velocity = KilometersPerHour(45)
  private val dt = Milliseconds(100)
  private val warning = Seconds(1.2)

  /** Drivers who take any gap going and don't care who they cut up. */
  private val selfish = MOBIL(politeness = 0.0, threshold = MetersPerSecondSquared(0.05))

  /** Drivers who would never change lanes of their own accord. */
  private val orderly = MOBIL(threshold = MetersPerSecondSquared(1000))

  private def road(
    outer: Int,
    inner: Int,
    mobil: MOBIL = selfish,
    indicating: Boolean = true
  ): TrackRoad =
    TrackRoad
      .ring(Circumference, List(outer, inner), limit, limit, mobil)
      .copy(laneChangeWarning = if (indicating) Some(warning) else None)

  private def tick(start: TrackRoad, times: Int): TrackRoad =
    (1 to times).foldLeft(start)((current, _) => TrackRoad.update(current, dt))

  private def ticksIn(duration: Time): Int = (duration / dt).round.toInt

  private def laneOf(road: TrackRoad, uuid: java.util.UUID): Int =
    road.lanes.indexWhere(_.vehicles.exists(_.uuid == uuid))

  /** The first tick at which somebody is indicating, and the road as it was then. */
  private def runUpToAnIntent(start: TrackRoad, within: Int): TrackRoad =
    (1 to within)
      .scanLeft(start)((current, _) => TrackRoad.update(current, dt))
      .find(_.vehicles.exists(_.intent.isDefined))
      .getOrElse(fail("nobody indicated a lane change at all, so this proves nothing"))

  "A road with no warning period" should "decide and move in the same instant, as it always did" in {
    val plain = road(12, 6, indicating = false)

    val history = (1 to 200).scanLeft(plain)((current, _) => TrackRoad.update(current, dt))

    history.foreach { current =>
      current.vehicles.foreach(_.intent shouldBe None)
    }
    // And the changes still happen - this is the old behaviour, not the absence of behaviour.
    withClue("nobody changed lanes without a warning period, which used to be all they did: ") {
      changesIn(history) should not be empty
    }
  }

  /** Every (car, tick) at which a car came out of the tick in a different lane. */
  private def changesIn(history: Seq[TrackRoad]): List[(java.util.UUID, Int)] =
    history
      .sliding(2)
      .zipWithIndex
      .flatMap {
        case (Seq(before, after), at) =>
          after.vehicles
            .map(_.uuid)
            .filter(uuid => laneOf(before, uuid) != laneOf(after, uuid))
            .map(uuid => (uuid, at))
        case _ => Nil
      }
      .toList

  "A driver that has decided to change lanes" should "indicate before it moves" in {
    val announced = runUpToAnIntent(road(14, 4), 200)
    val indicating = announced.vehicles.filter(_.intent.isDefined)

    indicating.foreach { vehicle =>
      val intent = vehicle.intent.get
      withClue("a driver indicated a lane that isn't there: ") {
        announced.lanes.indices should contain(intent.to)
      }
      withClue("a driver started crossing before it had finished indicating: ") {
        vehicle.lateral shouldBe Meters(0)
      }
      withClue("it should be indicating a lane other than the one it is in: ") {
        laneOf(announced, vehicle.uuid) should not be intent.to
      }
    }
  }

  it should "cross once its warning is up, and stop indicating" in {
    val announced = runUpToAnIntent(road(14, 4), 200)
    val driver = announced.vehicles.find(_.intent.isDefined).get
    val wanted = driver.intent.get.to

    // Long enough for the warning to run out, whenever in it this driver happens to be.
    val after = tick(announced, ticksIn(warning) + 1)

    laneOf(after, driver.uuid) shouldBe wanted
    after.vehicles.find(_.uuid == driver.uuid).get.intent shouldBe None
  }

  it should "hold off for the whole warning rather than sneaking across early" in {
    val announced = runUpToAnIntent(road(14, 4), 200)
    val driver = announced.vehicles.find(_.intent.isDefined).get
    val startedIn = laneOf(announced, driver.uuid)
    val remaining = ticksIn(warning - driver.intent.get.elapsed)

    // A tick short of the warning it should still be where it was, still saying so.
    val justBefore = tick(announced, remaining - 1)

    laneOf(justBefore, driver.uuid) shouldBe startedIn
    justBefore.vehicles.find(_.uuid == driver.uuid).get.intent shouldBe defined
  }

  /**
    * The property the whole thing rests on, put to hundreds of changes rather than to one:
    * if a car can still cross without having said so first, then there is a lane change on
    * the screen that nothing drew your eye to, which is the state of affairs this is for.
    */
  "No lane change at all" should "happen without the full warning in front of it" in {
    val warningInTicks = ticksIn(warning)
    val history = (1 to 400).scanLeft(road(14, 6))((current, _) => TrackRoad.update(current, dt))
    val changes = changesIn(history)

    withClue("nobody changed lanes at all, so this proves nothing: ") {
      changes should not be empty
    }

    changes.foreach {
      case (uuid, at) =>
        val landedIn = laneOf(history(at + 1), uuid)
        // Every tick of the run-up, including the one the driver crossed on.
        val runUp = ((at - warningInTicks + 1) to at).map(history)

        runUp.foreach { current =>
          withClue(s"car $uuid crossed at tick $at without indicating throughout: ") {
            current.vehicles.find(_.uuid == uuid).flatMap(_.intent).map(_.to) shouldBe
            Some(landedIn)
          }
        }
    }
  }

  /**
    * The reason a warning is a promise rather than a plan: a second is long enough for the
    * gap to be taken by somebody else, and a driver that crosses anyway is a collision.
    */
  "A driver whose reasons have gone away" should "think better of it and stop indicating" in {
    val settled = tick(road(10, 6, orderly), 100)
    val (lane, index) = (0, 0)
    val hopeful = settled.lanes(lane).vehicles(index)

    // Planted directly, because orderly drivers never want a lane change in the first place.
    val announced = settled.copy(
      lanes = settled.lanes.updated(
        lane,
        settled
          .lanes(lane)
          .copy(
            vehicles = settled
              .lanes(lane)
              .vehicles
              .updated(index, hopeful.copy(intent = Some(LaneChangeIntent(1, warning, warning))))
          )
      )
    )

    val after = TrackRoad.update(announced, dt)

    laneOf(after, hopeful.uuid) shouldBe lane
    after.vehicles.find(_.uuid == hopeful.uuid).get.intent shouldBe None
  }

  /**
    * The invariant that a warning period could plausibly break: a driver decides against one
    * road and crosses onto another, a second later, that has moved on without it.
    */
  "Indicating traffic" should "still never put two cars in the same place" in {
    (1 to 600).foldLeft(road(12, 8)) { (current, _) =>
      val next = TrackRoad.update(current, dt)
      next.lanes.foreach { lane =>
        withClue(s"cars overlapped: ${lane.gaps.map(_.toMeters.round)} ") {
          lane.gaps.foreach(_ should be > Meters(0))
        }
      }
      next
    }
  }

  it should "keep every car it started with" in {
    val settled = tick(road(10, 6), 600)

    settled.vehicleCount shouldBe 16
    settled.vehicles.map(_.uuid).distinct.size shouldBe 16
  }

  "A forced lane change" should "announce itself, and then happen whatever MOBIL thinks" in {
    val settled = tick(road(11, 7, orderly), 400)
    val forced = TrackRoad.forceLaneChange(settled)

    val culprit = forced.vehicles
      .find(_.intent.isDefined)
      .getOrElse(fail("forcing a change left nobody indicating"))

    withClue("the change has to survive its own warning, or the button does nothing: ") {
      culprit.intent.get.insistent shouldBe true
    }
    forced.vehicles.count(_.intent.isDefined) shouldBe 1
    laneOf(forced, culprit.uuid) should not be culprit.intent.get.to

    // It waits on its gap rather than giving up on it, so this is a deadline, not a schedule.
    val crossed = (1 to ticksIn(warning) * 3)
      .scanLeft(forced)((current, _) => TrackRoad.update(current, dt))
      .find(current => laneOf(current, culprit.uuid) == culprit.intent.get.to)

    withClue("the button announced a change and then never made it: ") {
      crossed shouldBe defined
    }
    crossed.get.vehicles.find(_.uuid == culprit.uuid).get.intent shouldBe None
  }

  "An intent" should "run from nothing to done over its own duration" in {
    val intent = LaneChangeIntent(1, Seconds(1))

    intent.progress shouldBe 0.0
    intent.isRipe shouldBe false

    val halfway = intent.advancedBy(Seconds(0.5))
    halfway.progress shouldBe 0.5 +- 1e-9
    halfway.isRipe shouldBe false

    val done = halfway.advancedBy(Seconds(0.5))
    done.progress shouldBe 1.0
    done.isRipe shouldBe true
  }

  "The canvas" should "be told which way an indicating car is about to go" in {
    val scene = RingScene(road(11, 7, orderly), Seconds(0), dt)
    val settled = (1 to 400).foldLeft(scene)((current, _) => current.updateWithSpeedLimit(limit))

    val forced = settled.forceLaneChange()
    val culprit = forced.road.vehicles.find(_.intent.isDefined).get
    val movingInwards = culprit.intent.get.to > laneOf(forced.road, culprit.uuid)

    val drawn = forced.renderables.filter(_.signal.isDefined)
    drawn.size shouldBe 1
    drawn.head.uuid shouldBe culprit.uuid

    // The inner lanes are the ones you overtake in, so inwards is to the driver's left.
    drawn.head.signal.get.toTheLeft shouldBe movingInwards
  }

  it should "show the warning building as the moment approaches" in {
    val scene = RingScene(road(11, 7, orderly), Seconds(0), dt)
    val settled = (1 to 400).foldLeft(scene)((current, _) => current.updateWithSpeedLimit(limit))
    val forced = settled.forceLaneChange()

    def progressNow(current: RingScene): Double =
      current.renderables.flatMap(_.signal).map(_.progress).headOption.getOrElse(-1.0)

    val early = forced.updateWithSpeedLimit(limit)
    val later = (1 to 5).foldLeft(early)((current, _) => current.updateWithSpeedLimit(limit))

    progressNow(early) should be > 0.0
    progressNow(later) should be > progressNow(early)
    progressNow(later) should be < 1.0
  }

  /**
    * Which side of the car the chevrons come off is the one thing here that can be quietly
    * wrong: a sign the wrong way round still draws a convincing arrow, at the lane the driver
    * is about to leave.
    */
  "The chevrons" should "run out the side of the car the driver is heading for" in {
    val carLength = 40.0
    val carWidth = 20.0

    def apexesFor(toTheLeft: Boolean): Seq[Double] =
      (0 to 10).flatMap { step =>
        Window
          .chevronsFor(LaneChangeSignal(toTheLeft, step / 10.0), carLength, carWidth)
          .map(_.apexY)
      }

    // The car occupies 0 to carWidth across, with its left at the far end of that.
    all(apexesFor(toTheLeft = true)) should be >= carWidth
    all(apexesFor(toTheLeft = false)) should be <= 0.0
  }

  it should "travel outwards and fade as they go" in {
    val signal = LaneChangeSignal(toTheLeft = true, progress = 0.5)
    val chevrons = Window.chevronsFor(signal, 40.0, 20.0)

    chevrons.size should be > 1
    chevrons.map(_.opacity).foreach { opacity =>
      opacity should ((be >= 0.0).and(be <= 1.0))
    }

    // Whichever is furthest out is the faintest, so they read as running away from the car.
    chevrons.maxBy(_.apexY) shouldBe chevrons.minBy(_.opacity)
  }

  "Every preset that can change lanes" should "warn before it does" in {
    List(scenes.quietTwoLaneRing, scenes.waveProneTwoLaneRing, scenes.lopsidedTwoLaneRing)
      .foreach { named =>
        val preset = named.scene.asInstanceOf[RingScene].road
        withClue(s"${named.name} changes lanes with no warning: ") {
          preset.laneChangeWarning shouldBe defined
        }
        withClue(s"${named.name} warns for too little of a tick to see: ") {
          preset.laneChangeWarning.get should be >= Seconds(0.5)
        }
        withClue(s"${named.name} spends longer announcing changes than making them: ") {
          preset.laneChangeWarning.get should be < preset.laneChangeDuration.get
        }
      }
  }

  /**
    * None of this exists on a road with one lane, and the page used to open on one: a single
    * car approaching a stopped car, on a straight strip with nowhere to go. Everything the
    * simulation had learned about lane changing was a preset click away, which is the same as
    * not being there.
    */
  "The scene the page opens on" should "be a road where a lane change can happen at all" in {
    Client.model.originalScene match {
      case ring: RingScene =>
        withClue("the landing scene has nowhere to change lanes to: ") {
          ring.road.lanes.size should be > 1
        }
        ring.road.laneChangeWarning shouldBe defined
      case other =>
        fail(s"the page opens on $other, where no car can change lanes")
    }
  }

  it should "be running, rather than waiting to be started" in {
    Client.model.paused.now() shouldBe false
  }

  /**
    * A road that could change lanes but doesn't is no better to land on than a road that
    * can't. Both of the other two-lane presets are in this position: free-flowing traffic has
    * no reason to move and the standing-wave one is too tightly packed for a gap to be worth
    * taking, and neither indicates once in the first thirty seconds - about as long as anyone
    * gives a page before deciding it is a still picture.
    */
  it should "actually show somebody indicating, within half a minute of opening" in {
    val landing = Client.model.originalScene.asInstanceOf[RingScene].road
    // The sliders overwrite MOBIL every tick, so these are the drivers the page really runs.
    val asThePageRunsIt =
      landing.copy(mobil = MOBIL(politeness = 0.2, threshold = MOBIL.thresholdFor(0.5)))

    val history =
      (1 to 300).scanLeft(asThePageRunsIt)((current, _) => TrackRoad.update(current, dt))

    val indicatingTicks = history.count(_.vehicles.exists(_.intent.isDefined))

    withClue("the landing scene never announces a lane change, so it never shows one: ") {
      indicatingTicks should be > 0
    }
  }

  /** Picking a preset means "show me this", not "lay this out and wait". */
  "Loading a scene" should "start it, even if the page was paused at the time" in {
    val model = Client.model
    model.pause()

    model.loadScene(scenes.quietTwoLaneRing.scene)

    model.paused.now() shouldBe false
  }

  private lazy val scenes =
    new SampleSceneCreation(com.billding.physics.Spatial((0.5, 0, 0, squants.space.Kilometers)))(dt)
}
