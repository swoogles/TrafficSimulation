package com.billding.network

import squants.QuantityVector
import squants.motion.Distance
import squants.space.{Length, Meters}

/**
  * A place where two [[Movement]]s' traversable curves come close enough to count as
  * crossing, expressed as a position along each movement's own curve independently -
  * `sA` is how far into `a` the crossing sits, `sB` how far into `b`.
  *
  * A [[ConflictPoint]] says nothing about right-of-way; it only says these two
  * movements cannot both occupy this patch of road at once. `H2` is where
  * `control` (stop/yield/uncontrolled) turns a conflict into an admission rule.
  */
final case class ConflictPoint(a: MovementId, b: MovementId, sA: Length, sB: Length)

/**
  * Finds where movements through the same junction physically cross.
  *
  * The plan is explicit that this is a property of a *pair of movements*, not of a
  * section: two movements conflict because their driven paths cross in space, which
  * is a fact about geometry, not about whether they happen to share an endpoint in
  * the graph. That is also why endpoint-sharing pairs are skipped below rather than
  * flagged - a shared `from` is an ordinary diverge (two movements steering apart
  * from the same lane) and a shared `to` is an ordinary merge (two movements joining
  * the same lane); both are endpoint connectivity, which the plan calls out as a
  * separate concern from crossing conflicts, and both would otherwise register as a
  * trivial zero-distance "conflict" right at the shared node - not a real crossing,
  * just the two curves touching where the graph already says they touch.
  *
  * This runs at edit time over a junction's handful of movements, not per tick over
  * a whole network, so sampling both curves and taking the nearest approach is the
  * right tool; an analytic curve-intersection (line/line, line/arc, arc/arc, each
  * with its own special cases) would be more code for a check that only has to be
  * fast enough to run when a network is edited.
  */
object Conflicts {

  /**
    * Distance along a curve between consecutive samples.
    *
    * Junction geometry here is built from arcs with radii and straights with
    * lengths on the order of metres to tens of metres, and lane width is 3.5 m
    * (`NetworkFixtures.laneWidth`) - so 0.25 m is small relative to every feature
    * of the geometry (about 14 samples per lane width) while still keeping the
    * sample count for a junction's handful of movements in the hundreds, not
    * thousands: cheap enough to run on every edit. See [[CrossingThreshold]] for
    * how this resolution and the threshold work together.
    */
  val SamplingStep: Length = Meters(0.25)

  /**
    * How close two sampled points must land to count as the same place.
    *
    * Two independently-sampled curves that truly cross can miss the exact
    * crossing point by up to roughly half a [[SamplingStep]] on each curve, so
    * the nearest sampled pair can sit up to roughly one `SamplingStep` away from
    * the true intersection even though the curves do cross. Half a metre is a
    * comfortable multiple of that (twice the 0.25 m step) so a real crossing is
    * never missed for want of tolerance, while staying well under a lane width
    * (3.5 m) so two curves that merely run alongside each other - adjacent
    * lanes, or turning arcs that pass close by without actually overlapping
    * pavement - are never mistaken for a crossing.
    */
  val CrossingThreshold: Length = Meters(0.5)

  /**
    * Every conflict among the movements named in `junction`.
    *
    * Movements are considered in the order `net.movements` declares them (the
    * ordering the network itself was built with), and pairs are then formed in
    * that same order, so the result list's order is a deterministic function of
    * the network - not of `junction`'s (unordered) iteration order. Later cards
    * that resolve ties by a stable order depend on that.
    *
    * A movement named in `junction` whose `from` or `to` section is missing from
    * `net.sections` contributes no conflicts: there is no geometry to sample, and
    * that is a different problem (`NetworkValidation` territory), not this one's
    * to report.
    */
  def conflicts(net: RoadNetwork, junction: Set[MovementId]): List[ConflictPoint] = {
    val ordered: List[Movement] = net.movements.filter(m => junction.contains(m.id))

    val pairs: List[(Movement, Movement)] = for {
      (a, index) <- ordered.zipWithIndex
      b <- ordered.drop(index + 1)
    } yield (a, b)

    pairs.flatMap { case (a, b) => conflictBetween(net, a, b) }
  }

  /** A movement's driven path: `from`'s geometry followed by `to`'s, as one continuous curve. */
  private final case class Curve(totalLength: Length, at: Length => QuantityVector[Distance])

  private def curveOf(net: RoadNetwork, movement: Movement): Option[Curve] =
    for {
      from <- net.section(movement.from)
      to <- net.section(movement.to)
    } yield {
      val fromLength = from.path.totalLength
      val total = fromLength + to.path.totalLength
      Curve(
        total,
        s => if (s <= fromLength) from.path.pointAt(s) else to.path.pointAt(s - fromLength)
      )
    }

  /** Evenly spaced sample positions from 0 to `curve.totalLength`, inclusive of both ends. */
  private def samplePositions(curve: Curve): List[Length] =
    if (curve.totalLength <= Meters(0)) List(Meters(0))
    else {
      val stepCount = math.ceil(curve.totalLength / SamplingStep).toInt
      (0 to stepCount).toList.map { i =>
        val s = SamplingStep * i.toDouble
        if (s > curve.totalLength) curve.totalLength else s
      }
    }

  private def conflictBetween(net: RoadNetwork, a: Movement, b: Movement): Option[ConflictPoint] =
    // A shared `from` is a diverge and a shared `to` is a merge - both are endpoint
    // connectivity, not a crossing, and both would otherwise show up as a spurious
    // zero-distance match right at the node they already share. See the class doc.
    if (a.from == b.from || a.to == b.to) None
    else
      for {
        curveA <- curveOf(net, a)
        curveB <- curveOf(net, b)
        (sA, sB) <- nearestApproach(curveA, curveB)
      } yield ConflictPoint(a.id, b.id, sA, sB)

  private def nearestApproach(a: Curve, b: Curve): Option[(Length, Length)] = {
    val candidates: List[(Length, Length, Length)] = for {
      sA <- samplePositions(a)
      sB <- samplePositions(b)
      distance = (a.at(sA) - b.at(sB)).magnitude
    } yield (distance, sA, sB)

    candidates.minByOption(_._1).collect {
      case (distance, sA, sB) if distance <= CrossingThreshold => (sA, sB)
    }
  }
}
