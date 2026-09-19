package com.billding

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import squants.motion.{Distance, KilometersPerHour, MetersPerSecond}
import squants.space.Meters
import squants.time.Seconds
import squants.{QuantityVector, Velocity}

import com.billding.network._
import com.billding.physics.{Spatial, StraightPath}
import com.billding.svgRendering.{RoadArc, RoadStrip}
import com.billding.traffic.{IntelligentDriverModelImpl, NetworkScene, PilotedVehicle}

/**
  * Built by hand here rather than reusing `NetworkFixtures` throughout, the same way
  * `NetworkTickSpec` and `LookaheadSpec` do it - other agents are working in `network/`
  * elsewhere in this checkout, and a hand-built network needs nothing from them mid-edit.
  * `NetworkFixtures.straightChain` (already landed) is used where a longer road is handy.
  */
class NetworkSceneSpec extends AnyFlatSpec with Matchers {

  private val idm = new IntelligentDriverModelImpl
  private val startingSpeed: Velocity = KilometersPerHour(60)
  private val dt = Seconds(0.5)

  private def commuterAt(s: squants.Length): PilotedVehicle = {
    val spatial = Spatial.withVecs(QuantityVector[Distance](s, Meters(0), Meters(0)))
    PilotedVehicle.commuter2(spatial, idm, spatial)
  }

  private def sceneOn(
    network: RoadNetwork,
    index: NetworkIndex,
    traffic: NetworkTraffic,
    speedLimit: Velocity = startingSpeed
  ): NetworkScene =
    NetworkScene(network, index, traffic, Seconds(0), dt, speedLimit)

  "NetworkScene" should "advance a fixture network ten ticks and find vehicle poses on the road" in {
    val (network, index) = NetworkFixtures.straightChain(3, Meters(100))
    val section0 = SectionId("mainline-0")

    val vehicle = NetworkVehicle.enteringAt(commuterAt(Meters(0)), section0, Meters(0), startingSpeed, index)
    val scene = sceneOn(network, index, NetworkTraffic.of(List(vehicle)))

    val advanced = (1 to 10).foldLeft(scene) { (s, _) => s.updateWithSpeedLimit(s.speedLimit) }

    advanced.t shouldBe Seconds(5)
    advanced.traffic.all should have size 1

    val renderables = advanced.renderables
    renderables should have size 1

    val landed = advanced.traffic.all.head
    val section = network.section(landed.section).get
    val expectedPosition =
      section.path.pointAt(landed.s) + section.path.normalAt(landed.s).map { c: Double => landed.lateral * c }

    val rendered = renderables.head
    rendered.uuid shouldBe vehicle.piloted.uuid
    rendered.position.coordinates.head.toMeters shouldBe expectedPosition.coordinates.head.toMeters +- 1e-9
    rendered.position.coordinates(1).toMeters shouldBe expectedPosition.coordinates(1).toMeters +- 1e-9
    rendered.width shouldBe vehicle.piloted.width
    rendered.height shouldBe vehicle.piloted.height

    // Ten ticks of a car starting from rest should still be well inside the 300 m chain -
    // this test is about poses on the road, not about a vehicle finishing the course.
    advanced.completed shouldBe 0
  }

  it should "emit one road shape per section, straight and arc alike" in {
    val (network, index) = NetworkFixtures.ySplit(Meters(80), Meters(40))
    val scene = sceneOn(network, index, NetworkTraffic.empty)

    val shapes = scene.roadShapes
    shapes should have size network.sections.size
    shapes.collect { case s: RoadStrip => s } should have size 1
    shapes.collect { case a: RoadArc   => a } should have size 2
  }

  it should "fit its extent at one scale for both axes, never the stretched-axis treatment a street allows itself" in {
    val (network, index) = NetworkFixtures.straightChain(2, Meters(120))
    val scene = sceneOn(network, index, NetworkTraffic.empty)

    val projection = scene.project(360, 640)
    projection.metersPerPixelAcross shouldBe projection.metersPerPixelDown
  }

  it should "report no density or source timing, and start with nothing completed" in {
    val (network, index) = NetworkFixtures.straightChain(1, Meters(50))
    val scene = sceneOn(network, index, NetworkTraffic.empty)

    scene.density shouldBe None
    scene.sourceTiming shouldBe None
    scene.completed shouldBe 0
  }

  it should "accumulate completed departures across ticks rather than only reporting the latest tick's" in {
    val solo = SectionId("solo")
    val network = RoadNetwork(
      sections = Map(
        solo -> LaneSection(
          solo,
          StraightPath(
            QuantityVector[Distance](Meters(0), Meters(0), Meters(0)),
            QuantityVector[Distance](Meters(20), Meters(0), Meters(0))
          ),
          Meters(3.5),
          MetersPerSecond(10),
          layer = 0
        )
      ),
      movements = Nil
    )
    val index = NetworkIndex(network)

    // At the section's own 10 m/s speed limit, 3 s of travel each tick comfortably clears
    // the 20 m section with nothing beyond it - the same shape of case `NetworkTickSpec`
    // uses for a departure off a dangling end.
    val vehicle = NetworkVehicle.enteringAt(commuterAt(Meters(0)), solo, Meters(0), MetersPerSecond(10), index)
    val scene = sceneOn(network, index, NetworkTraffic.of(List(vehicle)), speedLimit = MetersPerSecond(10))
      .copy(dt = Seconds(3))

    val afterDeparture = scene.updateWithSpeedLimit(scene.speedLimit)
    afterDeparture.completed shouldBe 1
    afterDeparture.traffic.all shouldBe empty
    afterDeparture.renderables shouldBe empty

    // A further tick with nobody left on the network must not lose the earlier count.
    val stillOne = afterDeparture.updateWithSpeedLimit(afterDeparture.speedLimit)
    stillOne.completed shouldBe 1
  }
}
