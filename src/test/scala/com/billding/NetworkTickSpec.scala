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

  private def diverge(id: String, from: SectionId, to: SectionId): Movement =
    Movement(MovementId(id), from, to, MovementKind.Diverge, Control.Uncontrolled)

  /**
    * A vehicle built directly (not via [[NetworkVehicle.enteringAt]]) so a
    * test can drop it mid-trip: already carrying whatever `next`/`route`/
    * `destination`/`routeFailed` it would have if [[NetworkVehicle.chooseNext]]
    * had already run once, without needing a whole journey to get there.
    */
  private def routedVehicleAt(
    section: SectionId,
    s: Length,
    next: Option[MovementId] = None,
    route: List[MovementId] = Nil,
    destination: Option[SectionId] = None,
    routeFailed: Boolean = false,
    speed: Velocity = speedLimit
  ): NetworkVehicle =
    NetworkVehicle(commuterAt(s), section, s, speed, next = next, route = route, destination = destination, routeFailed = routeFailed)

  /**
    * `home` feeding a split at `hub`: `hub-right` (to `right`) declared
    * first, `hub-left` (to `left`) declared second. The declared order matters
    * only to prove a test isn't passing by accident - `chooseNext`'s
    * placeholder always picks the first declared movement, so a vehicle that
    * lands on `left` did so because of its `route`, not because of how these
    * movements happen to be listed.
    */
  private def splitFixture(homeLength: Length = Meters(20)): (NetworkIndex, SectionId, SectionId, SectionId, SectionId, MovementId, MovementId, MovementId) = {
    val hubLength = Meters(20)
    val branchLength = Meters(160)
    val home = SectionId("home")
    val hub = SectionId("hub")
    val right = SectionId("right")
    val left = SectionId("left")
    val homeHub = continuation("home-hub", home, hub)
    val hubRight = diverge("hub-right", hub, right)
    val hubLeft = diverge("hub-left", hub, left)
    val hubEnd = (homeLength + hubLength).toMeters
    val branchEnd = (homeLength + hubLength + branchLength).toMeters
    val network = RoadNetwork(
      sections = Map(
        home -> straightSection("home", 0, homeLength.toMeters),
        hub -> straightSection("hub", homeLength.toMeters, hubEnd),
        right -> straightSection("right", hubEnd, branchEnd),
        left -> straightSection("left", hubEnd, branchEnd)
      ),
      movements = List(homeHub, hubRight, hubLeft)
    )
    (NetworkIndex(network), home, hub, right, left, homeHub.id, hubRight.id, hubLeft.id)
  }

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

  it should "brake for a stopped leader whose rear has not cleared the seam, not treat the road as free" in {
    val home = SectionId("home")
    val b = SectionId("b")
    val toB = continuation("home-b", home, b)
    val network = RoadNetwork(
      sections = Map(home -> straightSection("home", 0, 55), b -> straightSection("b", 55, 255)),
      movements = List(toB)
    )
    val index = NetworkIndex(network)

    // A stationary follower's lookahead floors to NetworkTick.MinimumLookahead (50 m), short of
    // `home`'s own 55 m length - so a leader only found by walking off the end of `home` would
    // be invisible to it. The leader is stopped at b's very start, so all of carLength (8 m)
    // overhangs backward into `home`, landing its rear at home's s = 47, inside that 50 m.
    val follower = vehicleAt(home, Meters(0), speed = MetersPerSecond(0), next = Some(toB.id))
    val leader = vehicleAt(b, Meters(0), speed = MetersPerSecond(0))
    val traffic = NetworkTraffic.of(List(follower, leader))

    val dt = Seconds(1)
    val gap = Meters(47)
    val expectedAcceleration =
      idm.deltaVDimensionallySafe(MetersPerSecond(0), speedLimit, MetersPerSecond(0), T, accel, braking, gap, s0)
    val (_, expectedTravelled) = ballisticStep(MetersPerSecond(0), expectedAcceleration, dt)
    val freeRoadAcceleration =
      idm.deltaVDimensionallySafe(MetersPerSecond(0), speedLimit, MetersPerSecond(0), T, accel, braking, NetworkTick.FreeRoadGap, s0)

    // Sanity check that the two scenarios really would diverge - otherwise the assertions below
    // would pass even if the seam-spanning leader were invisible to `advance`.
    expectedAcceleration should not be freeRoadAcceleration

    val result = NetworkTick.advance(traffic, index, dt)

    val landedFollower = result.traffic.all.find(_.piloted.uuid == follower.piloted.uuid).get
    landedFollower.section shouldBe home
    landedFollower.s.toMeters shouldBe (Meters(0) + expectedTravelled).toMeters +- 1e-9
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

  it should "land on the route's chosen branch at a split, whichever branch that is" in {
    val (index, home, hub, right, left, homeHub, hubRight, hubLeft) = splitFixture()

    // 2 m from the home/hub seam; free road, so both vehicles comfortably cross into `hub`
    // this tick. One is routed left, the other right - `hub-right` is declared first, so a
    // vehicle that landed on `right` only because of declared order would pass the left-bound
    // assertion below for the wrong reason if this second vehicle weren't also checked.
    val toLeft = routedVehicleAt(home, Meters(18), next = Some(homeHub), route = List(hubLeft), destination = Some(left))
    val toRight = routedVehicleAt(home, Meters(18), next = Some(homeHub), route = List(hubRight), destination = Some(right))
    val traffic = NetworkTraffic.of(List(toLeft, toRight))

    val result = NetworkTick.advance(traffic, index, Seconds(1))

    result.departures shouldBe empty
    val landedLeft = result.traffic.all.find(_.piloted.uuid == toLeft.piloted.uuid).get
    val landedRight = result.traffic.all.find(_.piloted.uuid == toRight.piloted.uuid).get

    landedLeft.section shouldBe hub
    landedLeft.next shouldBe Some(hubLeft)
    landedLeft.route shouldBe empty
    landedLeft.routeFailed shouldBe false

    landedRight.section shouldBe hub
    landedRight.next shouldBe Some(hubRight)
    landedRight.route shouldBe empty
    landedRight.routeFailed shouldBe false
  }

  it should "keep `next` unchanged across ticks while a vehicle queues short of a split" in {
    val (index, home, _, _, left, homeHub, _, hubLeft) = splitFixture(homeLength = Meters(55))

    // Leader and follower both stopped, spaced so the physical gap (leaderS - followerS -
    // carLength) is exactly s0 (6 m) - the IDM's equilibrium spacing at zero speed, so
    // acceleration is ~0 and the follower stays queued in `home` for as many ticks as this
    // test cares to run, never reaching the `home`/`hub` seam where `next` would be redrawn.
    val follower =
      routedVehicleAt(home, Meters(0), speed = MetersPerSecond(0), next = Some(homeHub), route = List(hubLeft), destination = Some(left))
    val leader = routedVehicleAt(home, Meters(14), speed = MetersPerSecond(0))
    val traffic = NetworkTraffic.of(List(follower, leader))

    val ticked = (1 to 5).foldLeft(traffic) { (currentTraffic, _) =>
      val result = NetworkTick.advance(currentTraffic, index, Seconds(1))
      result.departures shouldBe empty

      val stillFollower = result.traffic.all.find(_.piloted.uuid == follower.piloted.uuid).get
      stillFollower.section shouldBe home
      stillFollower.next shouldBe Some(homeHub)
      stillFollower.route shouldBe List(hubLeft)
      stillFollower.routeFailed shouldBe false

      result.traffic
    }

    ticked.all should have size 2
  }

  it should "replan and complete when a vehicle's route ran out before reaching its destination" in {
    val (index, home, hub, _, left, homeHub, _, hubLeft) = splitFixture()

    // Route already exhausted (as if it had run out one seam early), but `destination` still
    // set to `left` - `hub` still has outgoing movements, so this is exactly the "missed its
    // exit" trigger the card names: exhausted route, section with outgoing movements.
    val vehicle = routedVehicleAt(home, Meters(18), next = Some(homeHub), route = Nil, destination = Some(left))
    val traffic = NetworkTraffic.of(List(vehicle))

    val afterFirstSeam = NetworkTick.advance(traffic, index, Seconds(1))
    afterFirstSeam.departures shouldBe empty
    val replanned = afterFirstSeam.traffic.all.find(_.piloted.uuid == vehicle.piloted.uuid).get
    replanned.section shouldBe hub
    replanned.next shouldBe Some(hubLeft) // replanned from `hub`, not the placeholder `hub-right`
    replanned.route shouldBe empty
    replanned.routeFailed shouldBe false

    // A further, generous tick to cross the remaining distance through `hub` onto `left`.
    val afterSecondSeam = NetworkTick.advance(afterFirstSeam.traffic, index, Seconds(2))
    afterSecondSeam.departures shouldBe empty
    val completed = afterSecondSeam.traffic.all.find(_.piloted.uuid == vehicle.piloted.uuid).get
    completed.section shouldBe left
    completed.routeFailed shouldBe false
  }

  it should "mark a vehicle whose replan fails rather than remove it, and let it keep driving" in {
    val (index, home, hub, _, _, homeHub, hubRight, _) = splitFixture()

    // A destination that doesn't exist in this network at all: `Routing.planRoute` returns
    // `None` immediately, so the replan this vehicle needs at `hub` fails outright.
    val vehicle =
      routedVehicleAt(home, Meters(18), next = Some(homeHub), route = Nil, destination = Some(SectionId("nowhere")))
    val traffic = NetworkTraffic.of(List(vehicle))

    val result = NetworkTick.advance(traffic, index, Seconds(1))

    result.departures shouldBe empty
    val landed = result.traffic.all.find(_.piloted.uuid == vehicle.piloted.uuid).get
    landed.section shouldBe hub
    landed.routeFailed shouldBe true
    // Continues legally rather than teleporting or vanishing: the placeholder first declared
    // outgoing movement, same as an unrouted vehicle would get.
    landed.next shouldBe Some(hubRight)
    landed.speed should be > MetersPerSecond(0)
  }
}
