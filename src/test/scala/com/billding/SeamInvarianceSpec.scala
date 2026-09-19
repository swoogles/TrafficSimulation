package com.billding

import java.util.UUID

import squants.motion.{Distance, MetersPerSecond}
import squants.space.{Length, Meters}
import squants.time.Seconds
import squants.{QuantityVector, Time, Velocity}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import com.billding.network._
import com.billding.physics.Spatial
import com.billding.traffic.{IntelligentDriverModelImpl, PilotedVehicle}

/**
  * Phase C's own gate (creativity_plan.md's checklist, section 2; `IMPLEMENTATION_TASKS.md`
  * card C5): splitting one physical road into more pieces must not materially change its
  * traffic behaviour, and every vehicle advances exactly once per tick.
  *
  * The test built to prove it: run identical traffic, from identical initial positions and
  * speeds, on [[NetworkFixtures.straightChain]] built once as a single 600 m section and
  * once as six 100 m sections, then compare where every vehicle ends up 200 ticks later.
  *
  * `s` is deliberately not what gets compared - it resets to a small number every time a
  * vehicle crosses into a new section on the six-piece chain, while it counts straight up to
  * 600 on the one-piece chain, so the two are incomparable directly. What has to agree is
  * *cumulative distance travelled*, and this spec gets that without re-summing every tick's
  * motion: every vehicle in these fixtures only ever moves forward (the IDM integration in
  * `NetworkTick` never reverses a car), so a vehicle's absolute position along the chain -
  * its section's offset from the chain's start, plus its own `s` - is already a running
  * total, and cumulative distance is just that position's net change from start to finish.
  *
  * "Same seed" per the card is trivial here: nothing in this spec draws from an RNG.
  * Vehicles are hand-placed at fixed positions and speeds, and the same `PilotedVehicle`
  * (same uuid, same driver/vehicle parameters) is reused for both chain shapes, so "same
  * seed" reduces to "same starting state," which construction guarantees directly.
  */
class SeamInvarianceSpec extends AnyFlatSpec with Matchers {

  private val idm = new IntelligentDriverModelImpl
  private val dt: Time = Seconds(0.1) // W8's fixed-timestep working default.
  private val ticks = 200
  private val toleranceMeters = 0.001 // one millimetre - the card's own bar.

  /** Same construction `NetworkTickSpec`/`LookaheadSpec` use for a test car. The position fed in is never read by tick physics - only `piloted.driver`/`piloted.vehicle`/`piloted.uuid` are. */
  private def commuterAt(marker: Length): PilotedVehicle = {
    val spatial = Spatial.withVecs(QuantityVector[Distance](marker, Meters(0), Meters(0)))
    PilotedVehicle.commuter2(spatial, idm, spatial)
  }

  /**
    * One vehicle's identity plus its starting state, expressed as a route position -
    * distance from the very start of the chain, independent of how many sections the chain
    * is cut into - so the same spec places it identically on both chain shapes.
    */
  private final case class VehicleSpec(piloted: PilotedVehicle, startAt: Length, startSpeed: Velocity)

  /**
    * Five vehicles in a queue, 25 m apart, starting below both chains' 80 km/h (22.2 m/s)
    * speed limit so they spend the run accelerating (and, in the braking scenario, reacting
    * to the wave) rather than idling at a constant cruise - the case most likely to expose a
    * seam-dependent bug. 100 m of queue plus roughly 20 s of travel at up to the speed limit
    * comfortably stays inside the chain's 600 m before either scenario's vehicles would reach
    * the dangling end.
    */
  private def vehicleSpecs(startSpeed: Velocity): List[VehicleSpec] =
    List(Meters(0), Meters(25), Meters(50), Meters(75), Meters(100)).zipWithIndex.map {
      case (routePosition, i) => VehicleSpec(commuterAt(Meters(i.toDouble)), routePosition, startSpeed)
    }

  /**
    * Every section's own distance from the start of the chain, found by walking
    * `successors` forward from the one section with no predecessor. Sound for any
    * `straightChain` - one piece or six - since it never branches: every section but the
    * last has exactly one successor.
    */
  private def routeOffsets(network: RoadNetwork, index: NetworkIndex): Map[SectionId, Length] = {
    val root = network.sections.keySet.find(id => index.predecessors(id).isEmpty).get

    @scala.annotation.tailrec
    def walk(id: SectionId, offset: Length, acc: Map[SectionId, Length]): Map[SectionId, Length] = {
      val updated = acc + (id -> offset)
      index.successors(id) match {
        case next :: Nil => walk(next, offset + network.sections(id).length, updated)
        case _           => updated
      }
    }

    walk(root, Meters(0), Map.empty)
  }

  private def routePositionOf(vehicle: NetworkVehicle, offsets: Map[SectionId, Length]): Length =
    offsets(vehicle.section) + vehicle.s

  /**
    * Places `spec` at `spec.startAt` meters into the chain: finds the section whose offset
    * window contains that distance and hands `enteringAt` the leftover local `s`. Neither
    * chain shape gives these vehicles a route or a destination, so `enteringAt` resolves
    * `next` to the same placeholder (the section's first declared outgoing movement) on
    * both - the only continuation a `straightChain` ever declares anyway.
    */
  private def place(spec: VehicleSpec, index: NetworkIndex, offsets: Map[SectionId, Length]): NetworkVehicle = {
    val (section, sectionOffset) = offsets.toList.filter(_._2 <= spec.startAt).maxBy(_._2.toMeters)
    NetworkVehicle.enteringAt(spec.piloted, section, spec.startAt - sectionOffset, spec.startSpeed, index)
  }

  private def initialTraffic(specs: List[VehicleSpec], index: NetworkIndex): NetworkTraffic = {
    val offsets = routeOffsets(index.network, index)
    NetworkTraffic.of(specs.map(place(_, index, offsets)))
  }

  private def withSpeed(traffic: NetworkTraffic, uuid: UUID, speed: Velocity): NetworkTraffic = {
    val vehicle = traffic.vehicleWith(uuid).get
    NetworkTraffic.place(traffic, vehicle.copy(speed = speed))
  }

  /**
    * Runs `ticks` steps of [[NetworkTick.advance]], applying `perturb` (if it is defined at
    * that tick number) to the traffic immediately before that tick runs. Fails loudly if
    * anyone departs the network - neither scenario below is meant to produce one, and a
    * departure would need position bookkeeping (an overshot `s` past the chain's nominal
    * end) this spec doesn't otherwise do.
    */
  private def run(
    index: NetworkIndex,
    initial: NetworkTraffic,
    perturb: PartialFunction[Int, NetworkTraffic => NetworkTraffic] = PartialFunction.empty
  ): NetworkTraffic =
    (1 to ticks).foldLeft(initial) {
      case (traffic, tick) =>
        val input = if (perturb.isDefinedAt(tick)) perturb(tick)(traffic) else traffic
        val result = NetworkTick.advance(input, index, dt)
        withClue(s"tick $tick: a vehicle departed the network unexpectedly - ") {
          result.departures shouldBe empty
        }
        result.traffic
    }

  private def finalPositions(traffic: NetworkTraffic, index: NetworkIndex): Map[UUID, Length] = {
    val offsets = routeOffsets(index.network, index)
    traffic.all.map(v => v.piloted.uuid -> routePositionOf(v, offsets)).toMap
  }

  private def assertAgreement(
    onePiece: Map[UUID, Length],
    sixPiece: Map[UUID, Length],
    startPositions: Map[UUID, Length]
  ): Unit = {
    onePiece.keySet shouldBe sixPiece.keySet
    onePiece.keySet.foreach { uuid =>
      val oneDistance = (onePiece(uuid) - startPositions(uuid)).toMeters
      val sixDistance = (sixPiece(uuid) - startPositions(uuid)).toMeters
      withClue(s"vehicle $uuid travelled $oneDistance m on one piece vs $sixDistance m on six pieces - ") {
        oneDistance shouldBe sixDistance +- toleranceMeters
      }
    }
  }

  "splitting a road into more pieces" should "not change cumulative distance travelled after 200 ticks of ordinary free-flow traffic" in {
    val specs = vehicleSpecs(MetersPerSecond(10))
    val startPositions = specs.map(s => s.piloted.uuid -> s.startAt).toMap

    val (_, oneIndex) = NetworkFixtures.straightChain(1, Meters(600))
    val (_, sixIndex) = NetworkFixtures.straightChain(6, Meters(100))

    val oneFinal = run(oneIndex, initialTraffic(specs, oneIndex))
    val sixFinal = run(sixIndex, initialTraffic(specs, sixIndex))

    assertAgreement(finalPositions(oneFinal, oneIndex), finalPositions(sixFinal, sixIndex), startPositions)
  }

  it should "not change cumulative distance travelled through a stop-and-go wave, the lead vehicle braking hard at tick 50" in {
    val specs = vehicleSpecs(MetersPerSecond(15))
    val startPositions = specs.map(s => s.piloted.uuid -> s.startAt).toMap
    val leadUuid = specs.maxBy(_.startAt.toMeters).piloted.uuid

    def brakeHard(traffic: NetworkTraffic): NetworkTraffic = withSpeed(traffic, leadUuid, MetersPerSecond(0))

    val (_, oneIndex) = NetworkFixtures.straightChain(1, Meters(600))
    val (_, sixIndex) = NetworkFixtures.straightChain(6, Meters(100))

    val oneFinal = run(oneIndex, initialTraffic(specs, oneIndex), { case 50 => brakeHard(_) })
    val sixFinal = run(sixIndex, initialTraffic(specs, sixIndex), { case 50 => brakeHard(_) })

    assertAgreement(finalPositions(oneFinal, oneIndex), finalPositions(sixFinal, sixIndex), startPositions)
  }
}
