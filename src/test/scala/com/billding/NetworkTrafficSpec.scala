package com.billding

import squants.motion.{Distance, KilometersPerHour}
import squants.space.{Length, Meters}
import squants.QuantityVector
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import com.billding.network._
import com.billding.physics.{Spatial, StraightPath}
import com.billding.traffic.{IntelligentDriverModelImpl, PilotedVehicle}

class NetworkTrafficSpec extends AnyFlatSpec with Matchers {

  private val laneWidth = Meters(3.5)
  private val speedLimit = KilometersPerHour(50)
  private val idm = new IntelligentDriverModelImpl

  private def straightSection(id: String, from: Double, to: Double): LaneSection =
    LaneSection(
      SectionId(id),
      StraightPath(
        QuantityVector[Distance](Meters(from), Meters(0), Meters(0)),
        QuantityVector[Distance](Meters(to), Meters(0), Meters(0))
      ),
      laneWidth,
      speedLimit,
      layer = 0
    )

  /**
    * The same construction TrackLane uses privately for a car parked at an
    * arc-length position: a Spatial with no particular heading, and a
    * PilotedVehicle whose destination is itself, since nothing in phase C
    * reads it yet.
    */
  private def commuterAt(s: Length): PilotedVehicle = {
    val spatial = Spatial.withVecs(QuantityVector[Distance](s, Meters(0), Meters(0)))
    PilotedVehicle.commuter2(spatial, idm, spatial)
  }

  private def vehicleAt(section: SectionId, s: Length): NetworkVehicle =
    NetworkVehicle(commuterAt(s), section, s, speedLimit)

  "Placing vehicles into a section" should "keep them ordered leader-first" in {
    val section = SectionId("a")
    val traffic = List(vehicleAt(section, Meters(10)), vehicleAt(section, Meters(50)), vehicleAt(section, Meters(30)))
      .foldLeft(NetworkTraffic.empty)(NetworkTraffic.place)

    traffic.on(section).map(_.s) shouldBe List(Meters(50), Meters(30), Meters(10))
  }

  it should "keep a growing section sorted as cars are added one at a time" in {
    val section = SectionId("a")
    val order = List(Meters(5), Meters(40), Meters(15), Meters(60), Meters(1))
    val traffic = order.foldLeft(NetworkTraffic.empty) { (current, s) =>
      NetworkTraffic.place(current, vehicleAt(section, s))
    }

    traffic.on(section).map(_.s) shouldBe order.sortBy(-_.toMeters)
  }

  it should "replace a vehicle already present under the same uuid rather than duplicate it" in {
    val section = SectionId("a")
    val piloted = commuterAt(Meters(10))
    val original = NetworkVehicle(piloted, section, Meters(10), speedLimit)
    val moved = original.copy(s = Meters(25), piloted = piloted)

    val traffic = NetworkTraffic.place(NetworkTraffic.place(NetworkTraffic.empty, original), moved)

    traffic.on(section) should have size 1
    traffic.on(section).head.s shouldBe Meters(25)
  }

  "NetworkTraffic.all" should "return every vehicle exactly once, across every section" in {
    val a = SectionId("a")
    val b = SectionId("b")
    val vehicles = List(
      vehicleAt(a, Meters(10)),
      vehicleAt(a, Meters(50)),
      vehicleAt(b, Meters(5)),
      vehicleAt(b, Meters(15)),
      vehicleAt(b, Meters(25))
    )
    val traffic = NetworkTraffic.of(vehicles)

    traffic.all should have size vehicles.size
    traffic.all.map(_.piloted.uuid).toSet shouldBe vehicles.map(_.piloted.uuid).toSet
  }

  "A vehicle's uuid" should "survive a round trip through the structure" in {
    val section = SectionId("a")
    val vehicle = vehicleAt(section, Meters(10))
    val traffic = NetworkTraffic.place(NetworkTraffic.empty, vehicle)

    traffic.vehicleWith(vehicle.piloted.uuid).map(_.piloted.uuid) shouldBe Some(vehicle.piloted.uuid)
    traffic.on(section).head.piloted.uuid shouldBe vehicle.piloted.uuid
  }

  "NetworkVehicle.enteringAt" should "default next to the section's first declared outgoing movement" in {
    val trunk = straightSection("trunk", 0, 100)
    val left = straightSection("left", 100, 200)
    val right = straightSection("right", 100, 200)

    val toLeft = Movement(MovementId("trunk-left"), SectionId("trunk"), SectionId("left"), MovementKind.Diverge, Control.Uncontrolled)
    val toRight = Movement(MovementId("trunk-right"), SectionId("trunk"), SectionId("right"), MovementKind.Diverge, Control.Uncontrolled)

    val network = RoadNetwork(
      sections = Map(SectionId("trunk") -> trunk, SectionId("left") -> left, SectionId("right") -> right),
      movements = List(toLeft, toRight)
    )
    val index = NetworkIndex(network)

    val vehicle = NetworkVehicle.enteringAt(commuterAt(Meters(10)), SectionId("trunk"), Meters(10), speedLimit, index)

    vehicle.next shouldBe Some(MovementId("trunk-left"))
  }

  it should "leave next as None on a dangling outgoing end, the implicit sink" in {
    val onlySection = straightSection("solo", 0, 100)
    val network = RoadNetwork(sections = Map(SectionId("solo") -> onlySection), movements = Nil)
    val index = NetworkIndex(network)

    val vehicle = NetworkVehicle.enteringAt(commuterAt(Meters(10)), SectionId("solo"), Meters(10), speedLimit, index)

    vehicle.next shouldBe None
  }
}
