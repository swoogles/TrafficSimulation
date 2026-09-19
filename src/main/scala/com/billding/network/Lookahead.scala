package com.billding.network

import scala.annotation.tailrec
import squants.space.{Length, Meters}

/**
  * How far a vehicle can see down the road ahead of it.
  *
  * A [[NetworkVehicle]]'s gap to its leader cannot be answered by looking at
  * its own section alone - a section boundary is not a wall, and the plan is
  * explicit that a stopped leader just past a seam is still a leader. This
  * walks forward from a vehicle's position, section by section, until it
  * finds a vehicle ahead or exhausts the distance it was asked to search.
  */
object Lookahead {

  /**
    * Guard against a cycle of zero-length (or merely short) sections walking
    * forever: the walk is capped by distance first, but a ring built entirely
    * from sections shorter than a hop's share of `within` would still take
    * unbounded hops to exhaust that distance, so hops are capped too.
    */
  private val MaxHops: Int = 20

  /**
    * The vehicle ahead of `of`, and the bumper-to-bumper gap to it along the
    * road - not a straight line - measured through as many downstream
    * sections as `within` allows.
    *
    * The gap follows [[com.billding.traffic.TrackLane]]'s convention: `of.s`
    * is read as this vehicle's own front bumper, and only the leader's length
    * is subtracted from the distance to its `s` (which is read the same way),
    * so a gap of zero means bumpers touching, not centers touching.
    *
    * Continuation past `of`'s own section follows `of.next` for exactly the
    * first hop - that choice belongs to the vehicle doing the looking, the
    * way a Y-split must only be seen down the branch `next` points at.
    * Beyond that second section, `next` has nothing more to say: it is a
    * single movement, not a route, so every hop after the first follows
    * whatever section it lands on's own first declared outgoing movement.
    * That is a placeholder the same way [[NetworkVehicle.enteringAt]]'s
    * default is - reasonable only because phase E has not filled in routing
    * yet, and something a real route will replace once it exists.
    */
  def leaderOf(
    traffic: NetworkTraffic,
    index: NetworkIndex,
    of: NetworkVehicle,
    within: Length
  ): Option[(NetworkVehicle, Length)] = {

    def vehicleLength(vehicle: NetworkVehicle): Length = vehicle.piloted.width

    def sectionLength(section: SectionId): Option[Length] =
      index.network.section(section).map(_.length)

    def movement(id: MovementId): Option[Movement] =
      index.network.movements.find(_.id == id)

    // hop 0 is `of`'s own section; the transition out of it is the one place
    // `of.next` is consulted. Every later transition follows the section's
    // own first declared outgoing movement instead.
    def continuationFrom(section: SectionId, hop: Int): Option[SectionId] =
      if (hop == 0) of.next.flatMap(movement).map(_.to)
      else index.successors(section).headOption

    @tailrec
    def go(
      section: SectionId,
      // The position to search strictly ahead of in this section - Some(s)
      // in the section `of` started on, None in every section reached by
      // walking forward, where the whole section is ahead of `of`.
      searchFromS: Option[Length],
      travelled: Length,
      hop: Int
    ): Option[(NetworkVehicle, Length)] = {
      val nearestAhead = traffic
        .on(section)
        .filter(vehicle => vehicle.piloted.uuid != of.piloted.uuid && searchFromS.forall(vehicle.s > _))
        .sortBy(_.s.toMeters)
        .headOption

      nearestAhead match {
        case Some(leader) =>
          val distanceInSection = leader.s - searchFromS.getOrElse(Meters(0))
          val gap = travelled + distanceInSection - vehicleLength(leader)
          if (gap <= within) Some((leader, gap)) else None

        case None =>
          sectionLength(section) match {
            case None => None // A section the index doesn't know about is a dead end.
            case Some(length) =>
              val remainingInSection = length - searchFromS.getOrElse(Meters(0))
              val travelledToSectionEnd = travelled + remainingInSection
              if (travelledToSectionEnd >= within || hop >= MaxHops) None
              else
                continuationFrom(section, hop) match {
                  case Some(next) => go(next, None, travelledToSectionEnd, hop + 1)
                  case None       => None // Dangling end: the implicit sink, nobody beyond it.
                }
          }
      }
    }

    go(of.section, Some(of.s), Meters(0), hop = 0)
  }
}
