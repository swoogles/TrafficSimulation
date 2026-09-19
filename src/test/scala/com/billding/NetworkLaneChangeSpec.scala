package com.billding

import squants.motion.{Distance, KilometersPerHour, MetersPerSecond}
import squants.space.{Length, Meters}
import squants.time.Seconds
import squants.{DoubleVector, QuantityVector, Time, Velocity}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import com.billding.network._
import com.billding.physics.{PathGrowth, Spatial}
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
  *
  * Card F3 adds the mandatory-change tests further down, against two more
  * fixtures: [[NetworkFixtures.rampMerge]] (an ending lane) and a hand-built
  * exit lane ([[exitFixture]]) that this file owns outright, since building
  * it exercises exactly the gap `Routing.planRoute` cannot cross on its own -
  * see that method's own doc for why.
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

  /**
    * A mainline lane that keeps going ("through") running alongside a
    * dedicated exit lane ("exit-lane") for `length`, side by side over their
    * whole shared length exactly the way [[NetworkFixtures.rampMerge]] pairs
    * its own accel lanes. Only "exit-lane" carries the diverge onto a short
    * "exit-branch"; "through" continues instead into a plain "through-far".
    * A third lane, "fast-lane", sits on `through`'s *other* side - empty, and
    * with nothing to do with the exit at all - so a test can check that
    * urgency pulls a car towards the lane its plan actually needs and not
    * merely towards whichever neighbour happens to look most inviting.
    *
    * Not part of [[NetworkFixtures]] because what it is built to exercise is
    * out of that file's scope: nothing but a [[LaneNeighbour]] connects
    * "through" to "exit-lane" - no [[Movement]] does - so `Routing.planRoute`,
    * which only ever walks movements, could never produce a route that puts
    * a car on "through" with a plan that needs "exit-lane"'s diverge. That
    * combination is precisely `NetworkLaneChange.mandatoryPoint`'s reason to
    * exist, so the tests below construct it by hand with `NetworkVehicle`
    * directly - the same way every other test in this file already
    * constructs a car without going through `enteringAt`.
    */
  private def exitFixture(length: Length): (RoadNetwork, NetworkIndex) = {
    val east = DoubleVector(1.0, 0.0, 0.0)
    val origin = QuantityVector[Distance](Meters(0), Meters(0), Meters(0))
    val laneWidth = Meters(3.5)
    val speedLimit = KilometersPerHour(80)

    def offsetOrigin(lanesLeft: Double) = origin + QuantityVector[Distance](Meters(0), laneWidth * lanesLeft, Meters(0))

    val throughPath = PathGrowth.straightFrom(origin, east, length)
    val exitLanePath = PathGrowth.straightFrom(offsetOrigin(-1), east, length)
    val fastLanePath = PathGrowth.straightFrom(offsetOrigin(1), east, length)
    val throughFarPath = PathGrowth.straightFrom(PathGrowth.endPoint(throughPath), east, Meters(100))
    val exitBranchPath = PathGrowth.straightFrom(PathGrowth.endPoint(exitLanePath), east, Meters(50))

    def section(id: SectionId, path: com.billding.physics.Path) = LaneSection(id, path, laneWidth, speedLimit, layer = 0)

    val through = section(SectionId("through"), throughPath)
    val exitLane = section(SectionId("exit-lane"), exitLanePath)
    val fastLane = section(SectionId("fast-lane"), fastLanePath)
    val throughFar = section(SectionId("through-far"), throughFarPath)
    val exitBranch = section(SectionId("exit-branch"), exitBranchPath)

    val movements = List(
      Movement.continuation(MovementId("through-through-far"), SectionId("through"), SectionId("through-far")),
      Movement(
        MovementId("exit-lane-exit-branch"),
        SectionId("exit-lane"),
        SectionId("exit-branch"),
        MovementKind.Diverge,
        Control.Uncontrolled
      )
    )

    val laneNeighbours = List(
      LaneNeighbour(SectionId("through"), SectionId("exit-lane"), Side.RightOf, Meters(0), length, Meters(0)),
      LaneNeighbour(SectionId("exit-lane"), SectionId("through"), Side.LeftOf, Meters(0), length, Meters(0)),
      LaneNeighbour(SectionId("through"), SectionId("fast-lane"), Side.LeftOf, Meters(0), length, Meters(0)),
      LaneNeighbour(SectionId("fast-lane"), SectionId("through"), Side.RightOf, Meters(0), length, Meters(0))
    )

    val network = RoadNetwork(
      List(through, exitLane, fastLane, throughFar, exitBranch).map(s => s.id -> s).toMap,
      movements,
      laneNeighbours
    )
    (network, NetworkIndex(network))
  }

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

  // Card F3: mandatory changes, layered on top of F2's discretionary ones.

  "a vehicle merging out of an ending lane (an on-ramp's own acceleration lane)" should
    "leave it well before the taper, not at it" in {
      val accelLength = Meters(500)
      val (_, index) = NetworkFixtures.rampMerge(Meters(200), accelLength)

      val merging = vehicleAt(SectionId("ramp-accel"), Meters(0), MetersPerSecond(20))

      // Drives with `NetworkTick.advance`, not `NetworkLaneChange.advance` alone, because only
      // a full tick actually moves the car forward - the lane-change phase alone only ever
      // settles a change already in flight, so `s` (and with it, urgency) would never budge.
      @annotation.tailrec
      def driveUntilMerged(traffic: NetworkTraffic, ticksLeft: Int): Option[Length] =
        if (ticksLeft <= 0) None
        else
          traffic.vehicleWith(merging.piloted.uuid) match {
            case None => None // ran off the end of the ramp without ever merging
            case Some(before) =>
              val result = NetworkTick.advance(traffic, index, dt)
              result.traffic.vehicleWith(merging.piloted.uuid) match {
                case Some(after) if after.section != before.section => Some(accelLength - before.s)
                case Some(_)                                        => driveUntilMerged(result.traffic, ticksLeft - 1)
                case None                                            => None
              }
          }

      val mergedWithRemaining = driveUntilMerged(NetworkTraffic.of(List(merging)), ticksLeft = 400)

      mergedWithRemaining shouldBe defined
      // "Well before the taper" against its 500 m length, not a last-second scramble at the end.
      mergedWithRemaining.get should be > Meters(100)
    }

  "a vehicle routed to an exit lane on a busy road" should
    "reach it well before the diverge, not just when the gap happens to be free" in {
      val length = Meters(600)
      val (_, index) = exitFixture(length)

      // Physically on "through", but its plan needs "exit-lane"'s own diverge - the mismatch
      // `mandatoryPoint` is built to catch. Built by hand rather than via `enteringAt` because
      // `Routing.planRoute` has no way to produce this state itself - see `exitFixture`'s doc.
      val exitBound = NetworkVehicle(
        piloted = commuterAt(Meters(0)),
        section = SectionId("through"),
        s = Meters(0),
        speed = MetersPerSecond(20),
        next = Some(MovementId("exit-lane-exit-branch")),
        route = Nil,
        destination = Some(SectionId("exit-branch"))
      )
      // A slow leader already sitting in the exit lane - a purely discretionary MOBIL would
      // rather stay in the empty, faster "through" lane than tuck in behind this. Only
      // mounting urgency, as the diverge gets closer, overrides that reluctance.
      val busyLeader = vehicleAt(SectionId("exit-lane"), Meters(350), MetersPerSecond(5))

      @annotation.tailrec
      def driveUntilMerged(traffic: NetworkTraffic, ticksLeft: Int, sawFastLane: Boolean): (Option[Length], Boolean) =
        traffic.vehicleWith(exitBound.piloted.uuid) match {
          case None => (None, sawFastLane)
          case Some(current) if current.section == SectionId("exit-lane") => (Some(length - current.s), sawFastLane)
          case Some(_) if ticksLeft <= 0                                  => (None, sawFastLane)
          case Some(current) =>
            driveUntilMerged(
              NetworkTick.advance(traffic, index, dt).traffic,
              ticksLeft - 1,
              sawFastLane || current.section == SectionId("fast-lane")
            )
        }

      val (mergedWithRemaining, everInFastLane) =
        driveUntilMerged(NetworkTraffic.of(List(exitBound, busyLeader)), ticksLeft = 300, sawFastLane = false)

      mergedWithRemaining shouldBe defined
      // Well before the diverge at the far end of "exit-lane", not a last-moment lunge for it.
      mergedWithRemaining.get should be > Meters(200)
      // The empty, faster lane on the *other* side never tempts it: urgency only ever pointed
      // it at the one lane its plan actually needs.
      everInFastLane shouldBe false
    }

  "a vehicle with no route, sitting well clear of any dead end" should
    "gain no urgency at all" in {
      val (_, index) = NetworkFixtures.straightChain(1, Meters(500), lanes = 2)
      val alone = vehicleAt(lane0, Meters(0), MetersPerSecond(20))

      // 30 ticks at 20 m/s covers about 60 m - the far end of this 500 m lane stays well
      // outside `NetworkLaneChange`'s onset distance the whole time, so this is purely a
      // check that nothing manufactures urgency out of thin air: alone in the rightmost
      // lane already, keep-right bias gives it nothing to gain by moving left either.
      val result = (1 to 30).foldLeft(NetworkTraffic.of(List(alone))) {
        case (traffic, _) => NetworkLaneChange.advance(traffic, index, dt)
      }

      result.vehicleWith(alone.piloted.uuid).get.section shouldBe lane0
    }
}
