package com.billding.network

import squants.Time
import squants.time.Seconds

import scala.annotation.tailrec
import scala.collection.mutable

/**
  * Route planning over a [[NetworkIndex]], weighted by free-flow travel time -
  * a section's length divided by its speed limit.
  *
  * This is the routing baseline the plan asks for (creativity_plan.md, "Merges
  * and splits: behavior, not just connectors"): routes come from physical
  * travel-time estimates over permitted lane movements, not from current
  * congestion. Congestion-aware rerouting is deliberately out of scope here -
  * the plan defers it until there is a stabilization threshold, so drivers do
  * not oscillate between nearly-equal routes.
  *
  * Dijkstra runs over sections as nodes and movements as edges. The weight of
  * taking any movement leaving a section is that section's own free-flow
  * travel time - every movement leaving the same section costs the same to
  * *leave*, so what actually distinguishes two routes is what they cost
  * further on. A movement itself is treated as a zero-cost seam, matching the
  * rest of the network model (B2): an ordinary continuation never adds a
  * stop, so it never adds a cost by itself either.
  */
object Routing {

  /** One entry in the search frontier: a section and the best travel time found to reach it so far. */
  private final case class Frontier(section: SectionId, distance: Time)

  /**
    * Orders frontier entries by distance first, then by section id - so that
    * when two sections are discovered with the same running time (a Y-split's
    * two branches leaving the same trunk, for instance), which one the search
    * settles first is decided by name, not by whatever order a `Map` happens
    * to iterate in. That determinism is what lets a later card replay the same
    * seed and get the same route every time.
    */
  private val frontierOrdering: Ordering[Frontier] =
    Ordering.by[Frontier, (Double, String)](f => (f.distance.toSeconds, f.section.value)).reverse

  /**
    * The cheapest sequence of movements from `from` to `to`, or `None` when no
    * such sequence exists. An empty list means `from` and `to` are the same
    * section - already there, nothing to traverse.
    */
  def planRoute(index: NetworkIndex, from: SectionId, to: SectionId): Option[List[MovementId]] = {
    val network = index.network

    if (network.section(from).isEmpty || network.section(to).isEmpty) None
    else if (from == to) Some(Nil)
    else {
      val best = mutable.Map[SectionId, Time](from -> Seconds(0))
      val via = mutable.Map[SectionId, MovementId]()
      val predecessor = mutable.Map[SectionId, SectionId]()
      val settled = mutable.Set[SectionId]()

      val queue = mutable.PriorityQueue(Frontier(from, Seconds(0)))(frontierOrdering)

      while (queue.nonEmpty) {
        val current = queue.dequeue()
        if (!settled.contains(current.section)) {
          settled += current.section

          // network.section(current.section) is guaranteed present: every
          // section that reaches the queue is either `from` (checked above)
          // or the `to` of some movement in this network's own section map.
          network.section(current.section).foreach { currentSection =>
            val travelTime = currentSection.length / currentSection.speedLimit

            index.outgoing.getOrElse(current.section, Nil).foreach { movement =>
              val candidate = current.distance + travelTime
              val improves = best.get(movement.to).forall(candidate < _)
              if (improves) {
                best(movement.to) = candidate
                via(movement.to) = movement.id
                predecessor(movement.to) = current.section
                queue.enqueue(Frontier(movement.to, candidate))
              }
            }
          }
        }
      }

      if (!settled.contains(to)) None
      else {
        @tailrec
        def reconstruct(section: SectionId, acc: List[MovementId]): List[MovementId] =
          predecessor.get(section) match {
            case Some(previous) => reconstruct(previous, via(section) :: acc)
            case None => acc
          }

        Some(reconstruct(to, Nil))
      }
    }
  }

  /** Whether any sequence of movements reaches `to` from `from` at all. */
  def isReachable(index: NetworkIndex, from: SectionId, to: SectionId): Boolean =
    planRoute(index, from, to).isDefined
}
