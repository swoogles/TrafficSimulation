package com.billding.network

import squants.motion.{MetersPerSecond, MetersPerSecondSquared}
import squants.space.{Length, Meters}

/**
  * Whether a vehicle approaching a controlled [[Movement]] may cross the line into it,
  * and - when it may not - how far short of the line it should be treated as blocked.
  *
  * `H1` (`Conflicts`) found where movements physically cross; this card turns that into a
  * right-of-way decision. Two rules, per `IMPLEMENTATION_TASKS.md` H2 and
  * `creativity_plan.md`'s "Required simulation behavior":
  *
  *   - `Stop`: not eligible even to be considered until the vehicle has actually come to
  *     rest at the line. Only once stopped does it get a turn to check the rules below.
  *   - `Yield`: no stopping prerequisite - it may sail through if the rules below already
  *     say so.
  *   - Both: the conflict rule (no conflicting movement has a vehicle closer to the shared
  *     [[ConflictPoint]] than this one is) and the downstream rule (there is room to
  *     actually enter `movement.to`, per the plan's "never enter a junction you cannot
  *     leave").
  *
  * `Uncontrolled` never reaches any of this: [[stoppingConstraint]]'s very first check
  * rules it out, so an ordinary road seam costs exactly one `Control` comparison and
  * nothing else - the genuine no-op `SeamInvarianceSpec` (C5) depends on.
  */
object Admission {

  /** Below this speed a vehicle counts as "stopped" for `Stop`'s full-stop prerequisite. */
  private val StoppedSpeed = MetersPerSecond(0.05)

  /** A freshly-placed [[NetworkVehicle]]'s default `acceleration` - see [[stoppedAt]]. */
  private val NoAcceleration = MetersPerSecondSquared(0)

  /**
    * How much slack beyond a vehicle's own `minimumDistance` (the IDM's `s0`) still counts
    * as "at the line" for `Stop`'s full-stop prerequisite.
    *
    * A vehicle braking toward a stationary obstacle - which is exactly how
    * [[NetworkTick.accelerationOf]] feeds this back into the IDM - settles asymptotically
    * at gap `s0`, never at a bare zero: that is the same equilibrium every stopped-queue
    * gap in this codebase already sits at (see `IntelligentDriverModelImpl.sStar`). Asking
    * for a gap tighter than `s0` would mean "eligible to proceed" is a line no driver's
    * physics ever actually crosses. A little more than that equilibrium is "clearly
    * waiting at the line" without demanding the vehicle close the last few centimetres of
    * an asymptote first.
    */
  private val AtLineSlack = Meters(0.5)

  /**
    * Cached [[Conflicts.conflicts]] over the whole network, grouped by the [[MovementId]]s
    * each [[ConflictPoint]] mentions.
    *
    * `Conflicts` is documented as edit-time work, not per-tick - sampling every pair of a
    * junction's curves is too much to redo for every vehicle on every tick. There is nowhere
    * left to precompute this once and hand it down: `RoadNetwork` and `NetworkIndex` are
    * off this card's touch list, and [[NetworkTick.accelerationOf]]'s signature is shared
    * with `NetworkLaneChange.situationFor`, so it cannot grow a new parameter either. A
    * single-slot cache keyed by the `RoadNetwork` actually in hand is what is left: the
    * network driving a running simulation is the same object tick after tick (it only
    * changes when something edits the graph), so this recomputes once per network version
    * and then reuses that result for as long as the network does not change. Recomputing
    * is triggered by reference identity, not full structural equality, so checking the
    * cache costs a pointer compare, not a walk of every section and movement.
    *
    * Safe as ordinary (non-`@volatile`, non-synchronized) mutable state because this whole
    * codebase runs on Scala.js: `sbt test` executes on Node's single JS thread, so there is
    * never a second thread racing this one. Every field this cache exposes is read back out
    * as an immutable `Map`/`List`, so nothing about the cache itself is mutated by a caller.
    */
  private var cachedNetwork: RoadNetwork = _
  private var cachedConflicts: Map[MovementId, List[ConflictPoint]] = Map.empty

  private def conflictsByMovement(net: RoadNetwork): Map[MovementId, List[ConflictPoint]] = {
    if (!net.eq(cachedNetwork)) {
      val found = Conflicts.conflicts(net, net.movements.map(_.id).toSet)
      cachedConflicts = found
        .flatMap(c => List(c.a -> c, c.b -> c))
        .groupBy(_._1)
        .view
        .mapValues(_.map(_._2))
        .toMap
      cachedNetwork = net
    }
    cachedConflicts
  }

  private def movementById(net: RoadNetwork, id: MovementId): Option[Movement] =
    net.movements.find(_.id == id)

  /**
    * How far `vehicle` has driven along `movementId`'s own curve, if it is somewhere on it
    * right now - `Some(vehicle.s)` while still on the movement's `from` (the curve's own
    * coordinates agree with `from`'s over that stretch, per `Conflicts.curveOf`), or
    * `Some(fromLength + vehicle.s)` once it has crossed into `to`. `None` when `vehicle` is
    * neither approaching nor inside `movementId` at all.
    *
    * A vehicle already inside `to` is only ever attributed to *a* movement that ends there,
    * not necessarily the one it actually arrived by, when more than one movement shares that
    * `to` - `NetworkVehicle` does not keep "which movement did I last take," only `next`
    * (what it will take leaving `to`). That is a conservative approximation, not an unsafe
    * one: it can only make a movement look more contended than it is (an extra vehicle
    * counted as a contender that was not really racing for this particular conflict),
    * never less, so the worst it costs is an occasional unnecessary yield, not a missed one.
    */
  private def positionAlong(net: RoadNetwork, movementId: MovementId, movement: Movement, vehicle: NetworkVehicle): Option[Length] =
    if (vehicle.section == movement.from && vehicle.next.contains(movementId)) Some(vehicle.s)
    else if (vehicle.section == movement.to) net.section(movement.from).map(_.length + vehicle.s)
    else None

  /**
    * The nearest any vehicle in `traffic` (other than `self`) sits to *its own* line, among
    * those on `otherId` that have not already reached the shared conflict at `conflictS` -
    * `None` if nobody on `otherId` is still short of it at all.
    *
    * Distance to *the conflict point* would seem the more direct reading of "nearest to the
    * conflict point wins," and an earlier version of this file measured it that way - each
    * movement's own recorded position for the pair, `ConflictPoint.sA`/`sB`. It is wrong.
    * `NetworkFixtures.fourWayCrossing` (and any junction whose lanes sit off the centreline,
    * which is every realistic one) puts a movement's *pair* of conflicts at two different
    * offsets from its own line - a bit short of it for one crossing street, a bit past it for
    * the other, since the crossing streets are themselves offset to either side of centre.
    * Two vehicles that are, physically, at exactly the same distance from the junction box
    * can then show *different* sA/sB-based distances depending purely on which side of the
    * box the label happens to fall on - and because that offset flips sign in exactly the way
    * that walks around the four approaches, four vehicles placed with genuine (not
    * coincidental) symmetry resolve into a four-way cycle: north beats east, east beats
    * south, south beats west, west beats north, nobody with an empty losing list, and the
    * junction never issues its first admission. That is a standing deadlock this card exists
    * to rule out, and it was reproducible on this exact fixture under load from all four
    * approaches.
    *
    * Distance to *the vehicle's own line* does not have this defect: it is measured the same
    * way for every movement - how far it is from entering the box at all - so two vehicles
    * that are, physically, equally far from entering compare exactly equal, and a total order
    * (SectionId) settles it without any cyclic possibility, a total order being transitive by
    * construction. `conflictS` still does its own, different job here: a contender who has
    * already driven past the shared point on their own curve is no longer a contender for
    * *this* crossing (the `filter` below), regardless of how far they still have to go before
    * their own line - that part of "have they already gone by" is genuinely a fact about the
    * specific crossing, not about the approach in general.
    */
  private def nearestContender(
    net: RoadNetwork,
    traffic: NetworkTraffic,
    self: NetworkVehicle,
    otherId: MovementId,
    conflictS: Length
  ): Option[Length] =
    movementById(net, otherId).flatMap { other =>
      net.section(other.from).map(_.length).flatMap { otherLineS =>
        traffic.all
          .filter(_.piloted.uuid != self.piloted.uuid)
          .flatMap(v => positionAlong(net, otherId, other, v).map(pos => (pos, otherLineS - pos)))
          .filter { case (pos, _) => pos < conflictS }
          .map(_._2)
          .minByOption(_.toMeters)
      }
    }

  /** `(self's position on `movementId`'s curve, the other movement's id, its position on that curve)` for one `ConflictPoint`. */
  private def orient(movementId: MovementId, conflict: ConflictPoint): (Length, MovementId, Length) =
    if (conflict.a == movementId) (conflict.sA, conflict.b, conflict.sB) else (conflict.sB, conflict.a, conflict.sA)

  /**
    * Does `self` (already known to be `selfDistance` from its own line) beat the nearest
    * contender on `otherId` to their shared conflict point?
    *
    * Ties - and only ties - fall back to the stable order the card asks for: the movement
    * whose `from` section sorts first (plain string order on [[SectionId.value]]) wins.
    * That is a fixed, position-independent rule, so two movements at exactly the same
    * distance resolve the same way on every tick they are compared, rather than flipping
    * from one tick to the next - which is what turns "somebody has to go first" into
    * lasting progress instead of a coin flip that could in principle keep landing the same
    * way against the same driver forever. Once whoever wins clears the conflict point, its
    * distance no longer competes at all (`positionAlong`/the `filter` in
    * [[nearestContender]] drop it), so the next-nearest vehicle - on either movement - gets
    * its own turn. Nothing waits on a condition that never changes, which is what rules out
    * a standing deadlock: every tick either admits somebody or leaves distances exactly as
    * they were, and distances are never exactly the same for long once anything is moving.
    */
  private def winsSingleConflict(
    net: RoadNetwork,
    traffic: NetworkTraffic,
    self: NetworkVehicle,
    ownFrom: SectionId,
    selfDistance: Length,
    otherId: MovementId,
    otherConflictS: Length
  ): Boolean =
    nearestContender(net, traffic, self, otherId, otherConflictS) match {
      case None => true
      case Some(otherDistance) =>
        if (selfDistance.toMeters != otherDistance.toMeters) selfDistance < otherDistance
        else {
          val otherFrom = movementById(net, otherId).map(_.from.value).getOrElse("")
          ownFrom.value < otherFrom
        }
    }

  /**
    * Every conflict `movementId` is currently losing, paired with where along its own curve
    * that conflict sits - `Nil` when it beats all of them (or has none).
    *
    * This is a `List`, not a `Boolean`, because a lost conflict is not only a reason to
    * deny admission - it is also, potentially, a *closer* place to hold the vehicle than
    * the line itself. See [[stoppingConstraint]].
    */
  private def losingConflicts(net: RoadNetwork, traffic: NetworkTraffic, self: NetworkVehicle, movementId: MovementId, movement: Movement): List[Length] =
    net.section(movement.from).toList.flatMap { fromSection =>
      val selfDistance = fromSection.length - self.s
      conflictsByMovement(net).getOrElse(movementId, Nil).flatMap { conflict =>
        val (selfConflictS, otherId, otherConflictS) = orient(movementId, conflict)
        if (winsSingleConflict(net, traffic, self, movement.from, selfDistance, otherId, otherConflictS)) None
        else Some(selfConflictS)
      }
    }

  /**
    * Is there room for `self` to actually enter `movement.to`?
    *
    * Exactly [[Boundary]]'s own admission check, ported from "may a fresh car spawn here"
    * to "may this car already on the network cross into here": build the vehicle `self`
    * would be the instant it landed at the very start of `movement.to`, and ask
    * [[Lookahead.leaderOf]] whether anything already there sits within `self`'s own
    * following distance. Reusing that distance (rather than a new constant) keeps "enough
    * room to enter" meaning the same thing everywhere a vehicle's own following distance
    * already means it elsewhere in this codebase.
    */
  private def downstreamClear(index: NetworkIndex, traffic: NetworkTraffic, self: NetworkVehicle, movement: Movement): Boolean = {
    val placeholderNext = index.outgoing.getOrElse(movement.to, Nil).headOption.map(_.id)
    val landing = self.copy(section = movement.to, s = Meters(0), next = placeholderNext)
    Lookahead.leaderOf(traffic, index, landing, self.piloted.driver.minimumDistance).isEmpty
  }

  /**
    * Whether `vehicle` has decelerated enough, and sits close enough to `blockS`, to count
    * as fully stopped there - `Stop`'s prerequisite before it is even considered for the
    * checks above. See [[AtLineSlack]] for why the tolerance is `s0` plus a little, not a
    * bare zero.
    *
    * Being close enough is required either way; which of the two speed tests decides it
    * is what keeps this from becoming its own deadlock. A vehicle that has never yet been
    * let go needs the direct one: `speed <= StoppedSpeed`. But the very first tick that
    * check passes, [[stoppingConstraint]] reports `None` and [[NetworkTick.accelerationOf]]
    * lets it accelerate - and one tick of `accelerationAbility` (order 2 m/s^2 over a 0.1 s
    * step) is already enough to push `speed` back out past [[StoppedSpeed]]. Re-demanding
    * `speed <= StoppedSpeed` on *every* tick would revoke admission the instant it was
    * granted, brake it back down toward this same line, and repeat forever - a real
    * driver easing off the brake at a clear stop sign, forced to stamp on it again every
    * time the car dares to move. `vehicle.acceleration` - the acceleration
    * [[NetworkTick.integrate]] already committed last tick, an existing field, not new
    * state added for this - is the way out: a positive value there means last tick's
    * acceleration was the free, admitted kind, not braking toward this same obstacle, so
    * a vehicle already this close that is already pulling away stays eligible instead of
    * being yanked back. It starts at `Meters/SecondSquared(0)` for a freshly-placed
    * vehicle, which is not positive, so this can never be how a vehicle gets *admitted*
    * in the first place - only how one already admitted stays that way.
    */
  private def stoppedAt(vehicle: NetworkVehicle, distanceToBlock: Length): Boolean = {
    val closeEnough = distanceToBlock <= vehicle.piloted.driver.minimumDistance + AtLineSlack
    val stoppedOrAlreadyGoing = vehicle.speed <= StoppedSpeed || vehicle.acceleration > NoAcceleration
    closeEnough && stoppedOrAlreadyGoing
  }

  /**
    * The stopping constraint `vehicle` faces from [[Movement.control]], if any: `None` when
    * it is free to proceed (either the movement is `Uncontrolled`, or it is currently
    * admitted), `Some(distance)` - the remaining distance to wherever it must be held
    * instead - when it is not.
    *
    * That "wherever" is the nearer of two things: the line itself (`movement.from`'s own
    * length), or the nearest [[ConflictPoint]] `vehicle` is currently losing, when that
    * point sits *before* the line. The second case matters for junction geometry where a
    * movement's curve crosses another before it ever reaches its own section boundary - the
    * offset lanes `NetworkFixtures.fourWayCrossing` builds are exactly this, since a lane
    * built half a lane-width off the centreline crosses the near cross-street a little
    * before reaching what the graph calls the line. Stopping only at the graph's line in
    * that case would let a losing vehicle drive straight through the earlier crossing
    * first - so the obstacle this hands back always sits at the nearest place `vehicle` is
    * not yet allowed past, not merely at the section boundary.
    *
    * [[NetworkTick.accelerationOf]] is the only caller, and it is the whole reason this
    * returns a distance rather than a plain "yes/no": that distance stands in for a
    * stationary obstacle sitting exactly at the block, fed through the same IDM call an
    * ordinary leader is, which is what turns "not admitted yet" into gradual, comfortable
    * braking rather than a car that is doing 15 m/s one tick and parked the next.
    */
  def stoppingConstraint(traffic: NetworkTraffic, index: NetworkIndex, vehicle: NetworkVehicle): Option[Length] = {
    val net = index.network
    (for {
      movementId <- vehicle.next
      movement <- movementById(net, movementId)
      if movement.control != Control.Uncontrolled
      fromSection <- net.section(movement.from)
    } yield {
      val lineS = fromSection.length
      val losing = losingConflicts(net, traffic, vehicle, movementId, movement)

      // The nearest place `vehicle` may not yet pass: the line, or an earlier lost
      // conflict, whichever comes first along its own curve.
      val blockS = (lineS :: losing).min
      val distanceToBlock = blockS - vehicle.s

      val stopPrerequisiteOk = movement.control match {
        case Control.Stop         => stoppedAt(vehicle, distanceToBlock)
        case Control.Yield        => true
        case Control.Uncontrolled => true // unreachable - filtered above
      }

      val admitted = stopPrerequisiteOk && losing.isEmpty && downstreamClear(index, traffic, vehicle, movement)

      if (admitted) None else Some(distanceToBlock)
    }).flatten
  }
}
