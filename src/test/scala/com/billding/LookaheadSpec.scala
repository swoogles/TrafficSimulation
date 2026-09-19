package com.billding

import squants.motion.{Distance, KilometersPerHour}
import squants.space.{Length, Meters}
import squants.QuantityVector
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import com.billding.network._
import com.billding.physics.{Spatial, StraightPath}
import com.billding.traffic.{IntelligentDriverModelImpl, PilotedVehicle}

/**
  * Fixtures here are built by hand, the same way `NetworkTrafficSpec` does it,
  * rather than reusing a shared fixtures file - see the card notes about a
  * fixtures file possibly landing mid-edit elsewhere in this checkout.
  */
class LookaheadSpec extends AnyFlatSpec with Matchers {

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

  /** Same construction `NetworkTrafficSpec` and `TrackLane` use for a parked test car. */
  private def commuterAt(s: Length): PilotedVehicle = {
    val spatial = Spatial.withVecs(QuantityVector[Distance](s, Meters(0), Meters(0)))
    PilotedVehicle.commuter2(spatial, idm, spatial)
  }

  /** Every commuter built above has this along-the-road length, whatever position it's given. */
  private val carLength: Length = commuterAt(Meters(0)).width

  private def vehicleAt(section: SectionId, s: Length, next: Option[MovementId] = None): NetworkVehicle =
    NetworkVehicle(commuterAt(s), section, s, speedLimit, next = next)

  private def continuation(id: String, from: SectionId, to: SectionId): Movement =
    Movement.continuation(MovementId(id), from, to)

  "leaderOf" should "find a leader in the same section" in {
    val section = SectionId("a")
    val network = RoadNetwork(Map(section -> straightSection("a", 0, 200)), Nil)
    val index = NetworkIndex(network)

    val follower = vehicleAt(section, Meters(10))
    val leader = vehicleAt(section, Meters(40))
    val traffic = NetworkTraffic.of(List(follower, leader))

    val result = Lookahead.leaderOf(traffic, index, follower, within = Meters(100))

    result.map(_._1.piloted.uuid) shouldBe Some(leader.piloted.uuid)
    result.map(_._2) shouldBe Some(Meters(30) - carLength)
  }

  it should "find a stopped leader just past a seam, carrying leftover distance across the boundary" in {
    val a = SectionId("a")
    val b = SectionId("b")
    val toB = continuation("a-b", a, b)
    val network = RoadNetwork(
      sections = Map(a -> straightSection("a", 0, 100), b -> straightSection("b", 100, 300)),
      movements = List(toB)
    )
    val index = NetworkIndex(network)

    // 10 m from the end of `a`.
    val follower = vehicleAt(a, Meters(90), next = Some(toB.id))
    // Stopped 5 m into `b`.
    val leader = vehicleAt(b, Meters(5))
    val traffic = NetworkTraffic.of(List(follower, leader))

    val result = Lookahead.leaderOf(traffic, index, follower, within = Meters(100))

    result.map(_._1.piloted.uuid) shouldBe Some(leader.piloted.uuid)
    // 10 m to the seam + 5 m into b, minus the leader's length.
    result.map(_._2) shouldBe Some(Meters(15) - carLength)
  }

  it should "carry leftover distance across more than one short section in a step" in {
    val home = SectionId("home")
    val s1 = SectionId("s1")
    val s2 = SectionId("s2")
    val s3 = SectionId("s3")
    val toS1 = continuation("home-s1", home, s1)
    val s1ToS2 = continuation("s1-s2", s1, s2)
    val s2ToS3 = continuation("s2-s3", s2, s3)
    val network = RoadNetwork(
      sections = Map(
        home -> straightSection("home", 0, 20),
        s1 -> straightSection("s1", 20, 25),
        s2 -> straightSection("s2", 25, 30),
        s3 -> straightSection("s3", 30, 35)
      ),
      movements = List(toS1, s1ToS2, s2ToS3)
    )
    val index = NetworkIndex(network)

    // 5 m from the end of `home`.
    val follower = vehicleAt(home, Meters(15), next = Some(toS1.id))
    // 2 m into the third section downstream.
    val leader = vehicleAt(s3, Meters(2))
    val traffic = NetworkTraffic.of(List(follower, leader))

    val result = Lookahead.leaderOf(traffic, index, follower, within = Meters(50))

    result.map(_._1.piloted.uuid) shouldBe Some(leader.piloted.uuid)
    // 5 m (home) + 5 m (s1) + 5 m (s2) + 2 m (into s3), minus the leader's length.
    result.map(_._2) shouldBe Some(Meters(17) - carLength)
  }

  it should "find nothing when the leader lies beyond the search distance" in {
    val a = SectionId("a")
    val b = SectionId("b")
    val toB = continuation("a-b", a, b)
    val network = RoadNetwork(
      sections = Map(a -> straightSection("a", 0, 100), b -> straightSection("b", 100, 300)),
      movements = List(toB)
    )
    val index = NetworkIndex(network)

    val follower = vehicleAt(a, Meters(0), next = Some(toB.id))
    val leader = vehicleAt(b, Meters(80))
    val traffic = NetworkTraffic.of(List(follower, leader))

    // Gap would be 100 + 80 - carLength, comfortably more than `within`.
    val result = Lookahead.leaderOf(traffic, index, follower, within = Meters(50))

    result shouldBe None
  }

  it should "not loop forever on a ring built from zero-length sections" in {
    val r1 = SectionId("r1")
    val r2 = SectionId("r2")
    val r3 = SectionId("r3")
    val toR2 = continuation("r1-r2", r1, r2)
    val r2ToR3 = continuation("r2-r3", r2, r3)
    val r3ToR1 = continuation("r3-r1", r3, r1)
    val network = RoadNetwork(
      sections = Map(
        r1 -> straightSection("r1", 0, 0),
        r2 -> straightSection("r2", 0, 0),
        r3 -> straightSection("r3", 0, 0)
      ),
      movements = List(toR2, r2ToR3, r3ToR1)
    )
    val index = NetworkIndex(network)

    val follower = vehicleAt(r1, Meters(0), next = Some(toR2.id))
    val traffic = NetworkTraffic.of(List(follower))

    // No leader anywhere on the ring; every section contributes zero distance,
    // so only the hop cap - not the distance budget - can end the walk.
    val result = Lookahead.leaderOf(traffic, index, follower, within = Meters(1000))

    result shouldBe None
  }

  "at a Y-split" should "see only the branch its next points at, even when the other branch is closer" in {
    val trunk = SectionId("trunk")
    val left = SectionId("left")
    val right = SectionId("right")
    val toLeft = continuation("trunk-left", trunk, left)
    val toRight = continuation("trunk-right", trunk, right)
    val network = RoadNetwork(
      sections = Map(
        trunk -> straightSection("trunk", 0, 20),
        left -> straightSection("left", 100, 130),
        right -> straightSection("right", 200, 230)
      ),
      // Right declared first, so a bug that defaulted to "first declared" instead
      // of honouring `next` would pick the wrong branch here.
      movements = List(toRight, toLeft)
    )
    val index = NetworkIndex(network)

    val follower = vehicleAt(trunk, Meters(15), next = Some(toLeft.id))
    val onRight = vehicleAt(right, Meters(2)) // Much closer by raw distance, but on the wrong branch.
    val onLeft = vehicleAt(left, Meters(20))
    val traffic = NetworkTraffic.of(List(follower, onRight, onLeft))

    val result = Lookahead.leaderOf(traffic, index, follower, within = Meters(100))

    result.map(_._1.piloted.uuid) shouldBe Some(onLeft.piloted.uuid)
    // 5 m to the seam + 20 m into `left`, minus the leader's length.
    result.map(_._2) shouldBe Some(Meters(25) - carLength)
  }

  it should "find nothing down the chosen branch when only the other branch has a car" in {
    val trunk = SectionId("trunk")
    val left = SectionId("left")
    val right = SectionId("right")
    val toLeft = continuation("trunk-left", trunk, left)
    val toRight = continuation("trunk-right", trunk, right)
    val network = RoadNetwork(
      sections = Map(
        trunk -> straightSection("trunk", 0, 20),
        left -> straightSection("left", 100, 130),
        right -> straightSection("right", 200, 230)
      ),
      movements = List(toLeft, toRight)
    )
    val index = NetworkIndex(network)

    val follower = vehicleAt(trunk, Meters(15), next = Some(toLeft.id))
    val onRight = vehicleAt(right, Meters(2))
    val traffic = NetworkTraffic.of(List(follower, onRight))

    val result = Lookahead.leaderOf(traffic, index, follower, within = Meters(100))

    result shouldBe None
  }
}
