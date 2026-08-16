package com.billding

import com.billding.physics.{RingPath, Spatial}
import com.billding.svgRendering.Motion
import com.billding.traffic.{RingScene, TrackLane, TrackRoad}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import squants.motion.{KilometersPerHour, MetersPerSecond, MetersPerSecondSquared}
import squants.space.{Kilometers, Meters}
import squants.time.{Milliseconds, Seconds}

/**
  * Colour is how the wave becomes visible. The speeds were always in the model; what was
  * missing was any way to see, on the screen, which part of the pack is braking.
  */
class MotionColouringSpec extends AnyFlatSpec with Matchers {

  private val dt = Milliseconds(100)
  private val limit = KilometersPerHour(45)
  private val ring = RingPath.ofCircumference(Meters(400))

  private def scene(cars: Int): RingScene =
    RingScene(
      TrackRoad(List(TrackLane.evenlySpaced(ring, cars, limit, limit))),
      Seconds(0),
      dt
    )

  private def tick(start: RingScene, times: Int): RingScene =
    (1 to times).foldLeft(start)((current, _) => current.updateWithSpeedLimit(limit))

  "A driver" should "read as accelerating, braking or steady" in {
    val moving = MetersPerSecond(10)

    Motion.of(moving, MetersPerSecondSquared(1.5)) shouldBe Motion.Accelerating
    Motion.of(moving, MetersPerSecondSquared(-1.5)) shouldBe Motion.Braking
    Motion.of(moving, MetersPerSecondSquared(0)) shouldBe Motion.Steady
  }

  it should "read as steady while making the small corrections either side of a settled speed" in {
    val moving = MetersPerSecond(10)

    Motion.of(moving, MetersPerSecondSquared(0.1)) shouldBe Motion.Steady
    Motion.of(moving, MetersPerSecondSquared(-0.1)) shouldBe Motion.Steady
  }

  /** A car at a standstill is left alone, however hard it is being told to brake. */
  it should "read as steady when stopped" in {
    Motion.of(MetersPerSecond(0), MetersPerSecondSquared(-3)) shouldBe Motion.Steady
    Motion.of(MetersPerSecond(0), MetersPerSecondSquared(2)) shouldBe Motion.Steady
  }

  /**
    * The canvas draws a car through a tint only when there is something to say about it, so
    * a swapped pair here would quietly paint the whole thing backwards.
    */
  "The canvas" should "reach for a tint only when a car is doing something" in {
    Window.filterFor(Motion.Accelerating) shouldBe defined
    Window.filterFor(Motion.Braking) shouldBe defined
    Window.filterFor(Motion.Accelerating) should not be Window.filterFor(Motion.Braking)
    Window.filterFor(Motion.Steady) shouldBe None
  }

  "A car on the ring" should "remember what it just did about its speed" in {
    // Starting at the speed limit is too fast to hold at this spacing, so they all lift off.
    val settling = tick(scene(16), 5)

    settling.road.vehicles.foreach { vehicle =>
      vehicle.acceleration should be < MetersPerSecondSquared(0)
    }
  }

  "Traffic that has settled" should "be drawn in its own colour, all the way round" in {
    val settled = tick(scene(8), 600)

    settled.renderables.map(_.motion).distinct shouldBe List(Motion.Steady)
  }

  /**
    * The picture the colours exist to draw: one car stops, and what you see behind it is a
    * band of red that turns green on the far side as the queue picks itself back up.
    */
  "A jam" should "show up as braking cars behind it and accelerating cars leaving it" in {
    val disturbed = tick(scene(16), 400).brakeOneCar()

    val seen = (1 to 400)
      .scanLeft(disturbed)((current, _) => current.updateWithSpeedLimit(limit))
      .flatMap(_.renderables.map(_.motion))
      .distinct
      .toSet

    seen should contain(Motion.Braking)
    seen should contain(Motion.Accelerating)
  }

  "Every preset that can change lanes" should "slide its cars across rather than teleport them" in {
    val scenes = new SampleSceneCreation(Spatial((0.5, 0, 0, Kilometers)))(dt)

    val twoLaners = List(
      scenes.quietTwoLaneRing,
      scenes.waveProneTwoLaneRing,
      scenes.lopsidedTwoLaneRing
    )

    twoLaners.foreach { named =>
      val road = named.scene.asInstanceOf[RingScene].road
      withClue(s"${named.name} changes lanes instantly: ") {
        road.laneChangeDuration shouldBe defined
      }
      withClue(s"${named.name} would still be crossing when it may change again: ") {
        road.laneChangeDuration.get should be < road.mobil.cooldown
      }
    }
  }

  /**
    * Mid-change cars are the ones that need to be findable, so the offset has to reach them
    * and then has to go away again. Followed by name, because these drivers change lanes of
    * their own accord too and any given moment may catch somebody else mid-crossing.
    */
  it should "actually leave a car between the lanes for a while after one moves" in {
    val scenes = new SampleSceneCreation(Spatial((0.5, 0, 0, Kilometers)))(dt)
    val settled = tick(scenes.quietTwoLaneRing.scene.asInstanceOf[RingScene], 100)

    val moved = settled.forceLaneChange()
    val crossing = moved.road.vehicles
      .find(_.lateral.abs > Meters(0.01))
      .getOrElse(fail("forcing a change left nobody between the lanes"))

    def offsetOf(scene: RingScene) =
      scene.road.vehicles.find(_.uuid == crossing.uuid).map(_.lateral.abs).getOrElse(Meters(-1))

    // It starts a full lane out, is partway across at one second, and has arrived by two.
    offsetOf(moved).toMeters shouldBe 6.0 +- 1e-6
    offsetOf(tick(moved, 10)).toMeters shouldBe 3.0 +- 0.35
    offsetOf(tick(moved, 21)).toMeters shouldBe 0.0 +- 1e-6
  }
}
