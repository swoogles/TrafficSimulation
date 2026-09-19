package com.billding.network

import squants.space.Length

/**
  * Which side of `section` a [[LaneNeighbour]] sits on.
  *
  * Sections are one-way (working default W4), so "left" and "right" are enough
  * to say which of a section's edges a neighbour touches - there is no need for
  * a direction-of-travel-relative "opposing" side the way a two-way road would
  * need.
  */
sealed trait Side
object Side {
  case object LeftOf extends Side
  case object RightOf extends Side
}

/**
  * Where, along `section`, a neighbouring lane can be reached - and how to
  * translate a position on one into a position on the other.
  *
  * This is the piece `TrackRoad.transpose` cannot express: that method maps a
  * position by normalized whole-lane progress, which silently assumes the two
  * lanes span the same stretch of road. That assumption breaks the moment the
  * lanes are different lengths, which is exactly what an acceleration lane is -
  * a ramp is adjacent to the mainline only over its acceleration region, a
  * window shorter than either section.
  *
  * `fromS`/`toS` is the interval on `section` (inclusive) over which `neighbour`
  * is actually alongside it. `offset` maps a position on `section` to the
  * corresponding position on `neighbour`: `neighbourS = s + offset`. Outside the
  * interval there is no correspondence to report at all - not an extrapolated
  * guess, [[None]].
  */
final case class LaneNeighbour(
  section: SectionId,
  neighbour: SectionId,
  side: Side,
  fromS: Length,
  toS: Length,
  offset: Length
) {

  require(toS >= fromS, s"a neighbour interval cannot run backwards: $fromS to $toS")

  /**
    * Where `s` on `section` lands on `neighbour`, or [[None]] when `s` falls
    * outside the stretch this neighbour relation covers.
    *
    * The network is expected to carry the mirror-image [[LaneNeighbour]] from
    * `neighbour` back to `section` (same window, negated offset) so that
    * mapping there and back through the two of them is the identity for every
    * `s` this method accepts.
    */
  def neighbourPosition(s: Length): Option[Length] =
    if (s < fromS || s > toS) None
    else Some(s + offset)
}

/**
  * The lane graph: sections, the movements that connect them end to end, and
  * the lane-neighbour intervals that connect them side by side.
  *
  * Deliberately dumb. Every accessor here is a linear scan; `NetworkIndex`
  * (phase B3) is where the precomputed adjacency that makes lookups fast on a
  * real-sized network lives. Keeping this container obvious is what makes it
  * trustworthy as the thing the index is checked against.
  */
final case class RoadNetwork(
  sections: Map[SectionId, LaneSection],
  movements: List[Movement],
  laneNeighbours: List[LaneNeighbour] = Nil
) {

  def section(id: SectionId): Option[LaneSection] = sections.get(id)

  /** Movements leading out of `id`, in the order they were declared. */
  def movementsFrom(id: SectionId): List[Movement] = movements.filter(_.from == id)

  /** Movements leading into `id`, in the order they were declared. */
  def movementsInto(id: SectionId): List[Movement] = movements.filter(_.to == id)

  /** Lane-neighbour relations declared with `id` as the section side. */
  def neighboursOf(id: SectionId): List[LaneNeighbour] = laneNeighbours.filter(_.section == id)
}
