package com.billding

import java.util.UUID

import com.billding.physics.{RingPath, Spatial}
import com.billding.traffic.{CompletionTally, Lane, TrackLane, TrackRoad}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import squants.motion.KilometersPerHour
import squants.space.{Kilometers, Meters}
import squants.time.{Milliseconds, Seconds}
import squants.{Time, Velocity}

/**
  * Counting the traffic that gets through: cars off the end of a street, and cars past the
  * line on a ring.
  *
  * The counts are the only numbers on the page that accumulate, which makes them the only ones
  * that can be quietly wrong for a long time before anybody notices - a lap missed here and
  * there reads exactly like traffic that is a bit slower than you thought. So these check them
  * against something known independently: how many laps' worth of road the cars have covered,
  * and how many cars a source put onto a road they all drove off the end of.
  */
class CompletedCarsSpec extends AnyFlatSpec with Matchers {

  private val Circumference = Meters(400)
  private val ring = RingPath.ofCircumference(Circumference)
  private val speedLimit: Velocity = KilometersPerHour(45)
  private val dt: Time = Milliseconds(100)

  private def tick(lane: TrackLane, times: Int): TrackLane =
    (1 to times).foldLeft(lane)((current, _) => TrackLane.update(current, dt))

  /**
    * Laps counted the other way round: by watching arc length fall.
    *
    * The counting line sits at arc length nought, so on a closed lane a car whose position
    * has gone down is a car that has been over it - traffic never reverses, so there is no
    * other way for that to happen. It is the same fact as [[TrackLane.passes]] arrived at
    * without the forward-gap arithmetic, which is the part worth checking.
    */
  private def wrapsWhile(lane: TrackLane, times: Int): Int =
    (1 to times).foldLeft((lane, 0)) {
      case ((current, wraps), _) =>
        val next = TrackLane.update(current, dt)
        val wrapped = current.vehicles.zip(next.vehicles).count {
          case (before, after) => after.s < before.s
        }
        (next, wraps + wrapped)
    }._2

  "A ring lane" should "count nothing before anybody has gone round" in {
    val lane = TrackLane.evenlySpaced(ring, 8, speedLimit, speedLimit)

    lane.passes shouldBe 0
    // The car at arc length nought starts on the line, and standing on it is not crossing it.
    tick(lane, 1).passes shouldBe 0
  }

  it should "count one car per lap of free-flowing traffic" in {
    val cars = 8
    val lane = TrackLane.evenlySpaced(ring, cars, speedLimit, speedLimit)
    val ticks = (Circumference / speedLimit * 3 / dt).toInt // about three laps' worth

    tick(lane, ticks).passes shouldBe wrapsWhile(lane, ticks)

    // And it is three laps of eight cars, near enough: the traffic settles a little under the
    // limit, so this is the right order of magnitude rather than exactly 24.
    tick(lane, ticks).passes should (be >= 20 and be <= cars * 3)
  }

  /**
    * Stopped traffic is where a naive check goes wrong: a car sitting on the line has a forward
    * gap of nothing to it, and counting that as arrival would tick the counter every frame for
    * as long as the jam lasted.
    */
  it should "not count a car that has stopped on the line" in {
    val lane = TrackLane
      .evenlySpaced(ring, 8, speedLimit, speedLimit)
      .withVehicleSlowedTo(0, squants.motion.MetersPerSecond(0))

    // The stopped car is the one at arc length nought, and the ring is empty enough that the
    // seven behind it have most of a lap to cover before any of them reaches the line.
    tick(lane, 20).passes shouldBe 0
  }

  "A two-lane ring" should "add its lanes' counts up into laps of the road" in {
    val road = TrackRoad.ring(Circumference, List(6, 4), speedLimit, speedLimit)

    road.completed shouldBe 0

    val lapTime = Circumference / speedLimit
    val after = (1 to (lapTime * 2 / dt).toInt).foldLeft(road)((r, _) => TrackRoad.update(r, dt))

    // Two laps of ten cars, give or take the inner lane being shorter and the traffic having
    // moved between lanes - so the count is bounded rather than exact.
    after.completed should be >= 15
    after.completed should be <= 25
  }

  /**
    * Checked against the cars that actually vanished, by name.
    *
    * The lane also drops cars when it is holding more than it will hold, and those have not
    * finished anything - so what this pins down is that the count is the cars that left by
    * driving to the end. A half kilometre at three-second spacing is nowhere near the overflow
    * limit, so on this road the two sets are the same set, and the count says which it means.
    */
  "A street lane" should "count the cars that drive off the end of it" in {
    val beginning = Spatial((0, 0, 0, Kilometers))
    val end = Spatial((0.5, 0, 0, Kilometers))
    val lane = Lane(Seconds(3), beginning, end, speedLimit, Nil)

    lane.completed shouldBe 0

    // Long enough for a good few to be produced and to drive the half kilometre.
    val (after, departed, _) =
      (1 to 2000).foldLeft((lane, Set.empty[UUID], Seconds(0): Time)) {
        case ((current, gone, t), _) =>
          val next = Lane.update(current, t, dt)
          val leavers = current.vehicles.map(_.uuid).toSet -- next.vehicles.map(_.uuid).toSet
          (next, gone ++ leavers, t + dt)
      }

    after.completed shouldBe departed.size
    after.completed should be > 0
    after.vehicles should not be empty // still traffic on the road, so it isn't counting those
  }

  "A tally" should "report nothing until there is enough time behind it" in {
    val tally = CompletionTally().observing(1, Seconds(0.1))

    tally.total shouldBe 1
    tally.perMinute shouldBe None
  }

  it should "report the recent rate rather than the whole run's average" in {
    // Twenty cars in the first ten seconds of watching, then a minute of nothing at all.
    val busy = (1 to 20).foldLeft(CompletionTally().observing(0, Seconds(0))) {
      case (tally, car) => tally.observing(car, Seconds(car * 0.5))
    }

    busy.perMinute.value shouldBe 120.0 +- 1e-6 // 20 cars in the 10s it has been watching

    val quiet = busy.observing(20, Seconds(70))
    quiet.total shouldBe 20
    quiet.perMinute.value shouldBe 0.0 // the busy spell has fallen out of the window
  }

  it should "measure over the window once it has one, not over the whole run" in {
    // A car a second for two minutes: far longer than the window, and a steady 60 a minute.
    val steady = (1 to 120).foldLeft(CompletionTally()) {
      case (tally, car) => tally.observing(car, Seconds(car))
    }

    steady.total shouldBe 120
    steady.perMinute.value shouldBe 60.0 +- 2.0
    steady.recent.size should be <= 31 // only the window's worth is kept
  }

  private implicit class Certainly[A](option: Option[A]) {
    def value: A = option.getOrElse(fail("expected a reading, and there wasn't one"))
  }
}
