package com.billding

import squants.motion.{Acceleration, Distance, MetersPerSecond}
import squants.space.{Length, Meters}
import squants.time.Seconds
import squants.{QuantityVector, Time, Velocity}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import com.billding.network._
import com.billding.physics.{Spatial, StraightPath}
import com.billding.traffic.{IntelligentDriverModelImpl, PilotedVehicle}

/**
  * Fixtures built by hand here, the same way `LookaheadSpec` and
  * `NetworkTrafficSpec` do it, rather than reusing a shared fixtures file -
  * see those specs' notes about one possibly landing mid-edit elsewhere in
  * this checkout.
  */
class NetworkTickSpec extends AnyFlatSpec with Matchers {

  private val laneWidth = Meters(3.5)
  private val speedLimit: Velocity = MetersPerSecond(10)
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

  /** Same construction `LookaheadSpec`/`NetworkTrafficSpec`/`TrackLane` use for a parked test car. */
  private def commuterAt(s: Length): PilotedVehicle = {
    val spatial = Spatial.withVecs(QuantityVector[Distance](s, Meters(0), Meters(0)))
    PilotedVehicle.commuter2(spatial, idm, spatial)
  }

  /** Every commuter built above has this along-the-road length, whatever position it's given. */
  private val carLength: Length = commuterAt(Meters(0)).width

  private def vehicleAt(
    section: SectionId,
    s: Length,
    speed: Velocity = speedLimit,
    next: Option[MovementId] = None
  ): NetworkVehicle = NetworkVehicle(commuterAt(s), section, s, speed, next = next)

  private def continuation(id: String, from: SectionId, to: SectionId): Movement =
    Movement.continuation(MovementId(id), from, to)

  /**
    * The IDM's driver constants baked into `commuterAt` (`Driver.commuter`
    * and `VehicleStats.Commuter`), pulled out here so the expected-value
    * maths below reads as the formula, not as magic numbers.
    */
  private val T = Seconds(1)
  private val accel: Acceleration = commuterAt(Meters(0)).vehicle.accelerationAbility
  private val braking: Acceleration = commuterAt(Meters(0)).vehicle.brakingAbility
  private val s0: Length = commuterAt(Meters(0)).driver.minimumDistance

  /** [[NetworkTick]]'s private ballistic step, reproduced so the expected values below are independent of it. */
  private def ballisticStep(speed: Velocity, acceleration: Acceleration, dt: Time): (Velocity, Length) = {
    val projected = speed + acceleration * dt
    if (projected > MetersPerSecond(0)) (projected, (speed + projected) / 2.0 * dt)
    else (MetersPerSecond(0), Meters(0))
  }

  "advance" should "carry a single vehicle's leftover distance across two seams in one tick, advancing it exactly once" in {
    val home = SectionId("home")
    val s1 = SectionId("s1")
    val s2 = SectionId("s2")
    val toS1 = continuation("home-s1", home, s1)
    val s1ToS2 = continuation("s1-s2", s1, s2)
    val network = RoadNetwork(
      sections = Map(
        home -> straightSection("home", 0, 20),
        s1 -> straightSection("s1", 20, 23),
        s2 -> straightSection("s2", 23, 123)
      ),
      movements = List(toS1, s1ToS2)
    )
    val index = NetworkIndex(network)

    // 2 m from the end of `home`; nobody else on the network, so this is the free-road case.
    val vehicle = vehicleAt(home, Meters(18), next = Some(toS1.id))
    val traffic = NetworkTraffic.of(List(vehicle))

    val dt = Seconds(1)
    val expectedAcceleration =
      idm.deltaVDimensionallySafe(speedLimit, speedLimit, MetersPerSecond(0), T, accel, braking, NetworkTick.FreeRoadGap, s0)
    val (_, expectedTravelled) = ballisticStep(speedLimit, expectedAcceleration, dt)

    val result = NetworkTick.advance(traffic, index, dt)

    // A single dt's worth of travel (roughly 10 m) is more than `home`'s remaining 2 m plus
    // all of `s1`'s 3 m, so the vehicle must cross both seams to land in `s2`. If a buggy
    // single-pass implementation instead advanced it again after moving it into `s1` (or
    // `s2`), the extra pass's acceleration and integration would add a second dt's motion,
    // landing it well past this value.
    result.departures shouldBe empty
    result.traffic.all should have size 1
    val landed = result.traffic.all.head
    landed.section shouldBe s2
    landed.s.toMeters shouldBe (Meters(18) + expectedTravelled - Meters(20) - Meters(3)).toMeters +- 1e-9
    landed.piloted.uuid shouldBe vehicle.piloted.uuid
  }

  it should "keep a queue of five vehicles in order as the lead car crosses a seam" in {
    val sectionA = SectionId("a")
    val sectionB = SectionId("b")
    val toB = continuation("a-b", sectionA, sectionB)
    val network = RoadNetwork(
      sections = Map(
        sectionA -> straightSection("a", 0, 100),
        sectionB -> straightSection("b", 100, 400)
      ),
      movements = List(toB)
    )
    val index = NetworkIndex(network)

    // Route position (distance from the start of `a`) 35 m apart, so every follower has an
    // identical 27 m physical gap to its pre-tick leader and thus an identical acceleration -
    // the uniform spacing is what keeps the expected maths to one number instead of five.
    val v1 = vehicleAt(sectionB, Meters(45)) // Free road: nothing ahead of it at all.
    val v2 = vehicleAt(sectionB, Meters(10))
    val v3 = vehicleAt(sectionA, Meters(75), next = Some(toB.id)) // 25 m from the seam.
    val v4 = vehicleAt(sectionA, Meters(40))
    val v5 = vehicleAt(sectionA, Meters(5))
    val traffic = NetworkTraffic.of(List(v1, v2, v3, v4, v5))

    val dt = Seconds(3)
    val followerGap = Meters(35) - carLength // 27 m
    val followerAcceleration =
      idm.deltaVDimensionallySafe(speedLimit, speedLimit, MetersPerSecond(0), T, accel, braking, followerGap, s0)
    val leaderAcceleration =
      idm.deltaVDimensionallySafe(speedLimit, speedLimit, MetersPerSecond(0), T, accel, braking, NetworkTick.FreeRoadGap, s0)
    val (_, leaderTravelled) = ballisticStep(speedLimit, leaderAcceleration, dt)
    val (_, followerTravelled) = ballisticStep(speedLimit, followerAcceleration, dt)

    val result = NetworkTick.advance(traffic, index, dt)

    result.departures shouldBe empty
    result.traffic.all should have size 5

    // v3 alone crosses the seam: 75 + followerTravelled must exceed `a`'s 100 m length.
    (Meters(75) + followerTravelled).toMeters should be > 100.0
    (Meters(40) + followerTravelled).toMeters should be <= 100.0
    (Meters(5) + followerTravelled).toMeters should be <= 100.0

    val onA = result.traffic.on(sectionA)
    val onB = result.traffic.on(sectionB)
    onA.map(_.piloted.uuid) shouldBe List(v4.piloted.uuid, v5.piloted.uuid)
    onB.map(_.piloted.uuid) shouldBe List(v1.piloted.uuid, v2.piloted.uuid, v3.piloted.uuid)

    def sOf(uuid: java.util.UUID): Length = result.traffic.all.find(_.piloted.uuid == uuid).get.s

    sOf(v1.piloted.uuid).toMeters shouldBe (Meters(45) + leaderTravelled).toMeters +- 1e-9
    sOf(v2.piloted.uuid).toMeters shouldBe (Meters(10) + followerTravelled).toMeters +- 1e-9
    // v3's gap was measured to v2's *pre-tick* position (s = 10 in b); a bug that read v2's
    // already-moved position this same tick would shift this value.
    sOf(v3.piloted.uuid).toMeters shouldBe (Meters(75) + followerTravelled - Meters(100)).toMeters +- 1e-9
    sOf(v4.piloted.uuid).toMeters shouldBe (Meters(40) + followerTravelled).toMeters +- 1e-9
    sOf(v5.piloted.uuid).toMeters shouldBe (Meters(5) + followerTravelled).toMeters +- 1e-9
  }

  it should "return a vehicle that overshoots a dangling end as a departure, not clamp it in place" in {
    val solo = SectionId("solo")
    val network = RoadNetwork(sections = Map(solo -> straightSection("solo", 0, 20)), movements = Nil)
    val index = NetworkIndex(network)

    // At the section's own speed limit (so acceleration is ~0, not a hard brake), 3 s of
    // travel at 10 m/s comfortably clears the 20 m section with nothing beyond it.
    val vehicle = vehicleAt(solo, Meters(0), next = None)
    val traffic = NetworkTraffic.of(List(vehicle))

    val result = NetworkTick.advance(traffic, index, Seconds(3))

    result.departures.map(_.piloted.uuid) shouldBe List(vehicle.piloted.uuid)
    result.traffic.all shouldBe empty
    result.traffic.on(solo) shouldBe empty
  }

  it should "leave a vehicle that stays within its section untouched in section and next" in {
    val onlySection = SectionId("solo")
    val network = RoadNetwork(sections = Map(onlySection -> straightSection("solo", 0, 1000)), movements = Nil)
    val index = NetworkIndex(network)

    val vehicle = vehicleAt(onlySection, Meters(0))
    val traffic = NetworkTraffic.of(List(vehicle))

    val result = NetworkTick.advance(traffic, index, Seconds(1))

    result.departures shouldBe empty
    result.traffic.all should have size 1
    result.traffic.all.head.section shouldBe onlySection
    result.traffic.all.head.s.toMeters should be > 0.0
  }
}
