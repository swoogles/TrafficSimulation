package com.billding.network

import squants.motion.Distance
import squants.space.Meters
import squants.time.Frequency
import squants.{QuantityVector, Time}

import com.billding.physics.Spatial
import com.billding.traffic.{IntelligentDriverModelImpl, PilotedVehicle}

/**
  * A demand boundary at the start of `at`: Poisson arrivals (W6, `IMPLEMENTATION_TASKS.md`)
  * that either get admitted onto the network this tick, or wait.
  *
  * `queued`, `admitted` and `dropped` are exactly the plan's "virtual source
  * queue, with a documented bound and overflow policy"
  * (`creativity_plan.md`, "Capacity: four different quantities"): a car that
  * cannot be admitted is never simply forgotten. It is held in `queued` and
  * retried on a later tick; only once `queued` is already at the bound
  * [[Boundary.tick]] is given does a further arrival get counted in `dropped`
  * instead. Congestion backing up onto this boundary is a number you can
  * read, not demand that quietly evaporated at the seam.
  *
  * `seed` is the entire RNG state this source carries. [[Boundary.tick]]
  * consumes it and returns a new `Source` with a new `seed`, rather than
  * reading from a shared, mutable `scala.util.Random` - the state lives in
  * the case class, not in a hidden object identity, so replaying the same
  * starting `seed` through the same sequence of ticks reproduces the exact
  * same arrivals, independent of anything else running in the process.
  *
  * `destination` and `unserviceable` are optional and default to behaviour
  * unchanged from a plain admit-or-queue source. When a destination *is*
  * given, `creativity_plan.md`'s "Merges and splits: behavior, not just
  * connectors" is explicit: only admit a vehicle whose destination is
  * reachable at spawn, and report unserviceable demand separately rather
  * than spawning a car that can only strand. [[Routing.isReachable]] is
  * reused for that check rather than inventing a second one.
  */
final case class Source(
  at: SectionId,
  meanRate: Frequency,
  seed: Long,
  queued: Int = 0,
  admitted: Int = 0,
  dropped: Int = 0,
  destination: Option[SectionId] = None,
  unserviceable: Int = 0
)

/**
  * An explicit boundary that counts departures at `at`, distinct from the
  * implicit sink W5 already gives every dangling outgoing end.
  *
  * [[NetworkTick.advance]] already removes a vehicle from the network the
  * moment it drives off a section with no outgoing movement - that is what
  * makes the end implicit. A [[Sink]] does not change that removal; it only
  * attributes departures at one named section to a running count, the way a
  * detector at a highway's end would, via [[Boundary.arrive]].
  */
final case class Sink(at: SectionId, departed: Int = 0)

/**
  * Advances [[Source]]s and [[Sink]]s by one tick.
  *
  * Everything here is a pure function from a `Source`/`Sink` and the current
  * `NetworkTraffic`/`NetworkIndex` to the next one - no shared mutable state
  * anywhere, matching the rest of phase C/E.
  */
object Boundary {

  /**
    * How many blocked arrivals a [[Source]] holds before further arrivals are
    * dropped instead of queued.
    *
    * Ten: enough that a short-lived jam at the boundary (a few seconds of a
    * blocked gap, at the sort of arrival rates this backlog's fixtures use)
    * does not immediately start discarding demand, but small enough that a
    * genuinely blocked road produces a visible, bounded number rather than an
    * unbounded one - an unbounded queue would just move the "demand vanishes"
    * problem the plan warns about from the seam to the bound itself. Callers
    * that need a different bound can pass their own to [[tick]].
    */
  val DefaultQueueBound: Int = 10

  private val Idm = new IntelligentDriverModelImpl

  /** The linear-congruential recurrence `java.util.Random` uses internally, applied to a seed at rest instead of to a hidden mutable field. */
  private val Multiplier: Long = 0x5DEECE66DL
  private val Increment: Long = 0xBL
  private val Mask: Long = (1L << 48) - 1

  /**
    * One step of the generator: the next state, and a uniform draw in
    * `[0, 1)` derived from it.
    *
    * Carrying the state as a plain `Long` return value - rather than calling
    * `.nextDouble()` on a stored `scala.util.Random` - is what makes a
    * `Source`'s `seed` field the *entire* RNG state: two sources (or two runs
    * of the same source) started from the same seed and fed the same ticks
    * take the same sequence of states no matter what else in the process
    * asked some other `Random` for numbers in between.
    */
  private def nextUniform(seed: Long): (Double, Long) = {
    val advanced = (seed * Multiplier + Increment) & Mask
    (advanced.toDouble / (Mask.toDouble + 1.0), advanced)
  }

  /**
    * Whether a Poisson arrival happens in this tick, and the seed to carry
    * forward.
    *
    * The standard thinning approximation to a Poisson process (W6): with `dt`
    * small relative to `1 / meanRate`, drawing one uniform variate per tick
    * and comparing it against `meanRate * dt` gives an arrival count over many
    * ticks indistinguishable from a true Poisson process at that rate, without
    * needing an inverse-CDF sample of an exponential inter-arrival time.
    */
  private def arrival(meanRate: Frequency, dt: Time, seed: Long): (Boolean, Long) = {
    val (draw, advanced) = nextUniform(seed)
    val probability = math.min(1.0, math.max(0.0, meanRate.toHertz * dt.toSeconds))
    (draw < probability, advanced)
  }

  /**
    * A freshly-built vehicle, positioned at the very start of `source.at`,
    * entering at that section's speed limit (or its own desired speed if the
    * section isn't in `index` at all).
    *
    * Built fresh on every attempt rather than carried across queued ticks -
    * `Source.queued` is a count, not a list of waiting vehicles, so a car that
    * fails admission this tick and succeeds next tick is, as far as this
    * boundary's bookkeeping cares, indistinguishable from a new one. Only the
    * counts in `Source` are meant to be trusted across ticks.
    */
  private def enteringVehicle(source: Source, index: NetworkIndex): NetworkVehicle = {
    val spatial = Spatial.withVecs(QuantityVector[Distance](Meters(0), Meters(0), Meters(0)))
    val piloted = PilotedVehicle.commuter2(spatial, Idm, spatial)
    val entrySpeed = index.network.section(source.at).map(_.speedLimit).getOrElse(piloted.driver.desiredSpeed)
    NetworkVehicle.enteringAt(piloted, source.at, Meters(0), entrySpeed, index, destination = source.destination)
  }

  /**
    * Whether the start of `source.at` is clear enough to admit `candidate`.
    *
    * Reuses [[Lookahead.leaderOf]] rather than a second notion of gap, exactly
    * as the card asks: searching only as far as `candidate`'s own minimum
    * following distance is what turns that general lookahead into an
    * admission check specifically - a leader found inside that distance means
    * its rear has not yet cleared far enough along `source.at` for a car
    * entering at the very start to have safe following distance; `None` means
    * either nobody is that close or nobody is there at all.
    */
  private def hasSafeGap(traffic: NetworkTraffic, index: NetworkIndex, candidate: NetworkVehicle): Boolean =
    Lookahead.leaderOf(traffic, index, candidate, candidate.piloted.driver.minimumDistance).isEmpty

  /**
    * Advance `source` by one tick of `dt` against `traffic`.
    *
    * Order of operations: first, an unreachable destination makes admission
    * impossible regardless of gap, so that demand is counted as
    * `unserviceable` and nothing else about `source` changes (no RNG draw is
    * consumed - there is nothing this tick that a draw could decide).
    * Otherwise, a Poisson draw decides whether a new car arrives at the
    * boundary this tick; it joins `queued` if there is room under
    * `queueBound`, or is counted in `dropped` if there is not. Finally, if
    * anything is queued (this tick's arrival, or one still waiting from
    * before), a fresh candidate vehicle is tried against the current gap: a
    * safe gap admits it onto `traffic` and moves one car from `queued` to
    * `admitted`; no safe gap leaves it queued for the next tick.
    *
    * Returns the updated `Source`, the possibly-updated `NetworkTraffic`, and
    * the vehicle admitted this tick, if any.
    */
  def tick(
    source: Source,
    traffic: NetworkTraffic,
    index: NetworkIndex,
    dt: Time,
    queueBound: Int = DefaultQueueBound
  ): (Source, NetworkTraffic, Option[NetworkVehicle]) = {
    val unreachable = source.destination.exists(destination => !Routing.isReachable(index, source.at, destination))

    if (unreachable) {
      (source.copy(unserviceable = source.unserviceable + 1), traffic, None)
    } else {
      val (arrived, advancedSeed) = arrival(source.meanRate, dt, source.seed)

      val (queuedAfterArrival, droppedAfterArrival) =
        if (!arrived) (source.queued, source.dropped)
        else if (source.queued < queueBound) (source.queued + 1, source.dropped)
        else (source.queued, source.dropped + 1)

      if (queuedAfterArrival == 0) {
        (source.copy(seed = advancedSeed, dropped = droppedAfterArrival), traffic, None)
      } else {
        val candidate = enteringVehicle(source, index)

        if (hasSafeGap(traffic, index, candidate)) {
          val admittedTraffic = NetworkTraffic.place(traffic, candidate)
          val admittedSource = source.copy(
            seed = advancedSeed,
            queued = queuedAfterArrival - 1,
            admitted = source.admitted + 1,
            dropped = droppedAfterArrival
          )
          (admittedSource, admittedTraffic, Some(candidate))
        } else {
          val blockedSource =
            source.copy(seed = advancedSeed, queued = queuedAfterArrival, dropped = droppedAfterArrival)
          (blockedSource, traffic, None)
        }
      }
    }
  }

  /**
    * Attribute departures at `sink.at` to `sink`'s running count.
    *
    * `departures` is exactly [[NetworkTickResult.departures]] - vehicles that
    * fell off a dangling outgoing end this tick, still carrying the section
    * they fell off of in their own `.section` field. This never removes
    * anything from the network itself (W5's implicit sink already did that);
    * it only lets a caller who named a boundary `Sink` read how many of those
    * departures were its own.
    */
  def arrive(sink: Sink, departures: List[NetworkVehicle]): Sink =
    sink.copy(departed = sink.departed + departures.count(_.section == sink.at))
}
