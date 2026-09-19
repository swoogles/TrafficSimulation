package com.billding.network

import java.util.UUID

import scala.annotation.tailrec

import squants.motion.{Acceleration, MetersPerSecond}
import squants.space.{Length, Meters}
import squants.{Time, Velocity}

/**
  * The outcome of one [[NetworkTick.advance]]: the network's new state, and
  * whoever left it.
  *
  * `advance`'s signature in the card is a plain `NetworkTraffic`, but a
  * vehicle that overshoots a section with no outgoing movement has nowhere
  * left to be placed - it has driven off the dangling end that W5 treats as
  * an implicit sink. Silently dropping it would make its trip vanish rather
  * than complete, so it comes back here instead, letting a caller count
  * departures (per source/sink, eventually) without `NetworkTraffic` itself
  * needing to know about sinks at all.
  */
final case class NetworkTickResult(traffic: NetworkTraffic, departures: List[NetworkVehicle])

/**
  * Advances every vehicle on a [[RoadNetwork]] by one fixed timestep.
  *
  * Four phases, run in this order:
  *
  *   1. '''Lane change''' (card F2) - [[NetworkLaneChange.advance]] decides
  *      and commits every discretionary lane change for the tick, reading
  *      only the snapshot `advance` was given. A change decided against a
  *      half-updated world is not the change the driver would have made,
  *      which is exactly why this runs before anything below touches
  *      positions or speeds - the same reason phases 2-4 themselves are kept
  *      to one snapshot apiece rather than mutating as they go.
  *   1. '''Read''' - every vehicle's acceleration is computed from
  *      [[Lookahead.leaderOf]] and the existing IDM call, reading the state
  *      lane changes just committed (so a car that just moved reacts to its
  *      new leader, not its old one) but nothing this phase itself touches.
  *      Nothing moves yet.
  *   1. '''Integrate''' - a new speed and a new `s` are computed for every
  *      vehicle, still only from that same snapshot.
  *   1. '''Commit''' - any vehicle whose new `s` now exceeds its section's
  *      length is walked forward (subtract the length, follow `next`, repeat)
  *      until it lands inside a section or falls off a dangling end. Only
  *      then is `bySection` rebuilt, leader-first.
  *
  * Doing phases 2-4 in one pass instead - mutating sections as you iterate
  * them - would advance a vehicle twice the moment it crosses into a section
  * that iteration has not reached yet: once when it was read out of its old
  * section, and again if the pass later reaches the section it just landed
  * in. Keeping Read and Integrate working from one immutable snapshot, and
  * folding every vehicle through Commit exactly once
  * (`vehicles.map(...).map(...).map(...)`, never a second pass over the
  * result), is what rules that out - see `NetworkTickSpec` for a test built
  * to catch it.
  */
object NetworkTick {

  private val Stopped: Velocity = MetersPerSecond(0)

  /** Keeps the IDM's 1/gap^2 term finite when cars are (nearly) touching. */
  private val MinimumGap: Length = Meters(0.1)

  /**
    * What a vehicle with nobody ahead reacts to - matching the free-road gap
    * `TrackLane` feeds the IDM when [[TrackLane.leaderOf]] finds nothing.
    */
  val FreeRoadGap: Length = Meters(10000)

  /** A floor under [[lookaheadDistance]], so a stopped or crawling car still looks past a seam. */
  private val MinimumLookahead: Length = Meters(50)

  /**
    * Guard against a cycle of short (or zero-length) sections making
    * [[settle]] walk forever within a single tick - the plan calls for
    * rejecting exactly this, the same way [[Lookahead]] caps its own walk by
    * hop count as well as by distance.
    */
  private val MaxSettleHops: Int = 20

  def advance(traffic: NetworkTraffic, index: NetworkIndex, dt: Time): NetworkTickResult = {
    // 1. Lane change - committed from `traffic` exactly as given, before anything below reads it.
    val afterLaneChanges = NetworkLaneChange.advance(traffic, index, dt)
    val vehicles = afterLaneChanges.all

    // 2. Read - every acceleration comes from `afterLaneChanges`, untouched throughout the rest of this method.
    val accelerationByUuid: Map[UUID, Acceleration] =
      vehicles.map(vehicle => vehicle.piloted.uuid -> accelerationOf(afterLaneChanges, index, vehicle)).toMap

    // 3. Integrate - new speed and s, still only a function of the snapshot above.
    val integrated: List[NetworkVehicle] =
      vehicles.map(vehicle => integrate(vehicle, accelerationByUuid(vehicle.piloted.uuid), dt))

    // 4. Commit - resolve overshoot section-by-section, then rebuild leader-first.
    val settled: List[Either[NetworkVehicle, NetworkVehicle]] = integrated.map(settle(_, index))
    val departures: List[NetworkVehicle] = settled.collect { case Left(departed) => departed }
    val remaining: List[NetworkVehicle] = settled.collect { case Right(stayed) => stayed }

    NetworkTickResult(NetworkTraffic.of(remaining), departures)
  }

  /**
    * What `vehicle` is doing about whoever (if anyone) is ahead of it,
    * reusing [[com.billding.traffic.TrackLane]]'s pattern exactly: a leader
    * beyond [[lookaheadDistance]] or off the end of the network is treated as
    * no leader at all, and a vehicle with no leader reacts as if chasing
    * itself - zero closing speed, against the free-road gap - the same
    * substitution `TrackLane.accelerationAt` makes.
    */
  private[network] def accelerationOf(traffic: NetworkTraffic, index: NetworkIndex, vehicle: NetworkVehicle): Acceleration = {
    val speedLimit = index.network
      .section(vehicle.section)
      .map(_.speedLimit)
      .getOrElse(vehicle.piloted.driver.desiredSpeed)

    Lookahead.leaderOf(traffic, index, vehicle, lookaheadDistance(vehicle)) match {
      case Some((leader, gap)) => reactTo(vehicle, leader.speed, atLeastMinimum(gap), speedLimit)
      case None                => reactTo(vehicle, vehicle.speed, FreeRoadGap, speedLimit)
    }
  }

  /** [[com.billding.traffic.TrackVehicle.reactTo]], rebuilt against a [[NetworkVehicle]]'s fields. */
  private def reactTo(vehicle: NetworkVehicle, leaderSpeed: Velocity, gap: Length, speedLimit: Velocity): Acceleration =
    vehicle.piloted.driver.idm.deltaVDimensionallySafe(
      vehicle.speed,
      speedLimit,
      vehicle.speed - leaderSpeed,
      vehicle.piloted.driver.preferredDynamicSpacing,
      vehicle.piloted.vehicle.accelerationAbility,
      vehicle.piloted.vehicle.brakingAbility,
      gap,
      vehicle.piloted.driver.minimumDistance
    )

  private def atLeastMinimum(gap: Length): Length = if (gap < MinimumGap) MinimumGap else gap

  /**
    * How far ahead `vehicle` searches for a leader before the road counts as
    * clear - the plan's "far enough to brake safely."
    *
    * Twice the distance this vehicle would need to stop from its current
    * speed at its own braking rate, so a driver sees a stopped queue in time
    * to react rather than only just in time to lock the brakes, floored so a
    * stationary or crawling car still looks far enough past its own seam to
    * see a queue starting to form there.
    */
  private def lookaheadDistance(vehicle: NetworkVehicle): Length = {
    val v = vehicle.speed.toMetersPerSecond
    val b = math.max(vehicle.piloted.vehicle.brakingAbility.toMetersPerSecondSquared, 0.1)
    val padded = Meters((v * v) / (2 * b)) * 2.0
    if (padded > MinimumLookahead) padded else MinimumLookahead
  }

  /**
    * The ballistic step [[com.billding.traffic.TrackLane.update]] uses:
    * update the speed first, then move by the average of the old and new
    * speeds, stopping short rather than reversing if the step would cross
    * zero. Reimplemented here rather than called, since `TrackLane`'s
    * version is private to it - the equation is the same one, not a new one.
    */
  private def integrate(vehicle: NetworkVehicle, acceleration: Acceleration, dt: Time): NetworkVehicle = {
    val projectedSpeed = vehicle.speed + acceleration * dt
    val (newSpeed, travelled) =
      if (projectedSpeed > Stopped) (projectedSpeed, (vehicle.speed + projectedSpeed) / 2.0 * dt)
      else (Stopped, distanceToStop(vehicle.speed, acceleration))
    vehicle.copy(s = vehicle.s + travelled, speed = newSpeed, acceleration = acceleration)
  }

  private def distanceToStop(speed: Velocity, acceleration: Acceleration): Length = {
    val rate = acceleration.toMetersPerSecondSquared
    if (rate < 0) {
      val current = speed.toMetersPerSecond
      Meters(-(current * current) / (2 * rate))
    } else Meters(0)
  }

  /**
    * Resolve one already-integrated vehicle against the section graph.
    *
    * While its `s` still exceeds its current section's length, subtract that
    * length and follow `next` into the successor, landing at the leftover
    * distance - the carry the plan asks for, extended across as many short
    * sections as one step covers. `next` (and `route`/`routeFailed`) are
    * then refreshed by [[NetworkVehicle.chooseNext]], the same policy
    * [[NetworkVehicle.enteringAt]] uses for a car's first section: the head
    * of whatever route remains, replanned from the landed section if that
    * route ran out before reaching the destination, or the landed section's
    * own first declared outgoing movement when there is no destination to
    * route toward (or a replan failed) - E3's routing in place of phase C's
    * placeholder.
    *
    * `Left` marks a vehicle that ran out of road - its `next` was `None` (a
    * dangling end, the implicit sink) - rather than landing inside a
    * section; `Right` is everyone still on the network. A vehicle within its
    * section's length on the first check never enters the loop at all, so it
    * is touched by this method exactly once, matching every other vehicle.
    */
  private def settle(vehicle: NetworkVehicle, index: NetworkIndex): Either[NetworkVehicle, NetworkVehicle] = {
    @tailrec
    def go(current: NetworkVehicle, hop: Int): Either[NetworkVehicle, NetworkVehicle] =
      index.network.section(current.section) match {
        case None => Right(current) // A section the index doesn't know about: nothing more to resolve.
        case Some(section) =>
          if (current.s <= section.length) Right(current)
          else if (hop >= MaxSettleHops) Right(current.copy(s = section.length)) // Guard: a short-section cycle.
          else {
            val overflow = current.s - section.length
            current.next.flatMap(movement(index, _)) match {
              case Some(toNext) =>
                val (next, remainingRoute, routeFailed) =
                  NetworkVehicle.chooseNext(index, toNext.to, current.route, current.destination, current.routeFailed)
                val landed = current.copy(
                  section = toNext.to,
                  s = overflow,
                  next = next,
                  route = remainingRoute,
                  routeFailed = routeFailed
                )
                go(landed, hop + 1)
              case None => Left(current) // No outgoing movement: this vehicle has left the network.
            }
          }
      }

    go(vehicle, hop = 0)
  }

  private def movement(index: NetworkIndex, id: MovementId): Option[Movement] =
    index.network.movements.find(_.id == id)
}
