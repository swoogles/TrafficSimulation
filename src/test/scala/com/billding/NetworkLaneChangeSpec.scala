package com.billding

import squants.motion.{Distance, MetersPerSecond}
import squants.space.{Length, Meters}
import squants.time.Seconds
import squants.{QuantityVector, Time, Velocity}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import com.billding.network._
import com.billding.physics.Spatial
import com.billding.traffic.{IntelligentDriverModelImpl, MOBIL, PilotedVehicle}

/**
  * Card F2: discretionary lane changes on the graph, MOBIL ported through
  * [[LaneMapping.neighbourAt]] and [[Lookahead]] instead of `TrackRoad.transpose`.
  *
  * Built on [[NetworkFixtures.straightChain]] with `lanes = 2` and `count = 1`,
  * the plainest two-lane fixture the shared builder produces: one segment,
  * `SectionId("mainline-0-lane-0")` (rightmost) and `SectionId("mainline-0-lane-1")`
  * (its left neighbour), connected by a symmetric pair of `LaneNeighbour`s
  * spanning the whole segment at zero offset.
  */
class NetworkLaneChangeSpec extends AnyFlatSpec with Matchers {

  private val idm = new IntelligentDriverModelImpl
  private val dt: Time = Seconds(0.1)
  private val segment: Length = Meters(300)

  private val lane0 = SectionId("mainline-0-lane-0") // rightmost
  private val lane1 = SectionId("mainline-0-lane-1") // its left neighbour

  private def fixture(): (RoadNetwork, NetworkIndex) = NetworkFixtures.straightChain(1, segment, lanes = 2)

  /** Same construction every other network spec in this package uses for a test car. */
  private def commuterAt(s: Length): PilotedVehicle = {
    val spatial = Spatial.withVecs(QuantityVector[Distance](s, Meters(0), Meters(0)))
    PilotedVehicle.commuter2(spatial, idm, spatial)
  }

  private def vehicleAt(section: SectionId, s: Length, speed: Velocity): NetworkVehicle =
    NetworkVehicle(commuterAt(s), section, s, speed)

  "a faster driver stuck behind a slow one on a two-lane chain" should "move over into the empty lane" in {
    val (_, index) = fixture()

    val slowLeader = vehicleAt(lane0, Meters(100), MetersPerSecond(3))
    val fastFollower = vehicleAt(lane0, Meters(20), MetersPerSecond(20))
    val initial = NetworkTraffic.of(List(slowLeader, fastFollower))

    val result = NetworkLaneChange.advance(initial, index, dt)

    val moved = result.vehicleWith(fastFollower.piloted.uuid).get
    moved.section shouldBe lane1
    moved.lateral should not be Meters(0) // it has just crossed, so it is still visibly mid-change
  }

  it should "not move into a gap that is already occupied" in {
    val (_, index) = fixture()

    val slowLeader = vehicleAt(lane0, Meters(100), MetersPerSecond(3))
    val fastFollower = vehicleAt(lane0, Meters(20), MetersPerSecond(20))
    // Cruising in the next lane just behind where `fastFollower` would land - its sudden
    // appearance there would force braking harder than MOBIL's safety criterion allows.
    val blocker = vehicleAt(lane1, Meters(15), MetersPerSecond(20))
    val initial = NetworkTraffic.of(List(slowLeader, fastFollower, blocker))

    val result = NetworkLaneChange.advance(initial, index, dt)

    result.vehicleWith(fastFollower.piloted.uuid).get.section shouldBe lane0
    result.vehicleWith(blocker.piloted.uuid).get.section shouldBe lane1
  }

  it should "not immediately dither back once the cooldown is running, even when conditions reverse" in {
    val (_, index) = fixture()

    val slowLeader = vehicleAt(lane0, Meters(100), MetersPerSecond(3))
    val fastFollower = vehicleAt(lane0, Meters(20), MetersPerSecond(20))
    val initial = NetworkTraffic.of(List(slowLeader, fastFollower))

    val afterChange = NetworkLaneChange.advance(initial, index, dt)
    val moved = afterChange.vehicleWith(fastFollower.piloted.uuid).get
    moved.section shouldBe lane1

    // Flip the picture: the old lane is now clear, and a slow leader sits ahead in the
    // *new* lane instead - exactly the situation that would send the driver straight back,
    // if not for the cooldown still running from the change it just made. `blocker2` sits
    // right where the slow leader would need to land if it tried to dodge back into the
    // (otherwise inviting) empty lane itself - MOBIL's own politeness term would otherwise
    // have it get out of `fastFollower`'s way before the cooldown ever gets a chance to
    // matter, which would test that courtesy effect instead of the cooldown this is for.
    val hostileLeader = slowLeader.copy(section = lane1, s = moved.s + Meters(30))
    val blocker2 = vehicleAt(lane0, hostileLeader.s - Meters(2), MetersPerSecond(20))
    val reversed = NetworkTraffic.of(List(hostileLeader, moved, blocker2))

    val stillCoolingDown = NetworkLaneChange.advance(reversed, index, dt)
    stillCoolingDown.vehicleWith(fastFollower.piloted.uuid).get.section shouldBe lane1

    // Run out the cooldown (MOBIL.DefaultCooldown, matching `NetworkLaneChange`'s settle
    // duration) under the same reversed picture; only once it has fully elapsed is the
    // driver considered for another change.
    val ticksToClearCooldown = math.ceil(MOBIL.DefaultCooldown / dt).toInt + 1
    val afterCooldown = (1 to ticksToClearCooldown).foldLeft(stillCoolingDown) {
      case (traffic, _) => NetworkLaneChange.advance(traffic, index, dt)
    }

    afterCooldown.vehicleWith(fastFollower.piloted.uuid).get.section shouldBe lane0
  }

  "NetworkTick.advance" should "run the lane-change phase as part of an ordinary tick" in {
    val (_, index) = fixture()

    val slowLeader = vehicleAt(lane0, Meters(100), MetersPerSecond(3))
    val fastFollower = vehicleAt(lane0, Meters(20), MetersPerSecond(20))
    val initial = NetworkTraffic.of(List(slowLeader, fastFollower))

    val result = NetworkTick.advance(initial, index, dt)

    result.departures shouldBe empty
    result.traffic.vehicleWith(fastFollower.piloted.uuid).get.section shouldBe lane1
  }
}
