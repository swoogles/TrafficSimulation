package com.billding.network

/**
  * The whole-network ledger card E4 asks for: one place a running simulation
  * (or a test driving one by hand) can ask "does every vehicle this network
  * has ever touched still add up?"
  *
  * `creativity_plan.md`'s "Live edits while traffic keeps running" section is
  * explicit about two things this exists to catch: removals from a geometry
  * edit must never be folded into completed trips, and every vehicle must be
  * accounted for exactly once. Those are exactly the two failure modes
  * [[balances]] checks for - a vehicle counted twice (duplication) and a
  * vehicle that stops appearing anywhere (quiet disappearance) - because
  * neither one is easy to see by watching the screen, but both break one of
  * these two identities immediately:
  *
  *   - `admitted == active + departed + removedByEdit` - every vehicle this
  *     network has ever let onto the road is, right now, exactly one of:
  *     still driving (`active`), gone because it finished a trip
  *     (`departed`), or gone because a live geometry edit removed it
  *     (`removedByEdit`). This is the identity that actually exercises
  *     independent bookkeeping against each other: `admitted` comes from
  *     [[Source]]'s own running counts, `active` from what [[NetworkTraffic]]
  *     actually holds, `departed` from [[Sink]]'s own running counts - three
  *     structures that never share state, so this equation only holds if all
  *     three agree.
  *   - `requested == admitted + queued + dropped` - every arrival a
  *     [[Source]] has ever drawn is, right now, exactly one of: already let
  *     through (`admitted`), still waiting at the boundary (`queued`), or
  *     turned away for having queued past the bound (`dropped`). Card E1
  *     built [[Source]] so this is true of a single source by construction -
  *     see [[of]] - which makes this identity a guard against a
  *     [[TrafficAccounts]] built (or edited) by hand with numbers that don't
  *     actually describe a real source, rather than something a correct
  *     [[of]] call could ever fail.
  *
  * `removedByEdit` stays `0` until card J2 gives live geometry edits
  * somewhere to report a removal from. It must never be added to `departed`
  * instead - the plan is explicit that a car deleted out from under it by a
  * geometry edit did not complete a trip, and conflating the two would hide
  * exactly the number this ledger exists to keep visible.
  *
  * `unserviceable` demand (`Source.unserviceable` - an arrival never even
  * drawn because the destination wasn't reachable at spawn) deliberately
  * plays no part in either identity above, matching the card's own formula
  * literally: that demand never became a `requested` arrival at the boundary
  * in the first place, so it has nothing here to balance against. It stays
  * visible as `Source.unserviceable` itself.
  */
final case class TrafficAccounts(
  requested: Int,
  admitted: Int,
  active: Int,
  departed: Int,
  removedByEdit: Int,
  dropped: Int,
  queued: Int
) {

  /**
    * Whether every vehicle this ledger describes is accounted for exactly
    * once. `false` means either a duplication (something counted as
    * `admitted` more than once for the same car) or a quiet disappearance
    * (a car that is neither `active`, `departed` nor `removedByEdit`) has
    * happened somewhere upstream - see the two identities documented on
    * [[TrafficAccounts]] itself.
    */
  def balances: Boolean =
    admitted == active + departed + removedByEdit &&
      requested == admitted + queued + dropped
}

object TrafficAccounts {

  /** Nothing has happened yet - the ledger a simulation starts from. */
  val zero: TrafficAccounts = TrafficAccounts(
    requested = 0,
    admitted = 0,
    active = 0,
    departed = 0,
    removedByEdit = 0,
    dropped = 0,
    queued = 0
  )

  /**
    * Read the whole-network ledger straight off live simulation state - the
    * one line later cards can copy into any test that already has these
    * three things in scope:
    *
    *     TrafficAccounts.of(sources, sinks, traffic).balances shouldBe true
    *
    * `admitted`, `queued` and `dropped` are summed directly from every
    * `Source`'s own running counts, and `requested` is derived from that
    * same sum (`admitted + queued + dropped`) rather than tracked
    * separately - by the invariant [[Boundary.tick]] already keeps for a
    * single `Source` (every arrival it has ever drawn becomes admitted,
    * queued or dropped, and nothing else), that derivation is exactly what
    * "every arrival this source has drawn" means, for one source or for a
    * sum over many. `departed` is summed from every `Sink`'s own running
    * count, and `active` is read from `traffic` itself - `traffic.all.size`,
    * the number of vehicles the network actually holds right now, independent
    * of anything any `Source` or `Sink` believes.
    *
    * `removedByEdit` defaults to `0` (no card before J2 produces one); pass
    * the running total explicitly once J2 exists.
    */
  def of(sources: List[Source], sinks: List[Sink], traffic: NetworkTraffic, removedByEdit: Int = 0): TrafficAccounts = {
    val admitted = sources.map(_.admitted).sum
    val queued = sources.map(_.queued).sum
    val dropped = sources.map(_.dropped).sum
    val departed = sinks.map(_.departed).sum

    TrafficAccounts(
      requested = admitted + queued + dropped,
      admitted = admitted,
      active = traffic.all.size,
      departed = departed,
      removedByEdit = removedByEdit,
      dropped = dropped,
      queued = queued
    )
  }

  /** [[of]] for the common single-source, single-sink case. */
  def of(source: Source, sink: Sink, traffic: NetworkTraffic, removedByEdit: Int): TrafficAccounts =
    of(List(source), List(sink), traffic, removedByEdit)

  /** [[of]] for the common single-source, single-sink case, with no edits yet to report. */
  def of(source: Source, sink: Sink, traffic: NetworkTraffic): TrafficAccounts =
    of(List(source), List(sink), traffic, removedByEdit = 0)
}
