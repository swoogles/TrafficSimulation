package com.billding.traffic

import com.billding.physics.{Path, Spatial}
import squants.motion.{Acceleration, MetersPerSecond}
import squants.space.{Length, Meters}
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
  speed: Velocity
) {

  /** The along-the-road extent of the car, which is what a gap is measured between. */
  val length: Length = piloted.width

  /** Rewrite the derived Spatial from this position on the given path. */
  def placedOn(path: Path): TrackVehicle = {
    val position = path.pointAt(s)
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
    speedLimit: Velocity
  ): TrackLane = {
    require(count >= 0, "a lane cannot hold a negative number of cars")
    require(path.isClosed, "even spacing only makes sense on a closed path")

    val spacing = if (count == 0) Meters(0) else path.totalLength / count.toDouble
    val vehicles = (0 until count).toList.map { index =>
      val s = path.normalize(spacing * -index.toDouble)
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
    lane.vehicles.indices.toList.map { index =>
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
    leaderOf(lane, index) match {
      case Some(leader) => lane.path.forwardGap(follower.s, leader.s) - leader.length
      // Alone on a loop: the road ahead is the whole thing, less the car's own footprint.
      case None if lane.path.isClosed => lane.path.totalLength - follower.length
      case None                       => FREE_ROAD
    }
  }

  private def leaderOf(lane: TrackLane, index: Int): Option[TrackVehicle] = {
    val count = lane.vehicles.size
    if (!lane.path.isClosed) lane.vehicles.lift(index - 1)
    else if (count <= 1) None // Chasing yourself around a loop is not following.
    else Some(lane.vehicles((index - 1 + count) % count))
  }

  private def atLeastMinimum(gap: Length): Length =
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

    vehicle.at(path.normalize(vehicle.s + travelled), newSpeed).placedOn(path)
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
