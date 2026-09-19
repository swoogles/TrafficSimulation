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
    * Every section-local stretch of road `v`'s body currently covers, as
    * `(section, fromS, toS)` intervals in that section's own coordinates.
    *
    * A vehicle whose rear has cleared its head section (`v.s >= length`) has
    * a single entry: the ordinary case, `(v.section, v.s - length, v.s)`. One
    * whose front has crossed a seam before its rear did - `v.s < length` -
    * still has its tail sitting in whatever came before `v.section`, so this
    * walks `index.predecessors` backwards, handing off the still-unaccounted
    * overhang from one section to the next exactly the way [[NetworkTick]]'s
    * `settle` hands off overshoot going forward, until the whole length is
    * placed or [[MaxHops]] guards against a short-section cycle.
    *
    * A section fully swallowed by the overhang (the vehicle is longer than
    * it) gets a full-width entry and the walk continues past it - only the
    * *last* entry in the result is where the body's rear edge actually is;
    * the others are sections the body merely passes through. Branches at a
    * predecessor with more than one incoming section (a future merge point)
    * are all reported rather than guessed at, since nothing here records
    * which one a vehicle actually arrived from.
    */
  def occupiedSpan(v: NetworkVehicle, index: NetworkIndex): List[(SectionId, Length, Length)] = {
    val length = v.piloted.width

    def sectionLength(section: SectionId): Length =
      index.network.section(section).map(_.length).getOrElse(Meters(0))

    val headStart = if (v.s >= length) v.s - length else Meters(0)
    val headEntry = (v.section, headStart, v.s)
    val overhang = length - v.s

    def upstream(section: SectionId, remaining: Length, hop: Int): List[(SectionId, Length, Length)] =
      if (remaining <= Meters(0) || hop >= MaxHops) Nil
      else
        index.predecessors(section).flatMap { pred =>
          val predLength = sectionLength(pred)
          if (remaining <= predLength) List((pred, predLength - remaining, predLength))
          else (pred, Meters(0), predLength) :: upstream(pred, remaining - predLength, hop + 1)
        }

    if (overhang > Meters(0)) headEntry :: upstream(v.section, overhang, hop = 0) else List(headEntry)
  }

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
    * A vehicle whose rear has not cleared its head section is still found by
    * a follower in whatever section its tail is still sitting in: for every
    * vehicle in `traffic` other than `of`, [[occupiedSpan]]'s *last* entry -
    * the section its rear edge is actually in - is checked against the
    * section currently being searched, using that entry's near edge as the
    * position to measure to. Only the last entry is a candidate; the others
    * are sections a long body merely passes all the way through; a follower
    * standing inside one of those would already be overlapping the body,
    * which valid traffic never does. This is computed once per call rather
    * than per section searched, since it depends only on `traffic` and `of`,
    * not on how far the walk below has gotten.
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

    // Every other vehicle whose body's rear edge - occupiedSpan's last entry -
    // sits in a section other than its own, keyed by that section, with the
    // near-edge position already resolved. A vehicle with no overhang at all
    // never appears here: its rear is in its own head section, and the
    // ordinary `traffic.on(section)` lookup below already finds it there.
    val overhangBySection: Map[SectionId, List[(NetworkVehicle, Length)]] =
      traffic.all
        .filter(_.piloted.uuid != of.piloted.uuid)
        .flatMap { vehicle =>
          occupiedSpan(vehicle, index).lastOption.collect {
            case (section, nearEdge, _) if section != vehicle.section => section -> (vehicle, nearEdge)
          }
        }
        .groupBy(_._1)
        .view
        .mapValues(_.map(_._2))
        .toMap

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
      val fromS = searchFromS.getOrElse(Meters(0))

      val ownNearest = traffic
        .on(section)
        .filter(vehicle => vehicle.piloted.uuid != of.piloted.uuid && searchFromS.forall(vehicle.s > _))
        .sortBy(_.s.toMeters)
        .headOption
        .map(leader => (leader, leader.s - vehicleLength(leader)))

      val straddlingNearest = overhangBySection
        .getOrElse(section, Nil)
        .filter { case (_, nearEdge) => searchFromS.forall(nearEdge > _) }
        .sortBy(_._2.toMeters)
        .headOption

      val nearestAhead = (ownNearest.toList ++ straddlingNearest.toList).sortBy(_._2.toMeters).headOption

      nearestAhead match {
        case Some((leader, nearEdge)) =>
          val gap = travelled + (nearEdge - fromS)
          if (gap <= within) Some((leader, gap)) else None

        case None =>
          sectionLength(section) match {
            case None => None // A section the index doesn't know about is a dead end.
            case Some(length) =>
              val remainingInSection = length - fromS
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
