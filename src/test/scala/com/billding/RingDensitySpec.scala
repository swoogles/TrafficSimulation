package com.billding

import com.billding.physics.{RingPath, Spatial}
import com.billding.traffic.{RingScene, TrackLane}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import squants.motion.KilometersPerHour
import squants.space.{Kilometers, Meters}
import squants.time.Milliseconds

/**
  * A ring has a capacity, and past it the traffic doesn't jam - it stops for good and never
  * starts again. Cars are 8m long and want 6m of room at a standstill, so a 400m loop runs
  * out of road somewhere around 28 of them.
  */
class RingDensitySpec extends AnyFlatSpec with Matchers {

  private val dt = Milliseconds(100)
  private val limit = KilometersPerHour(45)

  private def settle(count: Int, seconds: Int = 30) = {
    val start = TrackLane.evenlySpaced(RingPath.ofCircumference(Meters(400)), count, limit, limit)
    (1 to seconds * 10).foldLeft(start)((lane, _) => TrackLane.update(lane, dt))
  }

  "Traffic on a ring" should "get slower as you add cars" in {
    val speeds = List(8, 16, 22).map(settle(_).meanSpeed.toKilometersPerHour)

    speeds shouldBe speeds.sorted.reverse
    speeds.last should be > 5.0 // still moving, not seized up
  }

  it should "seize up completely once there is no room left" in {
    val overloaded = settle(30)

    overloaded.meanSpeed.toKilometersPerHour shouldBe 0.0 +- 0.001
    overloaded.vehicles.count(_.speed.toMetersPerSecond < 0.01) shouldBe 30
  }

  /**
    * Guards the buttons on the page: a preset that gridlocks looks like a broken simulation
    * rather than a full one, so none of them should be over capacity.
    */
  "Every preset ring" should "still be moving after a minute" in {
    val scenes = new SampleSceneCreation(Spatial((0.5, 0, 0, Kilometers)))(dt)

    List(scenes.quietRing, scenes.busyRing, scenes.jammedRing).foreach { named =>
      val ring = named.scene.asInstanceOf[RingScene]
      val settled = (1 to 600).foldLeft(ring)((scene, _) => scene.updateWithSpeedLimit(limit))

      withClue(s"${named.name} came to a permanent stop: ") {
        settled.lane.meanSpeed.toKilometersPerHour should be > 5.0
      }
    }
  }
}
