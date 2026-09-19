package com.billding.network

import java.util.UUID

import squants.motion.{Acceleration, MetersPerSecondSquared}
import squants.space.{Length, Meters}
import squants.Time

import com.billding.traffic.{LaneChangeSituation, MOBIL}

/**
  * Discretionary lane changes on the graph (card F2), reusing [[MOBIL]]'s
  * incentive and safety criteria exactly as `TrackRoad` uses them - only the
  * geometry underneath is different: [[LaneMapping.neighbourAt]] answers
  * "where would I land in the next lane over" in place of `TrackRoad.transpose`,
  * and [[Lookahead]] (via [[NetworkTick.accelerationOf]]) answers "what would
  * I - or whoever ends up behind me - be doing" across section seams, not just
  * within one lane's own path.
  *
  * `NetworkVehicle` carries no `changeCooldown`/`intent` fields the way
  * `TrackVehicle` does - this card touches only this file, `NetworkTick.scala`
  * and its own spec, so those fields cannot be added here. `lateral` is
  * pressed into doing both jobs `TrackVehicle` splits across two fields: it is
  * the sideways offset a change leaves behind (as it already is for
  * `TrackVehicle`), and - since nothing else marks "this driver just moved" -
  * its decay back to zero is also the cooldown clock. A vehicle is only
  * considered for a fresh change once its last one has fully settled, which is
  * `SettleDuration` after it happened, matching [[MOBIL.DefaultCooldown]].
  */
object NetworkLaneChange {

  private val mobil: MOBIL = MOBIL()

  private val NoAcceleration: Acceleration = MetersPerSecondSquared(0)

  /** How long a change takes to settle - and, doubling as the cooldown, how long before the same driver is asked again. */
  private val SettleDuration: Time = MOBIL.DefaultCooldown

  /**
    * The lateral distance a change is drawn starting from. Purely a timing
    * peg for the settle-doubles-as-cooldown decay above; nothing in this
    * card renders it, so its magnitude only has to be nonzero and constant.
    */
  private val SettleMagnitude: Length = Meters(3.5)

  /** Below this a settling offset has arrived, and is snapped flat exactly as `TrackRoad.settle` does. */
  private val Settled: Length = Meters(0.01)

  /** Synthetic lane "indices" fed to [[MOBIL.biasFor]], which only ever compares them by `<`. */
  private val FromIndex: Int = 0
  private def toIndexFor(side: Side): Int = if (side == Side.RightOf) -1 else 1

  private final case class Proposal(
    uuid: UUID,
    to: SectionId,
    toS: Length,
    side: Side,
    motivation: Acceleration
  )

  /**
    * One tick's worth of discretionary lane changes, run as its own phase from
    * `traffic` exactly as it stood at the start of the tick - the same reason
    * [[NetworkTick.advance]]'s Read/Integrate/Commit phases each work from one
    * frozen snapshot rather than a world other vehicles have already half
    * moved through.
    *
    * Settling runs first so a driver's eligibility for a fresh change
    * (`lateral` fully decayed) reflects time already spent since its last one,
    * then proposals are gathered from that same, still only settled-not-moved,
    * snapshot, ranked, and committed one at a time with a fresh look at the
    * road as it now stands - `TrackRoad.reconsider`'s reason exactly: whoever
    * commits first is present in the state the next proposal is judged
    * against, which is what stops two cars taking the same gap.
    */
  def advance(traffic: NetworkTraffic, index: NetworkIndex, dt: Time): NetworkTraffic = {
    val settling = decaySettling(traffic, dt)
    ranked(freshProposals(settling, index)).foldLeft(settling)(reconsider(index))
  }

  private def mayChangeLane(vehicle: NetworkVehicle): Boolean = vehicle.lateral == Meters(0)

  /** Every discretionary change worth making, judged against one frozen snapshot. */
  private def freshProposals(traffic: NetworkTraffic, index: NetworkIndex): List[Proposal] =
    for {
      mover <- traffic.all
      if mayChangeLane(mover)
      side <- List(Side.LeftOf, Side.RightOf)
      (to, toS) <- LaneMapping.neighbourAt(index.network, mover.section, mover.s, side).toList
      situation = situationFor(traffic, index, mover, to, toS)
      if mobil.wants(situation, FromIndex, toIndexFor(side))
    } yield Proposal(
      mover.piloted.uuid,
      to,
      toS,
      side,
      mobil.motivation(situation, FromIndex, toIndexFor(side))
    )

  /** One move per car per tick, best lane first - [[TrackRoad.ranked]] verbatim, on [[Proposal]] instead. */
  private def ranked(proposals: List[Proposal]): List[Proposal] =
    proposals
      .groupBy(_.uuid)
      .values
      .toList
      .map(_.maxBy(_.motivation.toMetersPerSecondSquared))
      .sortBy(p => (-p.motivation.toMetersPerSecondSquared, p.uuid.toString))

  /**
    * A last look before crossing: judged fresh against `traffic` as it stands
    * at this point in the fold, not the snapshot the proposal was ranked from.
    * A mover already gone (departed some other way) is simply skipped.
    */
  private def reconsider(index: NetworkIndex)(traffic: NetworkTraffic, proposal: Proposal): NetworkTraffic =
    traffic.vehicleWith(proposal.uuid) match {
      case None => traffic
      case Some(mover) =>
        val situation = situationFor(traffic, index, mover, proposal.to, proposal.toS)
        if (mobil.wants(situation, FromIndex, toIndexFor(proposal.side))) commit(index, traffic, mover, proposal)
        else traffic
    }

  private def commit(index: NetworkIndex, traffic: NetworkTraffic, mover: NetworkVehicle, proposal: Proposal): NetworkTraffic = {
    // Refreshed exactly the way arriving in any new section refreshes it - see
    // `NetworkVehicle.chooseNext`, also used by `enteringAt` and `settle` -
    // since `next` names a movement out of a *section*, and the one the mover
    // carried named one out of the lane it is leaving.
    val (next, remainingRoute, routeFailed) =
      NetworkVehicle.chooseNext(index, proposal.to, mover.route, mover.destination, mover.routeFailed)

    val arriving = mover.copy(
      section = proposal.to,
      s = proposal.toS,
      lateral = arrivalLateral(proposal.side),
      next = next,
      route = remainingRoute,
      routeFailed = routeFailed
    )

    NetworkTraffic.place(removeFrom(traffic, mover.piloted.uuid), arriving)
  }

  private def arrivalLateral(side: Side): Length = if (side == Side.RightOf) SettleMagnitude else -SettleMagnitude

  private def removeFrom(traffic: NetworkTraffic, uuid: UUID): NetworkTraffic =
    NetworkTraffic(traffic.bySection.view.mapValues(_.filterNot(_.piloted.uuid == uuid)).toMap)

  /**
    * Weigh one car's move into one neighbouring lane - [[TrackRoad.situationFor]]
    * ported onto the graph. The hypothetical lanes are built for real, the
    * mover spliced out of its old section and into the new one at its mapped
    * position, and the same [[NetworkTick.accelerationOf]] the ordinary tick
    * uses is asked about every driver in every version of the world.
    *
    * Followers are found by a plain nearest-vehicle-behind search within the
    * section the mover is leaving or joining, not a seam-crossing walk the way
    * [[Lookahead.leaderOf]] does for leaders - there is no published
    * backward-searching equivalent to call, and inventing one is out of scope
    * for this card (`LaneMapping`/`Lookahead` are read-only here). A follower
    * that is itself still straddling the previous seam is missed by this, the
    * same way it would be missed by a plain `s`-ordered scan; leaders are not
    * affected, since [[NetworkTick.accelerationOf]] always goes through
    * [[Lookahead.leaderOf]] regardless of which hypothetical world it is asked
    * about.
    */
  private def situationFor(
    traffic: NetworkTraffic,
    index: NetworkIndex,
    mover: NetworkVehicle,
    to: SectionId,
    toS: Length
  ): LaneChangeSituation = {
    val uuid = mover.piloted.uuid
    val arriving = mover.copy(section = to, s = toS)

    val withoutMover = removeFrom(traffic, uuid)
    val withMoverArrived = NetworkTraffic.place(withoutMover, arriving)

    val oldFollower = followerInSection(traffic, mover.section, mover.s, uuid)
    val newFollower = followerInSection(traffic, to, toS, uuid)

    LaneChangeSituation(
      selfBefore = NetworkTick.accelerationOf(traffic, index, mover),
      selfAfter = NetworkTick.accelerationOf(withMoverArrived, index, arriving),
      oldFollowerBefore = oldFollower.map(NetworkTick.accelerationOf(traffic, index, _)).getOrElse(NoAcceleration),
      oldFollowerAfter = oldFollower.map(NetworkTick.accelerationOf(withoutMover, index, _)).getOrElse(NoAcceleration),
      newFollowerBefore = newFollower.map(NetworkTick.accelerationOf(traffic, index, _)).getOrElse(NoAcceleration),
      newFollowerAfter = newFollower.map(NetworkTick.accelerationOf(withMoverArrived, index, _)).getOrElse(NoAcceleration)
    )
  }

  /** The nearest vehicle behind `s` in `section`, other than `excluding` - `traffic.on` is already leader-first (descending `s`). */
  private def followerInSection(traffic: NetworkTraffic, section: SectionId, s: Length, excluding: UUID): Option[NetworkVehicle] =
    traffic.on(section).find(v => v.piloted.uuid != excluding && v.s < s)

  /**
    * Slide every car caught between lanes the rest of the way over, one
    * `dt`'s worth - [[com.billding.traffic.TrackRoad.settle]] verbatim, at a
    * fixed [[SettleMagnitude]]/[[SettleDuration]] rather than a road's own
    * configured lane width and duration, since this always animates (there is
    * no separate cooldown field to fall back to when it doesn't).
    */
  private def decaySettling(traffic: NetworkTraffic, dt: Time): NetworkTraffic =
    NetworkTraffic.of(traffic.all.map(decayOne(_, dt)))

  private def decayOne(vehicle: NetworkVehicle, dt: Time): NetworkVehicle =
    if (vehicle.lateral.abs < Settled) {
      if (vehicle.lateral == Meters(0)) vehicle else vehicle.copy(lateral = Meters(0))
    } else {
      val step = SettleMagnitude * (dt / SettleDuration)
      val remaining = vehicle.lateral.abs - step
      if (remaining <= Meters(0)) vehicle.copy(lateral = Meters(0))
      else if (vehicle.lateral < Meters(0)) vehicle.copy(lateral = -remaining)
      else vehicle.copy(lateral = remaining)
    }
}
