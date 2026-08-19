package com.billding.traffic

import java.util.UUID

import com.billding.physics.{Path, Spatial}
import squants.motion.{Acceleration, MetersPerSecond, MetersPerSecondSquared}
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
  changeCooldown: Time = Seconds(0),
  /** What the car did about its speed on the last tick, kept so the canvas can colour it. */
  acceleration: Acceleration = MetersPerSecondSquared(0),
  /** A change this driver has decided on but not yet made, if the road works that way. */
  intent: Option[LaneChangeIntent] = None,
  /**
    * How fast this driver wants to go, as a multiple of the limit.
    *
    * Traffic where everybody wants exactly the same speed has no reason to overtake, and a
    * ring of it settles into a state where every car sees an identical road and keeps seeing
    * it forever - so whatever MOBIL answers on the first tick, it answers for the rest of the
    * run. This is the one thing that makes a driver's situation its own: somebody in front
    * who is slower than you is a reason to be somewhere else, and it is the reason.
    *
    * A multiple rather than a speed of its own, so the speed control still moves the whole
    * population together rather than being overridden car by car.
    */
  speedPreference: Double = 1.0
) {

  /** The along-the-road extent of the car, which is what a gap is measured between. */
  val length: Length = piloted.width

  def uuid: UUID = piloted.uuid

  /**
    * A car that has just changed lanes stays put for a while, rather than dithering, and one
    * that has already made up its mind is not still shopping for somewhere to go.
    */
  def mayChangeLane: Boolean = changeCooldown <= Seconds(0) && intent.isEmpty

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
      speedLimit * speedPreference,
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
  speedLimit: Velocity,
  /**
    * The disagreement the lane was populated with, kept so it can make more cars like the
    * ones it has. A lane that gains a car after the fact should gain one of its own drivers,
    * not the one identical driver that every later arrival would otherwise be.
    */
  speedSpread: Double = 0.0,
  /**
    * How many cars have driven past [[TrackLane.CountingLine]] since the lane was laid out.
    *
    * A closed road has no end to disappear off, so there is nothing to count by watching the
    * traffic thin out - a ring holds the same cars from the first tick to the last. What it
    * has instead is a place, and a car that keeps going comes back to it, which is what makes
    * a loop measurable at all: one point on the tarmac, and a count of who has been over it.
    *
    * Cars arriving and leaving by other means don't touch this. A lane change carries the
    * count nowhere - each lane counts its own line - and a car the density dial adds or takes
    * away has neither finished a lap nor undone one.
    */
  passes: Int = 0
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
    *
    * `speedSpread` is how much the drivers disagree about how fast they want to go, either
    * side of the limit: 0.15 gives a population running from 85% of it to 115%. It defaults
    * to none, because a lane of identical drivers is what the single-lane demonstrations are
    * tuned around - their waves come from a car being braked, not from the traffic's own
    * variety, and giving them a spread would be retuning a scene that already works.
    */
  def evenlySpaced(
    path: Path,
    count: Int,
    initialSpeed: Velocity,
    speedLimit: Velocity,
    startingAt: Length = Meters(0),
    speedSpread: Double = 0.0
  ): TrackLane = {
    require(count >= 0, "a lane cannot hold a negative number of cars")
    require(path.isClosed, "even spacing only makes sense on a closed path")

    val spacing = if (count == 0) Meters(0) else path.totalLength / count.toDouble
    val vehicles = (0 until count).toList.map { index =>
      val s = path.normalize(startingAt + spacing * -index.toDouble)
      TrackVehicle(
        commuterAt(path, s, initialSpeed),
        s,
        initialSpeed,
        speedPreference = preferenceAt(index, speedSpread)
      ).placedOn(path)
    }
    TrackLane(path, vehicles, speedLimit, speedSpread)
  }

  /** Room a car wants beyond its own length before it will be dropped into a gap. */
  private val ArrivalClearance: Length = Meters(2)

  /**
    * One more car, dropped into the roomiest gap the lane has.
    *
    * The roomiest rather than anywhere, because a car appearing is a disturbance whatever you
    * do with it, and the biggest gap is where it disturbs least - it is the one place on the
    * loop where the traffic was not already using all the room it had.
    *
    * A lane with nowhere to put one comes back unchanged, which is how a road gets to be full:
    * the dial goes on asking for more cars and the road goes on declining, rather than either
    * of them having to know what full means.
    */
  def withOneMore(lane: TrackLane): TrackLane =
    if (lane.vehicles.isEmpty)
      lane.copy(vehicles = List(arrival(lane, Meters(0), lane.speedLimit)))
    else {
      val roomiest = lane.vehicles.indices.maxBy(index => gapAhead(lane, index).toMeters)
      val gap = gapAhead(lane, roomiest)
      val behind = lane.vehicles(roomiest)

      if (gap <= behind.length + ArrivalClearance * 2.0) lane
      else
        lane.withVehicleAdded(
          // Centred in the gap, and travelling with the car it is arriving behind.
          arrival(lane, lane.path.normalize(behind.s + (gap + behind.length) / 2.0), behind.speed)
        )
    }

  /**
    * One car fewer, taken from the tightest spot on the loop.
    *
    * Every removal leaves a hole the traffic has to absorb, and the hole is the car's length
    * plus whatever room it had. Taking the car that had least leaves the smallest one.
    */
  def withOneFewer(lane: TrackLane): TrackLane =
    if (lane.vehicles.isEmpty) lane
    else {
      val tightest = lane.vehicles.indices.minBy(index => gapAhead(lane, index).toMeters)
      lane.copy(vehicles = lane.vehicles.patch(tightest, Nil, 1))
    }

  /**
    * A car joining traffic that is already running.
    *
    * Its speed preference carries on the sequence the lane was laid out with, so an arrival is
    * a driver the lane did not have rather than a repeat of one it did.
    */
  private def arrival(lane: TrackLane, s: Length, speed: Velocity): TrackVehicle =
    TrackVehicle(
      commuterAt(lane.path, s, speed),
      s,
      speed,
      speedPreference = preferenceAt(lane.vehicles.size, lane.speedSpread)
    ).placedOn(lane.path)

  /**
    * The golden ratio's fractional multiples, which is the cheapest way to hand out a spread
    * that is both even and deterministic.
    *
    * Dealing them out in order - slowest car, next slowest, and so on round the loop - would
    * put every slow driver in one convoy and every fast one in another, which is a lane with
    * two speeds in it rather than a lane of drivers who disagree. Stepping by an irrational
    * fraction of the range instead means consecutive cars land far apart in it while the
    * population as a whole still comes out evenly covered, and it does so without a seed to
    * thread through or a random number generator to make a test's result depend on.
    */
  private[traffic] def preferenceAt(index: Int, spread: Double): Double = {
    val golden = 0.6180339887498949
    val place = (index * golden) % 1.0
    1.0 - spread + 2 * spread * place
  }

  /**
    * Where on a closed lane the cars are counted.
    *
    * Arc length nought, which on a ring is the 3 o'clock position and is the same angle
    * whatever a lane's radius is - so on concentric lanes the lines are one line across the
    * road rather than several scattered round it, and the canvas can draw it as such.
    */
  val CountingLine: Length = Meters(0)

  def update(lane: TrackLane, dt: Time): TrackLane = {
    val moved = lane.vehicles.zip(accelerations(lane)).map {
      case (vehicle, acceleration) => advance(vehicle, acceleration, dt, lane.path)
    }
    lane.copy(vehicles = moved, passes = lane.passes + crossings(lane, moved))
  }

  /**
    * How many of this step's movements took a car over the counting line.
    *
    * Read off where each car was and where it ended up rather than out of [[advance]],
    * because arc length is the only thing that knows a lap happened: a car's position wraps
    * from just short of the loop's length back to nearly nothing, and the distance it covered
    * getting there is exactly the forward gap between the two.
    *
    * The stretch counted is open at the start and closed at the end, so a car sitting on the
    * line is not over it until it moves - otherwise every lane would count its own starting
    * grid on the first tick, and a car stopped on the line would count once per frame forever.
    */
  private def crossings(lane: TrackLane, moved: List[TrackVehicle]): Int =
    lane.vehicles.zip(moved).count {
      case (before, after) =>
        val travelled = lane.path.forwardGap(before.s, after.s)
        val toTheLine = lane.path.forwardGap(before.s, CountingLine)
        toTheLine > Meters(0) && toTheLine <= travelled
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
      changeCooldown = if (vehicle.changeCooldown > dt) vehicle.changeCooldown - dt else Seconds(0),
      acceleration = acceleration,
      intent = vehicle.intent.map(_.advancedBy(dt))
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
