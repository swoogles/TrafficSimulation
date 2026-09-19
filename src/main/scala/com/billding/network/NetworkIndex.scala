package com.billding.network

/**
  * Precomputed adjacency over a [[RoadNetwork]], built once per network
  * version and carried alongside it.
  *
  * `RoadNetwork.movementsFrom`/`movementsInto` are deliberately linear scans
  * over `movements` - fine for a handful of sections in a test, but card C2's
  * leader lookup walks this on every vehicle on every tick. A linear scan
  * there would make the tick quadratic in network size, so `outgoing` and
  * `incoming` are grouped eagerly here instead of recomputed on demand.
  *
  * Declared order within each section's bucket is preserved: a Y-split's two
  * branches come back in the order the network declared them, because later
  * cards depend on that determinism. A section with no outgoing (or incoming)
  * movement simply has no entry in the map, so lookups fall back to `Nil`
  * rather than throwing.
  */
final case class NetworkIndex(network: RoadNetwork) {

  val outgoing: Map[SectionId, List[Movement]] =
    network.movements.groupBy(_.from)

  val incoming: Map[SectionId, List[Movement]] =
    network.movements.groupBy(_.to)

  /** Sections reachable directly from `id`, in declared order. */
  def successors(id: SectionId): List[SectionId] =
    outgoing.getOrElse(id, Nil).map(_.to)

  /** Sections that lead directly into `id`, in declared order. */
  def predecessors(id: SectionId): List[SectionId] =
    incoming.getOrElse(id, Nil).map(_.from)
}
