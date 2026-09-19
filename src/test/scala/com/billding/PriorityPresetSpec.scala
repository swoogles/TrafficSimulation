package com.billding

import com.billding.network._
import com.billding.network.PriorityPreset.{AllWayStop, TwoWayStop, YieldOnMinor}
import com.billding.physics.Spatial
import com.billding.traffic.{IntelligentDriverModelImpl, PilotedVehicle}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import squants.QuantityVector
import squants.motion.{Distance, MetersPerSecond}
import squants.space.{Length, Meters}

/** H3: presets write the controls H2 actually uses; they are not drawing-only labels. */
class PriorityPresetSpec extends AnyFlatSpec with Matchers {

  private val junction =
    Set("northbound", "southbound", "westbound", "eastbound").map(MovementId.apply)
  private val majorApproaches = Set(SectionId("east-in"), SectionId("west-in"))

  private def controlOf(network: RoadNetwork, id: String): Control =
    network.movements.find(_.id == MovementId(id)).get.control

  private def vehicleAt(section: SectionId, s: Length, index: NetworkIndex): NetworkVehicle = {
    val spatial = Spatial.withVecs(QuantityVector[Distance](s, Meters(0), Meters(0)))
    val piloted = PilotedVehicle.commuter2(spatial, new IntelligentDriverModelImpl, spatial)
    NetworkVehicle.enteringAt(piloted, section, s, MetersPerSecond(8), index)
  }

  "AllWayStop" should "stop every movement in the junction and leave unrelated movements alone" in {
    val (crossing, _) = NetworkFixtures.fourWayCrossing(Meters(70))
    val unrelated = Movement.continuation(MovementId("elsewhere"), SectionId("north-out"), SectionId("north-out"))
    val network = crossing.copy(movements = crossing.movements :+ unrelated)

    val result = AllWayStop(junction)(network)

    junction.foreach(id => result.movements.find(_.id == id).get.control shouldBe Control.Stop)
    result.movements.find(_.id == unrelated.id).get shouldBe unrelated
    network.movements.foreach(_.control shouldBe Control.Uncontrolled)
  }

  "TwoWayStop" should "leave the major road uncontrolled and stop the minor approaches" in {
    val (network, _) = NetworkFixtures.fourWayCrossing(Meters(70))
    val result = TwoWayStop(junction, majorApproaches)(network)

    controlOf(result, "westbound") shouldBe Control.Uncontrolled
    controlOf(result, "eastbound") shouldBe Control.Uncontrolled
    controlOf(result, "northbound") shouldBe Control.Stop
    controlOf(result, "southbound") shouldBe Control.Stop
  }

  "YieldOnMinor" should "leave the major road uncontrolled and make the minor approaches yield" in {
    val (network, _) = NetworkFixtures.fourWayCrossing(Meters(70))
    val result = YieldOnMinor(junction, majorApproaches)(network)

    controlOf(result, "westbound") shouldBe Control.Uncontrolled
    controlOf(result, "eastbound") shouldBe Control.Uncontrolled
    controlOf(result, "northbound") shouldBe Control.Yield
    controlOf(result, "southbound") shouldBe Control.Yield
  }

  "the three presets" should "produce different admission decisions for identical traffic" in {
    val armLength = Meters(70)
    val (base, _) = NetworkFixtures.fourWayCrossing(armLength)

    def blockedApproaches(preset: PriorityPreset): Set[SectionId] = {
      val index = NetworkIndex(preset(base))
      // The minor-road car is nearer its line, so Yield admits it; Stop still requires a
      // full stop. The major-road car is farther away and distinguishes all-way stop from
      // both major-road-priority presets.
      val minor = vehicleAt(SectionId("south-in"), armLength - Meters(10), index)
      val major = vehicleAt(SectionId("west-in"), armLength - Meters(30), index)
      val traffic = NetworkTraffic.of(List(minor, major))

      List(minor, major).collect {
        case vehicle if Admission.stoppingConstraint(traffic, index, vehicle).nonEmpty => vehicle.section
      }.toSet
    }

    blockedApproaches(AllWayStop(junction)) shouldBe Set(SectionId("south-in"), SectionId("west-in"))
    blockedApproaches(TwoWayStop(junction, majorApproaches)) shouldBe Set(SectionId("south-in"))
    blockedApproaches(YieldOnMinor(junction, majorApproaches)) shouldBe empty
  }

  "an explicit movement override" should "win only when it is applied after the latest preset" in {
    val (network, _) = NetworkFixtures.fourWayCrossing(Meters(70))
    val movement = MovementId("northbound")

    val overriddenBefore = PriorityPreset.overrideMovement(network, movement, Control.Uncontrolled)
    controlOf(AllWayStop(junction)(overriddenBefore), "northbound") shouldBe Control.Stop

    val presetFirst = AllWayStop(junction)(network)
    val overriddenAfter = PriorityPreset.overrideMovement(presetFirst, movement, Control.Yield)
    controlOf(overriddenAfter, "northbound") shouldBe Control.Yield
  }
}
