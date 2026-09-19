package com.billding

import com.billding.network._
import com.billding.physics.{PathGrowth, RingPath, Spatial}
import com.billding.traffic._
import squants.{DoubleVector, Length, QuantityVector}
import squants.motion.{Distance, DistanceUnit, KilometersPerHour, Velocity, VelocityUnit}
import squants.space.{Kilometers, Meters}
import squants.time.{Hertz, Milliseconds, Seconds, Time}

class SampleSceneCreation(endingSpatial: Spatial)(implicit val DT: Time) {

  private def simplerVehicle(xPos: Double, xV: Double) = {
    val lengthUnit: DistanceUnit = Meters
    val velocityUnit: VelocityUnit = KilometersPerHour
    PilotedVehicle((xPos, 0.0, 0.0, lengthUnit), endingSpatial, (xV, 0.0, 0.0, velocityUnit))
  }

  val emptyScene =
    NamedScene(
      "Empty Scene",
      createWithVehicles(
        Seconds(2),
        List(
          simplerVehicle(15, 100)
        )
      )
    )

  val scene1 =
    NamedScene(
      "group encountering a stopped vehicle",
      createWithVehicles(
        Seconds(3),
        List(
          simplerVehicle(90, 0.1),
          simplerVehicle(55, 100),
          simplerVehicle(40, 100),
          simplerVehicle(25, 100),
          simplerVehicle(10, 100)
        )
      )
    )

  val scene2 =
    NamedScene(
      "stopped group getting back up to speed",
      createWithVehicles(
        Seconds(100),
        List(
          simplerVehicle(125, 0),
          simplerVehicle(120, 0),
          simplerVehicle(115, 0),
          simplerVehicle(110, 0),
          simplerVehicle(105, 0),
          simplerVehicle(100, 0),
          simplerVehicle(95, 0),
          simplerVehicle(90, 0)
        )
      )
    )

  val multipleStoppedGroups =
    NamedScene(
      "multiple stopped groups getting back up to speed",
      createWithVehicles(
        Seconds(4),
        List(
          simplerVehicle(125, 0),
          simplerVehicle(120, 0),
          simplerVehicle(115, 0),
          simplerVehicle(110, 0),
          simplerVehicle(105, 0),
          simplerVehicle(100, 0),
          simplerVehicle(95, 0),
          simplerVehicle(90, 0),
          simplerVehicle(60, 0),
          simplerVehicle(55, 0),
          simplerVehicle(50, 0),
          simplerVehicle(45, 0),
          simplerVehicle(40, 0),
          simplerVehicle(35, 0),
          simplerVehicle(30, 0)
        )
      )
    )

  val singleCarApproachingAStoppedCar =
    NamedScene(
      "single car encountering a stopped vehicle",
      createWithVehicles(
        Seconds(12),
        List(
          simplerVehicle(120, 0),
          simplerVehicle(60, 60)
        )
      )
    )

  private def createWithVehicles(
    sourceTiming: Time,
    vehicles: List[PilotedVehicle]
  ): StreetScene = {

    val speedLimit: Velocity = KilometersPerHour(45) // TODO Connect this to Car Speed Control.
    val originSpatial = Spatial((0, 0, 0, Kilometers))
    val endingSpatial = Spatial((0.5, 0, 0, Kilometers))
    val canvasDimensions: (Length, Length) = (Kilometers(.25), Kilometers(.5))

    val lane =
      Lane.apply(sourceTiming, originSpatial, endingSpatial, speedLimit, vehicles)
    val street = Street(List(lane), originSpatial, endingSpatial)
    StreetScene(
      List(street),
      Seconds(0.2),
      DT,
      speedLimit,
      canvasDimensions
    )
  }

  private val ringSpeedLimit: Velocity = KilometersPerHour(45)

  /**
    * A fixed population going round a closed loop, which is where traffic gets to misbehave
    * on its own: nothing enters, nothing leaves, so any jam you see the cars made themselves.
    */
  private def ring(carCount: Int, circumference: Length): RingScene =
    RingScene(
      TrackRoad(
        List(
          TrackLane.evenlySpaced(
            RingPath.ofCircumference(circumference),
            carCount,
            ringSpeedLimit,
            ringSpeedLimit
          )
        )
      ),
      Seconds(0),
      DT
    )

  /**
    * Two lanes, with the outer one carrying more of the traffic.
    *
    * The imbalance is what gets things moving quickly: a crowded outer lane beside a clearer
    * inner one gives drivers somewhere they obviously want to be, so the first changes happen
    * within a second rather than within half a minute. It is not what gives them a reason at
    * all any more - the drivers disagree about how fast they want to go, so even lanes still
    * overtake - but it is over sooner, because levelling two lanes out is a thing traffic
    * finishes, where wanting past the car in front is not.
    */
  private def twoLaneRing(outerCars: Int, innerCars: Int, circumference: Length): RingScene =
    RingScene(
      TrackRoad
        .ring(
          circumference,
          List(outerCars, innerCars),
          ringSpeedLimit,
          ringSpeedLimit
        )
        .copy(
          laneChangeDuration = Some(SampleSceneCreation.LaneChangeDuration),
          laneChangeWarning = Some(SampleSceneCreation.LaneChangeWarning)
        ),
      Seconds(0),
      DT
    )

  /*
  Car counts on a 400m loop, with 8m cars that want 6m of room at a standstill. Past about
  28 cars there is no room left to want, and the whole ring locks solid and stays that way.
  These three sit either side of the interesting part: free flowing, dense enough to be
  fragile, and crawling.
   */
  val quietRing =
    NamedScene("ring road, 8 cars", ring(8, Meters(400)))

  val busyRing =
    NamedScene("ring road, 16 cars", ring(16, Meters(400)))

  val jammedRing =
    NamedScene("ring road, 22 cars", ring(22, Meters(400)))

  /*
  The inner lane is shorter, so the same car count is a higher density there. These pairs are
  picked by how full each lane actually is rather than by how the numbers look.
   */
  val quietTwoLaneRing =
    NamedScene("2 lanes, free flowing", twoLaneRing(9, 5, Meters(400)))

  /**
    * Dense enough that the traffic can't damp a disturbance out.
    *
    * Below about twenty cars in the outer lane a jolt spreads, fades and is gone inside a
    * minute. Above it the same jolt feeds itself: this is the density where one forced lane
    * change turns into stop-and-go traffic that is still going round the loop minutes later,
    * long after the car that caused it has driven out of its own wave.
    */
  val waveProneTwoLaneRing =
    NamedScene("2 lanes, standing waves", twoLaneRing(21, 13, Meters(400)))

  val lopsidedTwoLaneRing =
    NamedScene("2 lanes, all in the right one", twoLaneRing(18, 2, Meters(400)))

  /*
  The first road that isn't one straight or one closed loop: a single lane, grown the way the
  editor (phase J) eventually will, that leaves a straight approach, bends broadly left, and
  straightens out again before it runs out. It exists to answer one question the rings and the
  street never had to - does the network layer, the arc it draws and the traffic tick that
  moves cars across a seam actually look like a road once it's on the page - so it is built the
  same way `NetworkFixtures` builds every graph phase C and D are tested against: grown with
  `PathGrowth` so each piece starts exactly where the last one left off, and checked against
  `NetworkValidation` before it is trusted to a `NamedScene`.

  Three sections, one-way (W4), right-hand traffic:

    - "approach", a 100 m straight, so there is somewhere to watch a car hold its lane before
      the road asks anything of it;
    - "bend", a 350 m radius arc sweeping 30 degrees left (`PathGrowth.arcFrom`'s positive
      sweep) for about 183 m of curved pavement. 350 m is a highway radius, not a driveway
      one - at the 90 km/h posted limit below that is under 0.2 g of lateral acceleration,
      comfortably inside what a driver takes without noticing, which is what "broad" means
      here as opposed to a tight, hairpin turn;
    - "departure", another 100 m straight, so the bend has somewhere to unwind into rather than
      ending the road the moment it stops curving.

  Total road length is about 383 m - the "few hundred metres" the card asks for.

  Seven cars are seeded across all three sections rather than bunched onto the approach, so the
  bend already has traffic on it - the arc is the whole point of this scene - the moment the
  page loads rather than after however many ticks it takes the front of a queue to reach it.
  Within each section they sit roughly 45-60 m apart: at the 90 km/h limit an IDM commuter's
  own steady-state gap (minimum distance plus one second of following time, `Driver.commuter`)
  works out to a little over 30 m centre-to-centre once the 8 m car length is added back in, so
  this spacing reads as cars keeping a comfortable interval rather than as a clump released all
  at once. They start a little under the limit, at 80 km/h, so the first several ticks show
  them easing up to speed instead of sitting there already at it.

  Those seven, though, are a one-time seed rather than a supply: with nowhere for a car to
  arrive from, all seven drive off the departure end within about the road's own ~15 s crossing
  time (383 m at 90 km/h is Meters(383) / KilometersPerHour(90) =~ Seconds(15.3)) and the road
  sits empty ever after - correct, and exactly the unwatchable demo card E1 exists to fix. A
  `Source` on `approach` (card E1's own admit-or-queue boundary, `Boundary.tick`) keeps the
  seed from being the only traffic the scene ever has.

  The arrival rate is worked back from how many cars should be on the road at once rather than
  picked by feel: Little's law says the steady-state count on the road is (arrival rate) x
  (time spent on it), so wanting "a handful" - call it four or five cars, enough to watch
  without the road ever reading as jammed - at the ~15.3 s crossing time means a rate of
  roughly 4.5 / 15.3 s =~ 0.3 Hz, one car every ~3.3 s. That is comfortably below the rate a
  packed road could actually swallow: at the ~30 m steady-state centre-to-centre spacing noted
  above and the 90 km/h limit (25 m/s), cars can arrive as fast as one every 30 m / 25 m/s =
  1.2 s (~0.83 Hz) before a new one would have nowhere safe to enter - `Boundary.tick`'s own
  gap check (`Lookahead.leaderOf` within `minimumDistance`) would simply hold the excess in
  `queued` rather than jam the road, but 0.3 Hz never gets near testing that: it is chosen to
  keep the road busy, not to see it back up.
   */
  private val networkSpeedLimit: Velocity = KilometersPerHour(90)
  private val networkStartingSpeed: Velocity = KilometersPerHour(80)

  /**
    * One car every ~3.3 s, on average - see the comment above `buildSingleRoadNetwork` for the
    * Little's-law arithmetic (~4.5 cars x ~15.3 s crossing time) that this rate is worked back
    * from, and for why it sits well clear of the rate that would actually pack the road solid.
    */
  private val networkArrivalRate: squants.time.Frequency = Hertz(0.3)

  private def networkVehicleAt(s: Length): PilotedVehicle = {
    val spatial = Spatial.withVecs(QuantityVector[Distance](s, Meters(0), Meters(0)))
    PilotedVehicle.commuter2(spatial, PilotedVehicle.idm, spatial)
  }

  private def buildSingleRoadNetwork(): NetworkScene = {
    val east: DoubleVector = DoubleVector(1.0, 0.0, 0.0)
    val origin: QuantityVector[Distance] = QuantityVector[Distance](Meters(0), Meters(0), Meters(0))

    val approachLength = Meters(100)
    val bendRadius = Meters(350)
    val bendSweep = math.Pi / 6 // 30 degrees left
    val departureLength = Meters(100)

    val approachPath = PathGrowth.straightFrom(origin, east, approachLength)
    val bendPath =
      PathGrowth.arcFrom(PathGrowth.endPoint(approachPath), PathGrowth.endHeading(approachPath), bendRadius, bendSweep)
    val departurePath =
      PathGrowth.straightFrom(PathGrowth.endPoint(bendPath), PathGrowth.endHeading(bendPath), departureLength)

    val approachId = SectionId("approach")
    val bendId = SectionId("bend")
    val departureId = SectionId("departure")

    val laneWidth: Length = Meters(3.5)
    val sections = Map(
      approachId -> LaneSection(approachId, approachPath, laneWidth, networkSpeedLimit, layer = 0),
      bendId -> LaneSection(bendId, bendPath, laneWidth, networkSpeedLimit, layer = 0),
      departureId -> LaneSection(departureId, departurePath, laneWidth, networkSpeedLimit, layer = 0)
    )
    val movements = List(
      Movement.continuation(MovementId("approach-bend"), approachId, bendId),
      Movement.continuation(MovementId("bend-departure"), bendId, departureId)
    )

    val network = RoadNetwork(sections, movements)
    val faults = NetworkValidation.faults(network)
    require(faults.isEmpty, s"network demo scene produced a broken network: ${faults.map(_.message).mkString("; ")}")

    val index = NetworkIndex(network)

    val placements: List[(SectionId, Length)] = List(
      (approachId, Meters(15)),
      (approachId, Meters(60)),
      (bendId, Meters(20)),
      (bendId, Meters(70)),
      (bendId, Meters(120)),
      (departureId, Meters(10)),
      (departureId, Meters(55))
    )
    val vehicles = placements.map {
      case (section, s) =>
        NetworkVehicle.enteringAt(networkVehicleAt(s), section, s, networkStartingSpeed, index)
    }

    // A fixed seed rather than a wall-clock one, so the scene the page loads is the same
    // sequence of arrivals on every reload - the same determinism `BoundarySpec` relies on to
    // check a `Source`'s count against a Poisson expectation at all.
    val approachSource = Source(at = approachId, meanRate = networkArrivalRate, seed = 20260919L)

    NetworkScene(
      network,
      index,
      NetworkTraffic.of(vehicles),
      Seconds(0),
      DT,
      networkSpeedLimit,
      sources = List(approachSource)
    )
  }

  val singleRoadNetworkScene: NetworkScene = buildSingleRoadNetwork()

  val singleRoadNetwork: NamedScene =
    NamedScene("network, single road", singleRoadNetworkScene)
}

object SampleSceneCreation {

  /**
    * How long a car takes to cross the line, on screen.
    *
    * Instant changes are what the model does and are impossible to follow: a car is in one
    * lane, and on the next frame it is in the other, with nothing to tell you which car moved
    * or that anything moved at all. Sliding it across costs nothing in the physics - the
    * change has already happened as far as the other drivers are concerned - and buys the
    * thing the ring is for, which is being able to watch one.
    *
    * Kept shorter than MOBIL's cooldown so a car is always settled in its lane before it is
    * allowed to consider leaving it again.
    */
  val LaneChangeDuration: Time = Seconds(2)

  /**
    * How long a driver indicates before it starts to cross.
    *
    * Sliding a car across the line made a change followable once you had found it, which is
    * the harder half: on a ring of twenty cars, the change is over before you know which car
    * to watch, and watching all of them is not watching. This is the part that tells you
    * where to look, so it only has to be long enough to notice and turn towards - much longer
    * and the road is permanently full of cars indicating rather than doing anything.
    */
  val LaneChangeWarning: Time = Seconds(1.2)
}
