package com.billding

import com.billding.physics.{RingPath, StraightPath}
import com.billding.traffic.TrackLane
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import squants.motion.{Distance, KilometersPerHour, MetersPerSecond}
import squants.space.{Length, Meters}
import squants.time.Milliseconds
import squants.{QuantityVector, Velocity}

class TrackLaneSpec extends AnyFlatSpec with Matchers {

  private val Tolerance = 1e-6
  private val Circumference = Meters(400)
  private val CarLength = Meters(8) // VehicleStats.Commuter is 8m long.

  private val ring = RingPath.ofCircumference(Circumference)
  private val speedLimit: Velocity = KilometersPerHour(45)
  private val dt = Milliseconds(100)

  private def tick(lane: TrackLane, times: Int): TrackLane =
    (1 to times).foldLeft(lane)((current, _) => TrackLane.update(current, dt))

  private def roadAccountedFor(lane: TrackLane): Length =
    lane.gaps.reduce(_ + _) + CarLength * lane.vehicles.size.toDouble

  "A ring of cars" should "start out evenly spaced" in {
    val lane = TrackLane.evenlySpaced(ring, 8, speedLimit, speedLimit)

    lane.gaps.foreach { gap =>
      gap.toMeters shouldBe 42.0 +- Tolerance // 400/8 spacing, less the car in front
    }
  }

  /**
    * The best invariant a loop gives you: every metre of road is either gap or car, always.
    * It catches wrap bugs, drift and double-counted cars in one assertion.
    */
  it should "account for every metre of the loop, tick after tick" in {
    val lane = TrackLane.evenlySpaced(ring, 8, speedLimit, speedLimit)

    (1 to 300).foldLeft(lane) { (current, _) =>
      val next = TrackLane.update(current, dt)
      roadAccountedFor(next).toMeters shouldBe Circumference.toMeters +- Tolerance
      next
    }
  }

  it should "hold uniform flow uniform" in {
    val settled = tick(TrackLane.evenlySpaced(ring, 8, speedLimit, speedLimit), 300)

    settled.gaps.foreach { gap =>
      gap.toMeters shouldBe settled.gaps.head.toMeters +- Tolerance
    }
    settled.vehicles.foreach { vehicle =>
      vehicle.speed.toMetersPerSecond shouldBe
      settled.vehicles.head.speed.toMetersPerSecond +- Tolerance
    }
  }

  it should "keep the derived Spatial out on the circle" in {
    val settled = tick(TrackLane.evenlySpaced(ring, 8, speedLimit, speedLimit), 300)

    settled.vehicles.foreach { vehicle =>
      (vehicle.piloted.spatial.r - ring.center).magnitude.toMeters shouldBe
      ring.radius.toMeters +- Tolerance
    }
  }

  /**
    * Without its own special case a lone car measures the gap to itself, finds zero, and
    * brakes to a permanent standstill.
    */
  it should "let a single car drive the empty loop freely" in {
    val alone = TrackLane.evenlySpaced(ring, 1, MetersPerSecond(0), speedLimit)

    alone.gapAhead(0).toMeters shouldBe (Circumference - CarLength).toMeters +- Tolerance

    val afterTwentySeconds = tick(alone, 200)
    afterTwentySeconds.vehicles.head.speed.toMetersPerSecond should be > 11.0
    afterTwentySeconds.vehicles.head.speed should be <= speedLimit
  }

  it should "give the leader of an open road a free road ahead" in {
    val straight = StraightPath(
      QuantityVector[Distance](Meters(0), Meters(0), Meters(0)),
      QuantityVector[Distance](Meters(500), Meters(0), Meters(0))
    )
    val cars = TrackLane
      .evenlySpaced(ring, 2, speedLimit, speedLimit)
      .vehicles
      .zip(List(Meters(200), Meters(120)))
      .map { case (vehicle, s) => vehicle.at(s, speedLimit).placedOn(straight) }

    val lane = TrackLane(straight, cars, speedLimit)

    lane.gapAhead(0) should be > Meters(1000) // nobody ahead, so nothing to react to
    lane.gapAhead(1).toMeters shouldBe 72.0 +- Tolerance // 80m apart, less the car in front
  }

  /**
    * The Sugiyama experiment: stop one car on an otherwise uniform ring and watch what the
    * cars behind it do. This is the thing a straight road can never show you - the wave the
    * disturbance creates is still going round the loop a minute later.
    */
  it should "pass a jam backwards around the loop when one car brakes" in {
    val lane = TrackLane
      .evenlySpaced(ring, 8, speedLimit, speedLimit)
      .withVehicleSlowedTo(0, MetersPerSecond(0))

    val (settled, worstGap, slowestFollower) =
      (1 to 600).foldLeft((lane, Meters(1000), speedLimit)) {
        case ((current, minGap, minSpeed), _) =>
          val next = TrackLane.update(current, dt)
          val gap = next.gaps.minBy(_.toMeters)
          // Skip the car we braked ourselves; we want to see what it did to everyone else.
          val speed = next.vehicles.tail.map(_.speed).minBy(_.toMetersPerSecond)
          (next, if (gap < minGap) gap else minGap, if (speed < minSpeed) speed else minSpeed)
      }

    println(
      s"[perturbation] smallest gap: $worstGap, slowest follower: $slowestFollower, " +
      s"mean speed after 60s: ${settled.meanSpeed}, gaps: ${settled.gaps.map(_.toMeters.round)}"
    )

    worstGap should be > Meters(0) // nobody drove through anybody
    roadAccountedFor(settled).toMeters shouldBe Circumference.toMeters +- Tolerance

    // The disturbance reached the cars behind, and is still visible a minute later.
    slowestFollower should be < speedLimit * 0.75
    val spread = settled.gaps.maxBy(_.toMeters) - settled.gaps.minBy(_.toMeters)
    spread should be > Meters(5)
  }
}
