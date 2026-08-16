package com.billding.traffic

import java.util.UUID

import com.billding.physics.{Path, Spatial}
import squants.motion.{Acceleration, MetersPerSecond}
import squants.space.{Length, Meters}
import squants.time.Seconds
import squants.{DoubleVector, Time, Velocity}

/**
  * A vehicle located by how far it has driven along a Path, rather than by a point in space.
  *
  * The wrapped PilotedVehicle still owns everything that isn't longitudinal - the driver's
  * parameters, the car's abilities, its dimensions and uuid - and its Spatial is kept in step
  * with s so that rendering and serialization keep reading the field they already read.
  */
case class TrackVehicle(
  piloted: PilotedVehicle,
  s: Length,
  speed: Velocity,
  lateral: Length = Meters(0),
  changeCooldown: Time = Seconds(0)
) {

  /** The along-the-road extent of the car, which is what a gap is measured between. */
  val length: Length = piloted.width

  def uuid: UUID = piloted.uuid

  /** A car that has just changed lanes stays put for a while, rather than dithering. */
  def mayChangeLane: Boolean = changeCooldown <= Seconds(0)

  /** Rewrite the derived Spatial from this position on the given path. */
  def placedOn(path: Path): TrackVehicle = {
    val position = path.pointAt(s) + path.normalAt(s).map { component: Double =>
        lateral * component
      }
    val velocity = path.headingAt(s).map { component: Double =>
      speed * component
    }
    copy(
      piloted =
        piloted.updateSpatial(Spatial.withVecs(position, velocity, piloted.spatial.dimensions))
    )
  }

  def reactTo(leader: TrackVehicle, gap: Length, speedLimit: Velocity): Acceleration =
    piloted.driver.idm.deltaVDimensionallySafe(
      speed,
      speedLimit,
      speed - leader.speed, // Signed, unlike the vector version: positive means we are closing.
      piloted.driver.preferredDynamicSpacing,
      piloted.vehicle.accelerationAbility,
      piloted.vehicle.brakingAbility,
      gap,
      piloted.driver.minimumDistance
    )

  def at(newS: Length, newSpeed: Velocity): TrackVehicle =
    copy(s = newS, speed = newSpeed)
}

/**
  * A single lane of traffic running along a Path.
  *
  * Vehicles are ordered leader-first, the same convention Lane uses: the car ahead of
  * vehicles(i) is vehicles(i - 1). On a closed path that wraps, so the car ahead of the
  * head of the list is the car at the end of it.
  */
case class TrackLane(
  path: Path,
  vehicles: List[TrackVehicle],
  speedLimit: Velocity
) {

  def gapAhead(index: Int): Length = TrackLane.gapAhead(this, index)

  /** Which way a car is pointing: the tangent of the road under it. */
  def headingOf(vehicle: TrackVehicle): DoubleVector = path.headingAt(vehicle.s)

  def gaps: List[Length] = vehicles.indices.toList.map(gapAhead)

  def meanSpeed: Velocity =
    if (vehicles.isEmpty) MetersPerSecond(0)
    else vehicles.map(_.speed).reduce(_ + _) / vehicles.size.toDouble

  /** Slow one car down without moving it - the perturbation the whole ring exists to show. */
  def withVehicleSlowedTo(index: Int, newSpeed: Velocity): TrackLane =
    copy(vehicles = vehicles.updated(index, vehicles(index).copy(speed = newSpeed)))

  def without(vehicle: TrackVehicle): TrackLane =
    copy(vehicles = vehicles.filterNot(_.uuid == vehicle.uuid))

  /**
    * Slot an arriving car into the running order.
    *
    * Sorting by descending s is a canonical form of the leader-first-with-wrap order the
    * lane already keeps: the car ahead is still the one at index - 1, and the head of the
    * list still wraps round to the tail. Cars that stay put can't overtake, so a lane only
    * ever needs re-ordering when one arrives from somewhere else.
    */
  def withVehicleAdded(vehicle: TrackVehicle): TrackLane =
    copy(vehicles = (vehicle :: vehicles).sortBy(-_.s.toMeters))
}

object TrackLane {

  private val STOPPED: Velocity = MetersPerSecond(0)

  /** Keeps the IDM's 1/gap^2 term finite when cars are touching. */
  private val MINIMUM_GAP: Length = Meters(0.1)

  /** What the leading car on an open road reacts to, matching Lane's vehicle at infinity. */
  private val FREE_ROAD: Length = Meters(10000)

  /**
    * Fill a closed path with evenly spaced cars, all at the same speed.
    *
    * Index 0 sits at arc length 0 and each subsequent car sits one spacing behind it, which
    * puts the list in leader-first order the same way the straight lane's list is.
    */
  def evenlySpaced(
    path: Path,
    count: Int,
    initialSpeed: Velocity,
    speedLimit: Velocity,
    startingAt: Length = Meters(0)
  ): TrackLane = {
    require(count >= 0, "a lane cannot hold a negative number of cars")
    require(path.isClosed, "even spacing only makes sense on a closed path")

    val spacing = if (count == 0) Meters(0) else path.totalLength / count.toDouble
    val vehicles = (0 until count).toList.map { index =>
      val s = path.normalize(startingAt + spacing * -index.toDouble)
      TrackVehicle(commuterAt(path, s, initialSpeed), s, initialSpeed).placedOn(path)
    }
    TrackLane(path, vehicles, speedLimit)
  }

  def update(lane: TrackLane, dt: Time): TrackLane = {
    val moved = lane.vehicles.zip(accelerations(lane)).map {
      case (vehicle, acceleration) => advance(vehicle, acceleration, dt, lane.path)
    }
    lane.copy(vehicles = moved)
  }

  def accelerations(lane: TrackLane): List[Acceleration] =
    lane.vehicles.indices.toList.map(accelerationAt(lane, _))

  /**
    * What the car at `index` is doing about whoever is in front of it.
    *
    * MOBIL asks this of hypothetical lanes as well as real ones - "what would B' be doing if
    * I were in front of it?" is the same question, put to a lane with the asker spliced in.
    */
  def accelerationAt(lane: TrackLane, index: Int): Acceleration = {
    val follower = lane.vehicles(index)
    // With nobody ahead there is nothing to close on, so the car reacts to its own speed.
    val leader = leaderOf(lane, index).getOrElse(follower)
    follower.reactTo(leader, atLeastMinimum(gapAhead(lane, index)), lane.speedLimit)
  }

  /**
    * Bumper-to-bumper room in front of the car at `index`.
    *
    * Reported as measured, so it can go negative if cars have overlapped - callers that feed
    * it to the IDM go through [[atLeastMinimum]], and tests get to see the truth.
    */
  def gapAhead(lane: TrackLane, index: Int): Length = {
    val follower = lane.vehicles(index)
    gapFrom(lane, follower.s, follower.length, leaderOf(lane, index))
  }

  def leaderOf(lane: TrackLane, index: Int): Option[TrackVehicle] = {
    val count = lane.vehicles.size
    if (!lane.path.isClosed) lane.vehicles.lift(index - 1)
    else if (count <= 1) None // Chasing yourself around a loop is not following.
    else Some(lane.vehicles((index - 1 + count) % count))
  }

  def followerOf(lane: TrackLane, index: Int): Option[TrackVehicle] = {
    val count = lane.vehicles.size
    if (!lane.path.isClosed) lane.vehicles.lift(index + 1)
    else if (count <= 1) None
    else Some(lane.vehicles((index + 1) % count))
  }

  /**
    * The car a newcomer arriving at arc length s would find itself behind.
    *
    * Unlike [[leaderOf]] this searches by position rather than by running order, because the
    * car doing the asking isn't in this lane yet - it is weighing up whether to be.
    */
  def leaderAt(lane: TrackLane, s: Length): Option[TrackVehicle] =
    closest(lane, lane.vehicles.map(vehicle => (vehicle, lane.path.forwardGap(s, vehicle.s))))

  /** The car that would find itself behind a newcomer arriving at arc length s. */
  def followerAt(lane: TrackLane, s: Length): Option[TrackVehicle] =
    closest(lane, lane.vehicles.map(vehicle => (vehicle, lane.path.forwardGap(vehicle.s, s))))

  /**
    * The nearest of a set of candidates in the direction that was measured.
    *
    * An open path reports negative distances for whatever lies the other way, and those are
    * not candidates at all - on a closed path every distance is already a way round.
    */
  private def closest(
    lane: TrackLane,
    candidates: List[(TrackVehicle, Length)]
  ): Option[TrackVehicle] = {
    val ahead = if (lane.path.isClosed) candidates else candidates.filter(_._2 >= Meters(0))
    if (ahead.isEmpty) None else Some(ahead.minBy(_._2.toMeters)._1)
  }

  /**
    * The room a car at arc length s would have in front of it, given whoever is ahead.
    *
    * Kept separate from [[gapAhead]] so a hypothetical position - the one a driver is weighing
    * up in the next lane - is measured exactly the way a real one is.
    */
  def gapFrom(lane: TrackLane, s: Length, ownLength: Length, leader: Option[TrackVehicle]): Length =
    leader match {
      case Some(ahead)                => lane.path.forwardGap(s, ahead.s) - ahead.length
      case None if lane.path.isClosed => lane.path.totalLength - ownLength
      case None                       => FREE_ROAD
    }

  private[traffic] def atLeastMinimum(gap: Length): Length =
    if (gap < MINIMUM_GAP) MINIMUM_GAP else gap

  /**
    * Ballistic step, as Treiber recommends for the IDM: update the speed first, then move by
    * the average of the old and new speeds. A car that would reverse within the step instead
    * travels exactly as far as it takes to stop.
    */
  private def advance(
    vehicle: TrackVehicle,
    acceleration: Acceleration,
    dt: Time,
    path: Path
  ): TrackVehicle = {
    val projectedSpeed = vehicle.speed + acceleration * dt
    val (newSpeed, travelled) =
      if (projectedSpeed > STOPPED)
        (projectedSpeed, (vehicle.speed + projectedSpeed) / 2.0 * dt)
      else
        (STOPPED, distanceToStop(vehicle.speed, acceleration))

    val settled = vehicle.copy(
      changeCooldown = if (vehicle.changeCooldown > dt) vehicle.changeCooldown - dt else Seconds(0)
    )
    settled.at(path.normalize(settled.s + travelled), newSpeed).placedOn(path)
  }

  private def distanceToStop(speed: Velocity, acceleration: Acceleration): Length = {
    val rate = acceleration.toMetersPerSecondSquared
    if (rate < 0) {
      val current = speed.toMetersPerSecond
      Meters(-(current * current) / (2 * rate))
    } else Meters(0)
  }

  private def commuterAt(path: Path, s: Length, speed: Velocity): PilotedVehicle = {
    val spatial = Spatial.withVecs(path.pointAt(s), path.headingAt(s).map { component: Double =>
      speed * component
    })
    PilotedVehicle.commuter2(spatial, new IntelligentDriverModelImpl, spatial)
  }
}
