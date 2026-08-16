package com.billding

import com.billding.physics.RingPath
import com.billding.traffic.{MOBIL, TrackLane, TrackRoad, TrackVehicle}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import squants.motion.{KilometersPerHour, MetersPerSecond, MetersPerSecondSquared}
import squants.space.{Length, Meters}
import squants.time.{Milliseconds, Seconds}
import squants.Velocity

class TrackRoadSpec extends AnyFlatSpec with Matchers {

  private val Tolerance = 1e-6
  private val Circumference = Meters(400)
  private val CarLength = Meters(8) // VehicleStats.Commuter is 8m long.
  private val LaneWidth = Meters(6)

  private val limit: Velocity = KilometersPerHour(45)
  private val dt = Milliseconds(100)

  /** Drivers who take any gap going and don't care who they cut up. */
  private val selfish = MOBIL(politeness = 0.0, threshold = MetersPerSecondSquared(0.05))

  private def road(
    outer: Int,
    inner: Int,
    mobil: MOBIL = MOBIL()
  ): TrackRoad =
    TrackRoad.ring(Circumference, List(outer, inner), limit, limit, mobil)

  private def tick(start: TrackRoad, times: Int): TrackRoad =
    (1 to times).foldLeft(start)((current, _) => TrackRoad.update(current, dt))

  private def positionsByCar(road: TrackRoad): Map[String, Int] =
    road.lanes.zipWithIndex.flatMap {
      case (lane, index) => lane.vehicles.map(vehicle => vehicle.uuid.toString -> index)
    }.toMap

  /** Every metre of a lane is either a gap or a car, however the cars got there. */
  private def roadAccountedFor(lane: TrackLane): Length =
    lane.gaps.reduce(_ + _) + CarLength * lane.vehicles.size.toDouble

  "A two lane ring" should "give the inner lane a shorter loop than the outer one" in {
    val lanes = road(6, 6).lanes

    lanes.head.path.totalLength.toMeters shouldBe 400.0 +- Tolerance
    // One lane in from a 400m loop, so one lane-width smaller in radius all the way round.
    lanes.last.path.totalLength.toMeters shouldBe
    (400.0 - 2 * math.Pi * LaneWidth.toMeters) +- Tolerance
  }

  it should "keep every car it started with" in {
    val start = road(10, 6, selfish)
    val settled = tick(start, 600)

    settled.vehicleCount shouldBe 16
    settled.vehicles.map(_.uuid).distinct.size shouldBe 16
  }

  /**
    * The invariant that catches a botched insertion.
    *
    * A car spliced into the wrong place in a lane's running order still exists and still
    * moves, so nothing looks obviously broken - but the gaps stop adding up to the loop,
    * because two cars now disagree about which of them is in front.
    */
  it should "account for every metre of both lanes, through hundreds of lane changes" in {
    val start = road(10, 6, selfish)

    (1 to 600).foldLeft(start) { (current, _) =>
      val next = TrackRoad.update(current, dt)
      next.lanes.foreach { lane =>
        roadAccountedFor(lane).toMeters shouldBe lane.path.totalLength.toMeters +- Tolerance
      }
      next
    }
  }

  /**
    * The thing that goes wrong if changes are decided but not re-examined: two drivers both
    * like the look of the same gap, both are told it is safe against a road neither has
    * moved into yet, and both take it.
    */
  it should "never let two cars occupy the same stretch of road" in {
    val start = road(12, 8, selfish)

    (1 to 600).foldLeft(start) { (current, _) =>
      val next = TrackRoad.update(current, dt)
      next.lanes.foreach { lane =>
        withClue(s"cars overlapped: ${lane.gaps.map(_.toMeters.round)} ") {
          lane.gaps.foreach(_ should be > Meters(0))
        }
      }
      next
    }
  }

  "A driver stuck in a queue" should "move over when the next lane is clear" in {
    val start = road(16, 0)

    start.lanes.last.vehicles shouldBe empty

    val settled = tick(start, 300)
    settled.lanes.last.vehicles.size should be > 0
  }

  /**
    * The safety criterion is the one MOBIL will not trade away, so a road where every gap is
    * occupied should see no changes at all however eager the drivers are made.
    */
  "A driver with nowhere safe to go" should "stay put" in {
    val packed = road(24, 24, MOBIL(politeness = 0.0, threshold = MetersPerSecondSquared(0.0)))
    val before = positionsByCar(packed)

    val after = positionsByCar(TrackRoad.update(packed, dt))

    after.count { case (uuid, lane) => before(uuid) != lane } shouldBe 0
  }

  "A car that has just changed lanes" should "settle before considering another" in {
    val start = road(14, 2, selfish)
    val history = (1 to 400).scanLeft(start)((current, _) => TrackRoad.update(current, dt))

    val changeTicksByCar: Map[String, List[Int]] = history
      .sliding(2)
      .zipWithIndex
      .flatMap {
        case (Seq(before, after), tick) =>
          val was = positionsByCar(before)
          positionsByCar(after).collect { case (uuid, lane) if was(uuid) != lane => uuid -> tick }
        case _ => Nil
      }
      .toList
      .groupBy(_._1)
      .view
      .mapValues(_.map(_._2).sorted)
      .toMap

    withClue("no car changed lanes at all, so this proves nothing: ") {
      changeTicksByCar should not be empty
    }

    val cooldownInTicks = (MOBIL.DefaultCooldown / dt).toInt
    changeTicksByCar.foreach {
      case (uuid, ticks) =>
        withClue(s"car $uuid changed lanes at ticks $ticks: ") {
          ticks.sliding(2).foreach {
            case List(earlier, later) => (later - earlier) should be >= cooldownInTicks
            case _                    => ()
          }
        }
    }
  }

  "Politeness" should "make for fewer lane changes than selfishness" in {
    def changesIn(mobil: MOBIL): Int = {
      val start = road(12, 6, mobil)
      val history = (1 to 400).scanLeft(start)((current, _) => TrackRoad.update(current, dt))
      history
        .sliding(2)
        .map {
          case Seq(before, after) =>
            val was = positionsByCar(before)
            positionsByCar(after).count { case (uuid, lane) => was(uuid) != lane }
          case _ => 0
        }
        .sum
    }

    val selfishChanges = changesIn(MOBIL(politeness = 0.0, threshold = MetersPerSecondSquared(0.1)))
    val politeChanges = changesIn(MOBIL(politeness = 1.0, threshold = MetersPerSecondSquared(0.1)))

    println(s"[politeness] selfish: $selfishChanges changes, polite: $politeChanges changes")
    selfishChanges should be > politeChanges
  }

  "Forcing a lane change" should "move exactly one car, into a gap it fits in" in {
    val start = tick(road(12, 8), 100) // Let the traffic settle before disturbing it.
    val forced = TrackRoad.forceLaneChange(start)

    val was = positionsByCar(start)
    forced.vehicleCount shouldBe start.vehicleCount
    positionsByCar(forced).count { case (uuid, lane) => was(uuid) != lane } shouldBe 1

    forced.lanes.foreach { lane =>
      lane.gaps.foreach(_ should be > Meters(0))
    }
  }

  /**
    * The whole point of the button: one driver's bad decision, and traffic that never saw
    * what caused it braking anyway.
    */
  it should "start a wave in traffic that was running smoothly" in {
    val settled = tick(road(11, 7, orderly), 400)

    withClue("the traffic was not smooth to begin with: ") {
      speedSpread(settled) shouldBe 0.0 +- 0.01
    }

    val disturbed = TrackRoad.forceLaneChange(settled)
    val (ended, worstSpread, slowest) = watch(disturbed, 300)

    println(
      f"[forced change] worst spread $worstSpread%.2f m/s, slowest car $slowest%.2f m/s, " +
      f"spread after 30s ${speedSpread(ended)}%.2f m/s"
    )

    slowest should be < settled.meanSpeed.toMetersPerSecond * 0.5 // somebody braked hard
    worstSpread should be > 5.0 // and it was not just the one car
  }

  /**
    * Below a certain density traffic absorbs a jolt; above it, the jolt feeds itself. This is
    * the standing wave the ring exists to show, and the reason one of the presets is packed
    * as tightly as it is.
    */
  it should "leave a standing wave behind at the density the preset picks" in {
    val settled = tick(TrackRoad.ring(Circumference, List(21, 13), limit, limit, orderly), 400)

    val (ended, _, _) = watch(TrackRoad.forceLaneChange(settled), 1800) // three minutes

    println(f"[standing wave] spread still ${speedSpread(ended)}%.2f m/s after three minutes")

    withClue("the wave damped out instead of standing: ") {
      speedSpread(ended) should be > 5.0
    }
    withClue("the ring gridlocked rather than oscillating: ") {
      ended.meanSpeed.toKilometersPerHour should be > 5.0
    }
  }

  /** Drivers who never change lanes on their own, so a wave is down to the change we forced. */
  private val orderly = MOBIL(threshold = MetersPerSecondSquared(1000))

  /** Run the road on, keeping the worst speed spread and the slowest anyone got. */
  private def watch(start: TrackRoad, ticks: Int): (TrackRoad, Double, Double) =
    (1 to ticks).foldLeft((start, 0.0, Double.MaxValue)) {
      case ((current, worst, slowest), _) =>
        val next = TrackRoad.update(current, dt)
        val min = next.vehicles.map(_.speed.toMetersPerSecond).min
        (next, math.max(worst, speedSpread(next)), math.min(slowest, min))
    }

  /**
    * Measured within a lane rather than across the road.
    *
    * Two lanes of different density settle at different speeds, and that difference is the
    * traffic behaving correctly - it would drown out the thing being looked for here.
    */
  private def speedSpread(road: TrackRoad): Double =
    road.lanes.map { lane =>
      val speeds = lane.vehicles.map(_.speed.toMetersPerSecond)
      if (speeds.isEmpty) 0.0 else speeds.max - speeds.min
    }.max

  "Crossing between lanes" should "keep a car where it was round the loop" in {
    val two = road(4, 4)
    val outer = two.lanes.head.path
    val inner = two.lanes.last.path

    val quarterWayRound = outer.totalLength / 4.0
    val landed = two.transpose(outer, inner, quarterWayRound)

    (landed / inner.totalLength) shouldBe 0.25 +- Tolerance
  }

  /**
    * The seam animation will be turned on at. Instant changes never offset a car from its
    * lane, so the drawing code can already be trusted with an offset that is always zero.
    */
  "An instant lane change" should "put the car exactly on its new lane" in {
    val moved = TrackRoad.forceLaneChange(tick(road(12, 6), 100))

    moved.vehicles.foreach(_.lateral shouldBe Meters(0))

    val inner = moved.lanes.last
    inner.vehicles.foreach { vehicle =>
      (vehicle.piloted.spatial.r - RingPath.ORIGIN).magnitude.toMeters shouldBe
      inner.path.asInstanceOf[RingPath].radius.toMeters +- Tolerance
    }
  }

  "An animated lane change" should "start the car beside its new lane and slide it over" in {
    val animated = tick(road(12, 6), 100).copy(laneChangeDuration = Some(Seconds(1)))
    val moved = TrackRoad.forceLaneChange(animated)

    val crossing: TrackVehicle =
      moved.vehicles.find(_.lateral.abs > Meters(0.01)).getOrElse(fail("nobody is mid-change"))

    // It starts a full lane away from where it is headed, on the side it came from.
    crossing.lateral.abs.toMeters shouldBe LaneWidth.toMeters +- Tolerance

    val arrived = tick(moved, 20) // Two seconds, against a one second crossing.
    arrived.vehicles.foreach(_.lateral.toMeters shouldBe 0.0 +- Tolerance)
  }
}
