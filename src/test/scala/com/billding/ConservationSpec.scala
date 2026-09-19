package com.billding

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import squants.space.Meters
import squants.time.{Hertz, Seconds}
import squants.Time

import com.billding.network._

/**
  * Card E4. [[TrafficAccounts]] is only worth what it catches, so this spec
  * exercises both directions: a real run that should balance on every single
  * tick, and a deliberately broken ledger that [[TrafficAccounts.balances]]
  * must reject.
  */
class ConservationSpec extends AnyFlatSpec with Matchers {

  private val dt: Time = Seconds(0.1)

  "a congested Y-split" should "balance on every tick of a 2000-tick run" in {
    val (_, index) = NetworkFixtures.ySplit(trunkLength = Meters(150), branchLength = Meters(80))

    val trunk = SectionId("trunk")
    val branchLeft = SectionId("branch-left")
    val branchRight = SectionId("branch-right")

    // A high arrival rate relative to how fast admitted cars can clear the
    // trunk's own start - the congestion the card asks for - split across
    // both branches so both sides of the Y actually carry traffic.
    val sourceLeft = Source(at = trunk, meanRate = Hertz(1.5), seed = 11L, destination = Some(branchLeft))
    val sourceRight = Source(at = trunk, meanRate = Hertz(1.5), seed = 17L, destination = Some(branchRight))
    val sinkLeft = Sink(at = branchLeft)
    val sinkRight = Sink(at = branchRight)

    val ticks = 2000

    val finalState = (1 to ticks).foldLeft(
      (sourceLeft, sourceRight, sinkLeft, sinkRight, NetworkTraffic.empty)
    ) {
      case ((sLeft, sRight, skLeft, skRight, traffic), tick) =>
        val (sLeftAfter, afterLeft, _) = Boundary.tick(sLeft, traffic, index, dt)
        val (sRightAfter, afterBoth, _) = Boundary.tick(sRight, afterLeft, index, dt)

        val result = NetworkTick.advance(afterBoth, index, dt)

        val skLeftAfter = Boundary.arrive(skLeft, result.departures)
        val skRightAfter = Boundary.arrive(skRight, result.departures)

        val accounts =
          TrafficAccounts.of(List(sLeftAfter, sRightAfter), List(skLeftAfter, skRightAfter), result.traffic)

        withClue(s"tick $tick: $accounts ") {
          accounts.balances shouldBe true
        }

        (sLeftAfter, sRightAfter, skLeftAfter, skRightAfter, result.traffic)
    }

    // Not a vacuous 2000 ticks of nothing happening: some demand actually
    // moved through the split.
    val (finalSourceLeft, finalSourceRight, finalSinkLeft, finalSinkRight, _) = finalState
    (finalSourceLeft.admitted + finalSourceRight.admitted) should be > 0
    (finalSinkLeft.departed + finalSinkRight.departed) should be > 0
    // The congestion the card asks for: demand queued or was dropped at the boundary.
    (finalSourceLeft.queued + finalSourceRight.queued + finalSourceLeft.dropped + finalSourceRight.dropped) should be > 0
  }

  "TrafficAccounts.balances" should "catch a deliberately doubled admission" in {
    val (_, index) = NetworkFixtures.straightChain(count = 3, each = Meters(300))
    val entry = SectionId("mainline-0")

    val source = Source(at = entry, meanRate = Hertz(0.5), seed = 5L)
    val sink = Sink(at = SectionId("mainline-2"))

    val (admittedSource, traffic, admittedVehicle) = {
      // Drive the source until it admits exactly one vehicle.
      def loop(current: Source, current0: NetworkTraffic): (Source, NetworkTraffic, NetworkVehicle) =
        Boundary.tick(current, current0, index, dt) match {
          case (next, nextTraffic, Some(vehicle)) => (next, nextTraffic, vehicle)
          case (next, nextTraffic, None)          => loop(next, nextTraffic)
        }
      loop(source, NetworkTraffic.empty)
    }

    admittedVehicle.section shouldBe entry

    // Ground truth: one admission, one active car, nobody departed. This must balance.
    TrafficAccounts.of(admittedSource, sink, traffic).balances shouldBe true

    // Corrupt the ledger the way a duplication bug would: the source claims
    // a second admission that never actually put a second car on the
    // network (`traffic` is untouched). `admitted` no longer matches
    // `active + departed + removedByEdit`, and `balances` must say so.
    val doubledSource = admittedSource.copy(admitted = admittedSource.admitted + 1)

    TrafficAccounts.of(doubledSource, sink, traffic).balances shouldBe false
  }

  "TrafficAccounts" should "reject a hand-built ledger whose requested total doesn't match admitted + queued + dropped" in {
    val brokenBySecondIdentity = TrafficAccounts(
      requested = 100,
      admitted = 10,
      active = 10,
      departed = 0,
      removedByEdit = 0,
      dropped = 0,
      queued = 0
    )

    brokenBySecondIdentity.balances shouldBe false
  }

  "TrafficAccounts.zero" should "balance trivially" in {
    TrafficAccounts.zero.balances shouldBe true
  }

  "a vehicle marked removedByEdit" should "count toward admitted without needing to touch departed" in {
    val source = Source(at = SectionId("mainline-0"), meanRate = Hertz(0.0), seed = 1L, admitted = 1)
    val sink = Sink(at = SectionId("mainline-2"), departed = 0)

    // One admitted car, gone from `traffic` not because it completed a trip
    // (that would be `sink.departed`) but because a geometry edit removed
    // it (card J2). `removedByEdit` alone - untouched `departed` - is enough
    // to balance: card J2's edit-migration code has no reason to reach into
    // `Sink` at all, which is exactly the separation the plan insists on.
    sink.departed shouldBe 0
    TrafficAccounts.of(source, sink, NetworkTraffic.empty, removedByEdit = 1).balances shouldBe true
  }
}
