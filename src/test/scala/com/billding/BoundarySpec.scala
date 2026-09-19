package com.billding

import java.util.UUID

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import squants.motion.{Distance, MetersPerSecond}
import squants.space.Meters
import squants.time.{Hertz, Seconds}
import squants.{QuantityVector, Time}

import com.billding.network._
import com.billding.physics.Spatial
import com.billding.traffic.{IntelligentDriverModelImpl, PilotedVehicle}

/**
  * Card E1. Fixtures built by hand here rather than reused from
  * `NetworkFixtures`'s helpers directly for the blocked-road case, which
  * needs a hand-placed stalled vehicle sitting at a known `s` - the same
  * reasoning `NetworkTickSpec` gives for building its own fixtures instead of
  * sharing one file: this checkout has other cards landing in the same
  * network files at the same time.
  */
class BoundarySpec extends AnyFlatSpec with Matchers {

  private val idm = new IntelligentDriverModelImpl
  private val dt: Time = Seconds(0.1)

  /** Same construction `NetworkTickSpec`/`LookaheadSpec` use for a parked test car. */
  private def commuterAt(s: squants.space.Length): PilotedVehicle = {
    val spatial = Spatial.withVecs(QuantityVector[Distance](s, Meters(0), Meters(0)))
    PilotedVehicle.commuter2(spatial, idm, spatial)
  }

  private def stoppedVehicle(section: SectionId, s: squants.space.Length): NetworkVehicle =
    NetworkVehicle(commuterAt(s), section, s, MetersPerSecond(0))

  /** Drive `source` for `ticks` steps against `traffic`, running the network's own physics between admissions so admitted cars clear the boundary instead of sitting on it forever. */
  private def runWithPhysics(
    source: Source,
    traffic: NetworkTraffic,
    index: NetworkIndex,
    ticks: Int
  ): (Source, NetworkTraffic) =
    (1 to ticks).foldLeft((source, traffic)) {
      case ((currentSource, currentTraffic), _) =>
        val (nextSource, afterAdmission, _) = Boundary.tick(currentSource, currentTraffic, index, dt)
        val afterPhysics = NetworkTick.advance(afterAdmission, index, dt).traffic
        (nextSource, afterPhysics)
    }

  /** Drive `source` for `ticks` steps against a `traffic` that never changes on its own (the blocked-road case, where nothing ever moves). */
  private def runStatic(source: Source, traffic: NetworkTraffic, index: NetworkIndex, ticks: Int): Source =
    (1 to ticks)
      .foldLeft((source, traffic)) {
        case ((currentSource, currentTraffic), _) =>
          val (nextSource, nextTraffic, _) = Boundary.tick(currentSource, currentTraffic, index, dt)
          (nextSource, nextTraffic)
      }
      ._1

  "Boundary.tick" should "admit close to the expected Poisson count over 1000 ticks on a clear road" in {
    val (_, index) = NetworkFixtures.straightChain(count = 6, each = Meters(300))
    val meanRate = Hertz(0.3)
    val source = Source(at = SectionId("mainline-0"), meanRate = meanRate, seed = 42L)

    val ticks = 1000
    val (finalSource, _) = runWithPhysics(source, NetworkTraffic.empty, index, ticks)

    val expected = meanRate.toHertz * ticks * dt.toSeconds // 30 arrivals expected over 100 simulated seconds
    finalSource.admitted.toDouble should be(expected +- (expected * 0.5))
    finalSource.dropped shouldBe 0
    finalSource.unserviceable shouldBe 0
  }

  it should "admit none, queue to the bound and count drops past it on a blocked road" in {
    val (_, index) = NetworkFixtures.straightChain(count = 1, each = Meters(500))
    val entry = SectionId("mainline-0")

    // A stalled car one metre in - well inside any entering car's minimum
    // following distance (6 m for a commuter driver), so the gap check never
    // passes and the road never clears, since nothing here ever calls
    // `NetworkTick.advance`.
    val blocker = stoppedVehicle(entry, Meters(1))
    val traffic = NetworkTraffic.of(List(blocker))

    // A high rate relative to the bound - over 1000 ticks at 0.1 s each
    // (100 simulated seconds) a mean rate of 2 Hz expects roughly 200
    // arrivals, comfortably enough to fill a 10-car bound and then some.
    val source = Source(at = entry, meanRate = Hertz(2.0), seed = 7L)

    val finalSource = runStatic(source, traffic, index, ticks = 1000)

    finalSource.admitted shouldBe 0
    finalSource.queued shouldBe Boundary.DefaultQueueBound
    finalSource.dropped should be > 0
  }

  it should "replay identically when the same seed is fed the same sequence of ticks" in {
    val (_, index) = NetworkFixtures.straightChain(count = 6, each = Meters(300))
    val meanRate = Hertz(0.4)
    val seed = 123456789L
    val entry = SectionId("mainline-0")

    val firstRun = runWithPhysics(Source(entry, meanRate, seed), NetworkTraffic.empty, index, ticks = 500)._1
    val secondRun = runWithPhysics(Source(entry, meanRate, seed), NetworkTraffic.empty, index, ticks = 500)._1

    firstRun shouldBe secondRun
    // Not a vacuous check: some arrivals, some admissions, actually happened.
    firstRun.admitted should be > 0
  }

  it should "hold demand for an unreachable destination as unserviceable rather than admitting cars that would strand" in {
    val (_, index) = NetworkFixtures.straightChain(count = 3, each = Meters(300))
    val entry = SectionId("mainline-0")
    val nowhere = SectionId("does-not-exist")

    val source = Source(at = entry, meanRate = Hertz(5.0), seed = 99L, destination = Some(nowhere))

    val (finalSource, finalTraffic) = runWithPhysics(source, NetworkTraffic.empty, index, ticks = 200)

    finalSource.admitted shouldBe 0
    finalSource.queued shouldBe 0
    finalSource.dropped shouldBe 0
    finalSource.unserviceable should be > 0
    finalTraffic.all shouldBe empty
  }

  "Boundary.arrive" should "count only the departures that fell off its own section" in {
    val piloted = commuterAt(Meters(0))
    val here = SectionId("here")
    val elsewhere = SectionId("elsewhere")
    val departures = List(
      NetworkVehicle(piloted.copy(uuid = UUID.randomUUID()), here, Meters(10), MetersPerSecond(0)),
      NetworkVehicle(piloted.copy(uuid = UUID.randomUUID()), elsewhere, Meters(10), MetersPerSecond(0))
    )

    val sink = Boundary.arrive(Sink(at = here), departures)

    sink.departed shouldBe 1
  }
}
