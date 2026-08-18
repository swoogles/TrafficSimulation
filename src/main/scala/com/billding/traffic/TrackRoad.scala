package com.billding.traffic

import java.util.UUID

import com.billding.physics.{Path, PathExtent, RingPath}
import com.billding.svgRendering.RoadShape
import squants.motion.MetersPerSecond
import squants.space.{Length, Meters}
import squants.time.Seconds
import squants.{Acceleration, Time, Velocity}

/**
  * Several lanes running alongside each other, and the traffic's freedom to move between them.
  *
  * A single [[TrackLane]] can only ever show you what a queue does to itself. Give the cars
  * somewhere else to go and you get the thing worth watching: a driver takes a gap, the car
  * it cut in front of lifts off, and the lift-off travels backwards through traffic that
  * never saw what caused it.
  */
case class TrackRoad(
  lanes: List[TrackLane],
  mobil: MOBIL = MOBIL(),
  laneWidth: Length = RoadShape.LaneWidth,
  /**
    * How long a car spends visibly crossing the line, or None to have it arrive at once.
    *
    * Instant changes are what MOBIL describes and are the honest default; the offset that
    * an animated one needs is carried by [[TrackVehicle.lateral]] either way, so turning
    * this on is the only change animation asks for.
    */
  laneChangeDuration: Option[Time] = None,
  /**
    * How long a driver indicates before it crosses, or None to have it decide and go at once.
    *
    * Deciding and moving in the same instant is what MOBIL describes, and it means the first
    * thing you can see of a lane change is the car already making it. A warning gives the
    * canvas somewhere to point before anything moves, and gives the driver a moment to think
    * better of it - the decision is re-examined against the road when the warning is up.
    */
  laneChangeWarning: Option[Time] = None,
  /**
    * How often a driver changes lane for no reason at all, in changes per driver per minute.
    *
    * MOBIL only ever produces the changes that pay, and traffic made purely of those is
    * traffic that behaves better than any traffic does. Real roads are full of moves nobody
    * can account for - a driver who wanted the other lane, and took it - and those are the
    * ones that start the waves. This is the dial for them, and it is deliberately not part of
    * [[MOBIL]]: that is the model of a driver weighing something up, and the whole point of
    * these is that nothing was weighed.
    */
  whimsy: Double = 0.0,
  /**
    * Where the arbitrary changes are drawn from.
    *
    * Carried in the road rather than reached for globally so a run stays reproducible: the
    * same road ticked the same number of times gives the same traffic, which is what lets a
    * test say anything at all about drivers who are supposed to be unpredictable.
    */
  entropy: Long = TrackRoad.FirstSeed
) {

  require(lanes.nonEmpty, "a road needs at least one lane")
  require(whimsy >= 0.0, "a driver cannot change lanes arbitrarily less often than never")

  def speedLimit: Velocity = lanes.head.speedLimit

  def vehicles: List[TrackVehicle] = lanes.flatMap(_.vehicles)

  def vehicleCount: Int = lanes.map(_.vehicles.size).sum

  def meanSpeed: Velocity = {
    val all = vehicles
    if (all.isEmpty) MetersPerSecond(0)
    else all.map(_.speed).reduce(_ + _) / all.size.toDouble
  }

  /** The lanes a driver in `index` could move into. */
  def neighboursOf(index: Int): List[Int] =
    List(index - 1, index + 1).filter(lanes.indices.contains)

  /** The outermost lane is the widest, so it is the one the canvas has to fit. */
  def extent: PathExtent = lanes.head.path.extent

  /** Every lane's length added up, which is the road there is to put cars on. */
  def laneKilometres: Double = lanes.map(_.path.totalLength.toKilometers).sum

  /**
    * Cars per kilometre of lane.
    *
    * The one number that decides what this traffic is allowed to do. Eight-metre cars wanting
    * six metres at a standstill and a second of headway take about twenty-six metres each at
    * speed, so somewhere around forty per kilometre the road stops having room for a lane
    * change and no dial anywhere else on the page can put it back.
    *
    * Per lane rather than per road, so the reading means the same thing on a one-lane ring as
    * on a two-lane one, and the same thing on lanes of different lengths.
    */
  def density: Double = if (laneKilometres <= 0) 0.0 else vehicleCount / laneKilometres

  /** How many cars that density comes to on this particular road. */
  def carsFor(perKm: Double): Int = math.max(0, math.round(perKm * laneKilometres).toInt)

  def withSpeedLimit(speedLimit: Velocity): TrackRoad =
    copy(lanes = lanes.map(_.copy(speedLimit = speedLimit)))

  /**
    * Where a car sits after crossing the line.
    *
    * Concentric lanes are not the same length, so carrying arc length across unchanged would
    * slide a car forwards or backwards along the road. What is actually preserved is where
    * it is round the loop, which is the same fraction of a shorter lane.
    */
  def transpose(from: Path, to: Path, s: Length): Length =
    to.normalize(to.totalLength * (s / from.totalLength))

  /**
    * How far from its new lane's centre a car starts out, so it can be drawn sliding across.
    *
    * Lane index counts inwards and the paths' normals point the same way, so a car arriving
    * from further out starts at a negative offset and settles up to zero.
    */
  def arrivalOffset(from: Int, to: Int): Length =
    if (laneChangeDuration.isEmpty) Meters(0)
    else laneWidth * (from - to).toDouble
}

object TrackRoad {

  /** Room a forced change still insists on, so the car lands on tarmac rather than on a bumper. */
  private val ForcedClearance: Length = Meters(1)

  private val NoAcceleration: Acceleration = squants.motion.MetersPerSecondSquared(0)

  /** Below this a lateral offset has arrived, and is snapped flat rather than left drifting. */
  private val Settled: Length = Meters(0.01)

  /** How many warnings' worth of waiting a forced change will do before it gives up. */
  private val Patience: Double = 2.0

  /** A car's move across the line, and how much it wanted it. */
  private case class Proposal(
    uuid: UUID,
    from: Int,
    to: Int,
    motivation: Acceleration,
    insistent: Boolean = false
  )

  /**
    * Concentric lanes with a fixed population on each, outermost lane first.
    *
    * Lanes are staggered by half a car spacing rather than started in lockstep, because cars
    * sitting exactly abreast is both an unlikely way for traffic to be and a state with no
    * gap worth moving into.
    */
  def ring(
    circumference: Length,
    carsPerLane: List[Int],
    initialSpeed: Velocity,
    speedLimit: Velocity,
    mobil: MOBIL = MOBIL(),
    laneWidth: Length = RoadShape.LaneWidth,
    speedSpread: Double = DefaultSpeedSpread
  ): TrackRoad = {
    val paths: List[RingPath] =
      RingPath.concentric(circumference, carsPerLane.size, laneWidth)

    val lanes = paths.zip(carsPerLane).zipWithIndex.map {
      case ((path, count), index) =>
        val stagger =
          if (count == 0) Meters(0)
          else path.totalLength / count.toDouble / 2.0 * (index % 2).toDouble
        TrackLane.evenlySpaced(path, count, initialSpeed, speedLimit, stagger, speedSpread)
    }
    TrackRoad(lanes, mobil, laneWidth)
  }

  /**
    * How much the drivers on a multi-lane ring disagree about how fast they want to go.
    *
    * Without this a road of identical drivers has nothing to overtake for, and the lane
    * changing that MOBIL is here to show simply never happens: balanced lanes produced no
    * change at all at any density or any eagerness, because a ring of identical cars starts
    * at a fixed point and stays there, so MOBIL's answer on the first tick is its answer for
    * the whole run. Fifteen percent is picked to be the smallest spread that reliably gets
    * traffic moving between lanes - at twenty-five the slowest drivers start capping everyone
    * behind them hard enough to change what the traffic is, rather than just why it moves.
    */
  val DefaultSpeedSpread: Double = 0.15

  def update(road: TrackRoad, dt: Time): TrackRoad = {
    val afterChanges = withLaneChanges(road, dt)
    afterChanges.copy(
      lanes = afterChanges.lanes.map(lane => TrackLane.update(settle(afterChanges, lane, dt), dt))
    )
  }

  /**
    * The traffic's lane changing for one tick: promises kept first, then fresh decisions.
    *
    * On a road with no warning period the two halves collapse into the one they always were,
    * since a driver that never announces anything has no promise outstanding to keep.
    */
  private def withLaneChanges(road: TrackRoad, dt: Time): TrackRoad = {
    val afterPromises = ranked(ripeProposals(road)).foldLeft(road)(reconsider)
    val fresh = ranked(freshProposals(afterPromises))

    val afterReasons = road.laneChangeWarning match {
      case None           => fresh.foldLeft(afterPromises)(reconsider)
      case Some(duration) => fresh.foldLeft(afterPromises)(announce(duration))
    }
    withWhimsy(afterReasons, dt)
  }

  /**
    * Every change the drivers currently fancy, weighed against the same frozen road.
    *
    * Judging them all against one state means nobody's decision depends on the order they
    * happen to be considered in; the second look each one gets in [[reconsider]] is where
    * the road as it actually stands has its say.
    */
  private def freshProposals(road: TrackRoad): List[Proposal] =
    for {
      (lane, from)     <- road.lanes.zipWithIndex
      (vehicle, index) <- lane.vehicles.zipWithIndex
      if vehicle.mayChangeLane
      to <- road.neighboursOf(from)
      situation = situationFor(road, from, index, to)
      if road.mobil.wants(situation, from, to)
    } yield Proposal(vehicle.uuid, from, to, road.mobil.motivation(situation, from, to))

  /** The drivers who have finished indicating, and are now actually going. */
  private def ripeProposals(road: TrackRoad): List[Proposal] =
    for {
      (lane, from)     <- road.lanes.zipWithIndex
      (vehicle, index) <- lane.vehicles.zipWithIndex
      intent           <- vehicle.intent.toList
      if intent.isRipe && road.lanes.indices.contains(intent.to)
    } yield Proposal(
      vehicle.uuid,
      from,
      intent.to,
      road.mobil.motivation(situationFor(road, from, index, intent.to), from, intent.to),
      intent.insistent
    )

  /** One move per car per tick: a driver picks its best lane, not every lane that tempts it. */
  private def ranked(proposals: List[Proposal]): List[Proposal] =
    proposals
      .groupBy(_.uuid)
      .values
      .toList
      .map(_.maxBy(_.motivation.toMetersPerSecondSquared))
      .sortBy(proposal => (-proposal.motivation.toMetersPerSecondSquared, proposal.uuid.toString))

  /**
    * Take one last look before crossing, and cross if it still holds up.
    *
    * That second look is what stops two cars taking the same gap: whoever gets there first is
    * present in the state the runner-up is judged against, and either the safety criterion or
    * the incentive turns them away. With a warning period it is doing more than settling ties
    * - the road has had a second or so to change since the driver made its mind up, and a gap
    * that has closed in the meantime is a change that doesn't happen.
    */
  private def reconsider(road: TrackRoad, proposal: Proposal): TrackRoad =
    road.lanes(proposal.from).vehicles.indexWhere(_.uuid == proposal.uuid) match {
      case -1 => road // Already moved on somebody else's account.
      case index =>
        if (stillWorthIt(road, proposal, index)) move(road, proposal.from, index, proposal.to)
        else if (proposal.insistent && stillWaiting(road, proposal.from, index)) road
        else abandon(road, proposal.from, index)
    }

  /**
    * A forced change sits with its indicator on until the gap it wants opens up, rather than
    * giving up on it. The car it means to cut in front of has had a second to close the gap
    * it was picked for, and a button that quietly does nothing is worse than one that waits.
    *
    * Not indefinitely, though: on a road with nowhere to go, a car indicating for the rest of
    * the run is a light left blinking rather than a change about to happen.
    */
  private def stillWaiting(road: TrackRoad, from: Int, index: Int): Boolean =
    road.lanes(from).vehicles(index).intent.exists { intent =>
      intent.elapsed < intent.duration * Patience
    }

  /**
    * A forced change asks only whether the car will fit, since it was never made on MOBIL's
    * terms in the first place. Everybody else has to satisfy both criteria all over again.
    */
  private def stillWorthIt(road: TrackRoad, proposal: Proposal, index: Int): Boolean = {
    val fromLane = road.lanes(proposal.from)
    val mover = fromLane.vehicles(index)
    if (proposal.insistent)
      fits(
        road.lanes(proposal.to),
        road.transpose(fromLane.path, road.lanes(proposal.to).path, mover.s),
        mover.length
      )
    else
      road.mobil.wants(
        situationFor(road, proposal.from, index, proposal.to),
        proposal.from,
        proposal.to
      )
  }

  /** Think better of a change, and stop indicating it. */
  private def abandon(road: TrackRoad, from: Int, index: Int): TrackRoad =
    withVehicleAt(road, from, index)(vehicle => vehicle.copy(intent = None))

  /**
    * Start indicating, rather than moving.
    *
    * Nothing on the road moves as a result, so unlike a change itself these don't interact:
    * every driver that wants a lane this tick gets to say so.
    */
  private def announce(duration: Time)(road: TrackRoad, proposal: Proposal): TrackRoad =
    road.lanes(proposal.from).vehicles.indexWhere(_.uuid == proposal.uuid) match {
      case -1 => road
      case index =>
        withVehicleAt(road, proposal.from, index)(
          _.copy(intent = Some(LaneChangeIntent(proposal.to, duration)))
        )
    }

  /**
    * The changes nobody can account for: a driver moving over because it felt like it.
    *
    * Everything else on this road is a driver being sensible. MOBIL proposes only the moves
    * that pay, so traffic built out of it alone is better-behaved than traffic is, and the
    * interesting things - a gap taken badly, a wave nobody can trace - come from exactly the
    * moves it will not make. The button does one of those on demand; this is the same idea
    * left running, at whatever rate the road is set to.
    *
    * Drawn per driver rather than per road, so the rate reads the same whether you are
    * watching ten cars or forty: a road of twice as many drivers has twice as many whims.
    *
    * These go out under the same [[LaneChangeIntent.insistent]] flag the button uses, and for
    * the same reason. A change made for no reason cannot be re-examined for its reasons when
    * the warning is up - MOBIL would refuse every one of them, and the dial would do nothing
    * but light up indicators.
    */
  private def withWhimsy(road: TrackRoad, dt: Time): TrackRoad = {
    val chance = road.whimsy * (dt / OncePerMinute)
    if (chance <= 0.0) road
    else {
      // Gathered before any of them is acted on, so that a whim can only ever cost its own
      // driver a turn - who else is asked this tick does not depend on who went first.
      val asking = for {
        (lane, from) <- road.lanes.zipWithIndex
        vehicle      <- lane.vehicles
        if vehicle.mayChangeLane
      } yield (from, vehicle.uuid)

      val (indulged, entropy) = asking.foldLeft((road, road.entropy)) {
        case ((current, seed), (from, uuid)) =>
          val wanted = stirred(seed)
          val which = stirred(wanted)
          if (unitOf(wanted) >= chance) (current, which)
          else (whim(current, from, uuid, unitOf(which)), which)
      }
      indulged.copy(entropy = entropy)
    }
  }

  /** One driver acting on a whim, if there is anywhere it could go. */
  private def whim(road: TrackRoad, from: Int, uuid: UUID, draw: Double): TrackRoad =
    road.lanes(from).vehicles.indexWhere(_.uuid == uuid) match {
      case -1 => road // Somebody else's change has already moved it.
      case index =>
        val mover = road.lanes(from).vehicles(index)
        /*
        Unreasonable is not the same as impossible. A whim ignores whether the change is worth
        making and who it inconveniences, which is the point of it, but a car still has to land
        on tarmac rather than on a bumper - so this asks the one question the button also asks.
         */
        val somewhereToGo = road.neighboursOf(from).filter { to =>
          fits(
            road.lanes(to),
            road.transpose(road.lanes(from).path, road.lanes(to).path, mover.s),
            mover.length
          )
        }

        if (somewhereToGo.isEmpty) road
        else {
          val to = somewhereToGo(math.min((draw * somewhereToGo.size).toInt, somewhereToGo.size - 1))
          road.laneChangeWarning match {
            case None => move(road, from, index, to)
            case Some(duration) =>
              withVehicleAt(road, from, index)(
                _.copy(intent = Some(LaneChangeIntent(to, duration, insistent = true)))
              )
          }
        }
    }

  /**
    * One car nearer whatever population is being asked for.
    *
    * A car at a time rather than all at once, so that dragging the dial across the range is
    * something you watch happen rather than something that has happened - and so that each
    * arrival is placed against the road as it stands after the last one, instead of a dozen
    * cars all being dropped into the same gap because it was the roomiest when the batch
    * started. At a frame a car this is still faster than anyone can drag a slider.
    */
  def approaching(road: TrackRoad, perKm: Double): TrackRoad = {
    val target = road.carsFor(perKm)
    if (target > road.vehicleCount) withOneMoreCar(road)
    else if (target < road.vehicleCount) withOneFewerCar(road)
    else road
  }

  /**
    * Added to the emptiest lane, so the road fills evenly rather than stacking up in one.
    *
    * Lanes are tried in order of how full they are and the first that accepts wins, because a
    * lane can be out of room while its neighbour is not - and a road that gave up on the first
    * refusal would stop filling long before it was full.
    */
  private def withOneMoreCar(road: TrackRoad): TrackRoad =
    road.lanes.indices
      .sortBy(index => densityOf(road.lanes(index)))
      .map(index => (index, TrackLane.withOneMore(road.lanes(index))))
      .find { case (index, lane) => lane.vehicles.size > road.lanes(index).vehicles.size }
      .fold(road) { case (index, lane) => road.copy(lanes = road.lanes.updated(index, lane)) }

  /** Taken from the fullest lane, for the same reason. */
  private def withOneFewerCar(road: TrackRoad): TrackRoad =
    road.lanes.indices
      .sortBy(index => -densityOf(road.lanes(index)))
      .find(index => road.lanes(index).vehicles.nonEmpty)
      .fold(road) { index =>
        road.copy(lanes = road.lanes.updated(index, TrackLane.withOneFewer(road.lanes(index))))
      }

  private def densityOf(lane: TrackLane): Double =
    lane.vehicles.size / lane.path.totalLength.toKilometers

  /** The range the density dial covers, from nearly empty to nearly solid. */
  val Sparsest: Double = 5.0

  /**
    * Past about seventy cars to the kilometre there is no road left to hand out - eight metres
    * of car and six of gap is fourteen metres a car, and that is what a standstill looks like.
    * The dial stops just short of it: the last cars in are declined for want of room rather
    * than accepted into a road that has none, so the top of the range is where traffic stops
    * rather than where the arithmetic does.
    */
  val Densest: Double = 70.0

  /** The rate whimsy is quoted in, so the dial means something a person can picture. */
  private val OncePerMinute: Time = Seconds(60)

  /**
    * Xorshift, which is all the randomness a lane change needs and is worth having in full
    * sight: it is a pure function of the seed, so a road carries its own unpredictability
    * around with it and two runs of the same road are the same run.
    */
  private def stirred(seed: Long): Long = {
    // Zero is xorshift's one fixed point, and a road that reached it would quietly stop having
    // whims at all rather than fail - so it is stepped over on the way past.
    val from = if (seed == 0L) FirstSeed else seed
    val a = from ^ (from << 13)
    val b = a ^ (a >>> 7)
    b ^ (b << 17)
  }

  /** A seed as a number from 0 to 1, taken off the top bits, which are the well-mixed ones. */
  private def unitOf(seed: Long): Double =
    (seed >>> 11).toDouble / (1L << 53).toDouble

  /**
    * Any non-zero start will do, since xorshift is stuck at zero and indifferent otherwise.
    */
  val FirstSeed: Long = 0x5DEECE66DL

  /**
    * The most capricious a road can be set: two arbitrary changes per driver per minute.
    *
    * A driver is busy for about six seconds per change once the indicating, the crossing and
    * the settling afterwards are counted, so ten a minute is the most any car could physically
    * manage. Two is the top of the dial because past it the traffic stops being traffic that
    * sometimes does something inexplicable and becomes traffic that does nothing else.
    */
  val MostCapricious: Double = 2.0

  /** Turn a 0-to-1 dial into the rate it means. */
  def whimsyFor(dial: Double): Double =
    MostCapricious * math.max(0.0, math.min(1.0, dial))

  private def withVehicleAt(road: TrackRoad, lane: Int, index: Int)(
    change: TrackVehicle => TrackVehicle
  ): TrackRoad = {
    val target = road.lanes(lane)
    road.copy(
      lanes = road.lanes
        .updated(
          lane,
          target.copy(vehicles = target.vehicles.updated(index, change(target.vehicles(index))))
        )
    )
  }

  /**
    * Weigh up one car's move into one neighbouring lane.
    *
    * The hypothetical lanes are built for real - the mover spliced out of one and into the
    * other - and then asked the ordinary question about who is following whom. Reasoning
    * about the wrap-around cases in the abstract is where this gets subtly wrong; a lane of
    * two cars where the leader is also the follower is a case you want the one code path to
    * handle rather than a case you want to remember.
    */
  private def situationFor(
    road: TrackRoad,
    from: Int,
    index: Int,
    to: Int
  ): LaneChangeSituation = {
    val fromLane = road.lanes(from)
    val toLane = road.lanes(to)
    val mover = fromLane.vehicles(index)

    val arrivalS = road.transpose(fromLane.path, toLane.path, mover.s)
    val arriving = mover.at(arrivalS, mover.speed)

    val fromAfter = fromLane.without(mover)
    val toAfter = toLane.withVehicleAdded(arriving)

    val oldFollower = TrackLane.followerOf(fromLane, index)
    val newFollower = TrackLane.followerAt(toLane, arrivalS)

    LaneChangeSituation(
      selfBefore = accelerationOf(fromLane, mover),
      selfAfter = accelerationOf(toAfter, arriving),
      oldFollowerBefore = oldFollower.map(accelerationOf(fromLane, _)).getOrElse(NoAcceleration),
      oldFollowerAfter = oldFollower.map(accelerationOf(fromAfter, _)).getOrElse(NoAcceleration),
      newFollowerBefore = newFollower.map(accelerationOf(toLane, _)).getOrElse(NoAcceleration),
      newFollowerAfter = newFollower.map(accelerationOf(toAfter, _)).getOrElse(NoAcceleration)
    )
  }

  private def accelerationOf(lane: TrackLane, vehicle: TrackVehicle): Acceleration =
    lane.vehicles.indexWhere(_.uuid == vehicle.uuid) match {
      case -1    => NoAcceleration // Not on this lane, so it isn't following anybody on it.
      case index => TrackLane.accelerationAt(lane, index)
    }

  private def move(road: TrackRoad, from: Int, index: Int, to: Int): TrackRoad = {
    val fromLane = road.lanes(from)
    val toLane = road.lanes(to)
    val mover = fromLane.vehicles(index)

    val arriving = mover
      .copy(
        s = road.transpose(fromLane.path, toLane.path, mover.s),
        lateral = road.arrivalOffset(from, to),
        changeCooldown = road.mobil.cooldown,
        intent = None // Whatever it was indicating, it has now done.
      )
      .placedOn(toLane.path)

    road.copy(
      lanes = road.lanes
        .updated(from, fromLane.without(mover))
        .updated(to, toLane.withVehicleAdded(arriving))
    )
  }

  /**
    * Make the rudest lane change the road currently allows.
    *
    * This is the button, and it deliberately ignores both of MOBIL's criteria: of every move
    * that would physically fit, it takes the one that forces the hardest braking on the car
    * behind. That is the single errant change the whole ring exists to demonstrate - one
    * driver's bad decision, and a wave that outlives it.
    *
    * It still indicates first where the road asks drivers to, because this is the change you
    * pressed a button to watch, and being told where to look is the whole value of a warning.
    * What it doesn't do is change its mind when the warning is up.
    */
  def forceLaneChange(road: TrackRoad): TrackRoad = {
    val options = for {
      (lane, from)     <- road.lanes.zipWithIndex
      (vehicle, index) <- lane.vehicles.zipWithIndex
      if vehicle.intent.isEmpty
      to <- road.neighboursOf(from)
      arrivalS = road.transpose(lane.path, road.lanes(to).path, vehicle.s)
      if fits(road.lanes(to), arrivalS, vehicle.length)
      if road.laneChangeWarning.forall(warning => willStillFit(road, from, index, to, warning))
      situation = situationFor(road, from, index, to)
    } yield (from, index, to, situation.newFollowerAfter, vehicle.uuid)

    options
      .sortBy(option => (option._4.toMetersPerSecondSquared, option._5.toString))
      .headOption
      .fold(road) {
        case (from, index, to, _, _) =>
          road.laneChangeWarning match {
            case None => move(road, from, index, to)
            case Some(duration) =>
              withVehicleAt(road, from, index)(
                _.copy(intent = Some(LaneChangeIntent(to, duration, insistent = true)))
              )
          }
      }
  }

  /**
    * Will there still be room by the time a driver that indicates first actually goes?
    *
    * The rudest change available is by definition the one into the tightest gap, and on a
    * road where the lanes run at different speeds the tightest gap is the one most likely to
    * have shut by the end of the warning. Asking the question a warning ahead - everyone
    * holding their present speed - is what stops the button announcing a change that then
    * can't be made, which from the outside is a button that does nothing.
    */
  private def willStillFit(road: TrackRoad,
                           from: Int,
                           index: Int,
                           to: Int,
                           warning: Time): Boolean = {
    val fromLane = road.lanes(from)
    val toLane = road.lanes(to)
    val mover = fromLane.vehicles(index)

    fits(
      coasted(toLane, warning),
      road.transpose(fromLane.path, toLane.path, mover.s + mover.speed * warning),
      mover.length
    )
  }

  /** Where a lane's traffic would be after `elapsed`, if every driver held its speed. */
  private def coasted(lane: TrackLane, elapsed: Time): TrackLane =
    lane.copy(vehicles = lane.vehicles.map { vehicle =>
      vehicle.at(lane.path.normalize(vehicle.s + vehicle.speed * elapsed), vehicle.speed)
    })

  /** Is there actually room, front and back, for a car to appear here? */
  private def fits(lane: TrackLane, s: Length, length: Length): Boolean = {
    val ahead = TrackLane.gapFrom(lane, s, length, TrackLane.leaderAt(lane, s))
    val behind = TrackLane.followerAt(lane, s) match {
      case Some(follower) => lane.path.forwardGap(follower.s, s) - length
      case None           => lane.path.totalLength - length
    }
    ahead > ForcedClearance && behind > ForcedClearance
  }

  /**
    * Slide cars caught between lanes the rest of the way over.
    *
    * With instant changes nothing is ever offset and this does nothing at all, which is why
    * turning the animation on is a matter of giving the road a duration rather than teaching
    * anything new about how a car is drawn.
    */
  private def settle(road: TrackRoad, lane: TrackLane, dt: Time): TrackLane =
    road.laneChangeDuration match {
      case None => lane
      case Some(duration) =>
        val step = road.laneWidth * (dt / duration)
        lane.copy(vehicles = lane.vehicles.map { vehicle =>
          val remaining = vehicle.lateral.abs - step
          if (vehicle.lateral.abs < Settled) vehicle
          else if (remaining <= Meters(0)) vehicle.copy(lateral = Meters(0))
          else if (vehicle.lateral < Meters(0)) vehicle.copy(lateral = -remaining)
          else vehicle.copy(lateral = remaining)
        })
    }
}
