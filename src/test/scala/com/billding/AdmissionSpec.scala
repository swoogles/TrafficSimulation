package com.billding

import java.util.UUID

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import squants.motion.{Distance, MetersPerSecond}
import squants.space.{Length, Meters}
import squants.{QuantityVector, Time, Velocity}
import squants.time.Seconds

import com.billding.network._
import com.billding.physics.Spatial
import com.billding.traffic.{IntelligentDriverModelImpl, PilotedVehicle}

/**
  * H2: a vehicle approaching a `Stop` movement must come to a full stop at the line before
  * it is eligible; a `Yield` vehicle need not stop but may enter only when no conflicting
  * movement has a vehicle closer to the shared [[ConflictPoint]]; both additionally need
  * downstream clearance; and none of this may ever let the junction lock up.
  *
  * Fixtures built by hand here, the same way `NetworkTickSpec`/`LookaheadSpec`/
  * `SeamInvarianceSpec` do it - `NetworkFixtures.tJunction` and the newly-promoted
  * `NetworkFixtures.fourWayCrossing` supply the geometry, this file supplies the traffic.
  */
class AdmissionSpec extends AnyFlatSpec with Matchers {

  private val idm = new IntelligentDriverModelImpl
  private val dt: Time = Seconds(0.1) // W8's fixed-timestep working default.

  /** Same construction every other network spec uses for a test car - position isn't read by tick physics. */
  private def commuterAt(s: Length): PilotedVehicle = {
    val spatial = Spatial.withVecs(QuantityVector[Distance](s, Meters(0), Meters(0)))
    PilotedVehicle.commuter2(spatial, idm, spatial)
  }

  private def place(section: SectionId, s: Length, speed: Velocity, index: NetworkIndex): NetworkVehicle =
    NetworkVehicle.enteringAt(commuterAt(s), section, s, speed, index)

  private def removeVehicle(traffic: NetworkTraffic, uuid: UUID): NetworkTraffic =
    NetworkTraffic(traffic.bySection.view.mapValues(_.filterNot(_.piloted.uuid == uuid)).toMap)

  private def worldPositionOf(vehicle: NetworkVehicle, index: NetworkIndex): QuantityVector[Distance] =
    index.network.section(vehicle.section).get.path.pointAt(vehicle.s)

  private val nearlyStopped: Velocity = MetersPerSecond(0.2)

  // ===================================================================================
  // "a stop-controlled vehicle stops fully before proceeding"
  // ===================================================================================
  "a stop-controlled vehicle" should "come to a full stop at the line before proceeding" in {
    val (_, index) = NetworkFixtures.tJunction(Meters(60))
    val vehicle = place(SectionId("minor-approach"), Meters(0), MetersPerSecond(10), index)
    val uuid = vehicle.piloted.uuid

    var traffic = NetworkTraffic.of(List(vehicle))
    var firstStopped: Option[Int] = None
    var firstCrossed: Option[Int] = None

    for (tick <- 1 to 800) {
      traffic = NetworkTick.advance(traffic, index, dt).traffic
      traffic.vehicleWith(uuid).foreach { v =>
        if (firstStopped.isEmpty && v.section == SectionId("minor-approach") && v.speed <= nearlyStopped)
          firstStopped = Some(tick)
        if (firstCrossed.isEmpty && v.section == SectionId("major-east"))
          firstCrossed = Some(tick)
      }
    }

    withClue("the vehicle never came to a full stop at the line - ") {
      firstStopped shouldBe defined
    }
    withClue("the vehicle never proceeded across the junction - ") {
      firstCrossed shouldBe defined
    }
    withClue("it crossed before it had ever fully stopped - ") {
      firstCrossed.get should be > firstStopped.get
    }
  }

  // ===================================================================================
  // "a yielding vehicle waits out a stream and then goes" - and never crosses paths with it.
  // ===================================================================================
  "a yielding vehicle" should "wait out a stream of conflicting traffic and then go, never sharing the crossing with it" in {
    val armLength = Meters(70)
    val (_, index) =
      NetworkFixtures.fourWayCrossing(armLength, Map(MovementId("northbound") -> Control.Yield))

    val yieldVehicle = place(SectionId("south-in"), Meters(5), MetersPerSecond(8), index)
    val yieldUuid = yieldVehicle.piloted.uuid

    // A tight platoon on the crossing, uncontrolled road: five cars 12 m apart, so there is
    // always another one closing on the conflict point before the last one has cleared it.
    val streamVehicles = (0 until 5).toList.map(i => place(SectionId("west-in"), Meters(5) + Meters(12) * i.toDouble, MetersPerSecond(6), index))
    val streamUuids = streamVehicles.map(_.piloted.uuid).toSet

    var traffic = NetworkTraffic.of(yieldVehicle :: streamVehicles)
    val departedUuids = scala.collection.mutable.Set.empty[UUID]
    var closestApproach = Meters(1e9)

    for (_ <- 1 to 1200) {
      val result = NetworkTick.advance(traffic, index, dt)
      traffic = result.traffic
      result.departures.foreach(v => departedUuids += v.piloted.uuid)

      traffic.vehicleWith(yieldUuid).foreach { yv =>
        val yPos = worldPositionOf(yv, index)
        streamUuids.foreach { su =>
          traffic.vehicleWith(su).foreach { sv =>
            val sep = (yPos - worldPositionOf(sv, index)).magnitude
            if (sep < closestApproach) closestApproach = sep
          }
        }
      }
    }

    withClue(s"the yielding vehicle came within ${closestApproach.toMeters} m of a stream vehicle at the crossing - ") {
      closestApproach.toMeters should be > 3.0
    }
    withClue("the yielding vehicle never made it through the junction - ") {
      (departedUuids.contains(yieldUuid) || traffic.vehicleWith(yieldUuid).exists(_.section == SectionId("north-out"))) shouldBe true
    }
  }

  // ===================================================================================
  // "two simultaneous conflicting arrivals resolve one at a time in a repeatable order"
  // ===================================================================================
  "two vehicles arriving at a conflict point at exactly the same distance" should
    "resolve to the same winner every time, by nearest-to-the-point then SectionId order" in {

    val armLength = Meters(70)
    val (_, index) = NetworkFixtures.fourWayCrossing(
      armLength,
      Map(MovementId("northbound") -> Control.Yield, MovementId("eastbound") -> Control.Yield)
    )

    // Both vehicles placed exactly 20 m short of their own line - a genuine tie in the
    // distance the card's tie-break rule cares about. (Distance to *the conflict point*
    // itself would not be a fair tie here: `fourWayCrossing`'s offset lanes put each
    // movement's two conflicts at different offsets from its own line, so a north/east
    // pair that is truly, physically equidistant from the box reads as unequal if measured
    // that way - see the long comment on `Admission.nearestContender` for why this class
    // instead compares distance to each vehicle's own line.)
    val northStart = armLength - Meters(20)
    val eastStart = armLength - Meters(20)

    def runOnce(): (Option[Int], Option[Int]) = {
      val northVehicle = place(SectionId("south-in"), northStart, MetersPerSecond(8), index)
      val eastVehicle = place(SectionId("west-in"), eastStart, MetersPerSecond(8), index)
      var traffic = NetworkTraffic.of(List(northVehicle, eastVehicle))
      var northCrossedAt: Option[Int] = None
      var eastCrossedAt: Option[Int] = None

      for (tick <- 1 to 800) {
        traffic = NetworkTick.advance(traffic, index, dt).traffic
        if (northCrossedAt.isEmpty && traffic.vehicleWith(northVehicle.piloted.uuid).exists(_.section == SectionId("north-out")))
          northCrossedAt = Some(tick)
        if (eastCrossedAt.isEmpty && traffic.vehicleWith(eastVehicle.piloted.uuid).exists(_.section == SectionId("east-out")))
          eastCrossedAt = Some(tick)
      }
      (northCrossedAt, eastCrossedAt)
    }

    val (northFirst1, eastFirst1) = runOnce()
    val (northFirst2, eastFirst2) = runOnce()

    // `"south-in" < "west-in"` (SectionId order, `s` before `w`) is the tie-break the card
    // asks for, so northbound (from `south-in`) is the deterministic winner of an exact tie.
    withClue("northbound did not win the tie on the first run - ") { northFirst1 shouldBe defined }
    withClue("eastbound never got its turn on the first run - ") { eastFirst1 shouldBe defined }
    northFirst1.get should be < eastFirst1.get

    withClue("the outcome was not repeatable on a second, independent run - ") {
      (northFirst2, eastFirst2) shouldBe (northFirst1, eastFirst1)
    }
  }

  // ===================================================================================
  // "a vehicle does not enter when the far side is full"
  // ===================================================================================
  "a vehicle with an otherwise-clear approach" should "not enter a movement whose far side has no room" in {
    val (_, index) = NetworkFixtures.tJunction(Meters(60))

    val blocker = place(SectionId("major-east"), Meters(5), MetersPerSecond(0), index)
    val blockerUuid = blocker.piloted.uuid
    val approaching = place(SectionId("minor-approach"), Meters(0), MetersPerSecond(8), index)
    val approachingUuid = approaching.piloted.uuid

    def pinBlocker(traffic: NetworkTraffic): NetworkTraffic = {
      val current = traffic.vehicleWith(blockerUuid).get
      NetworkTraffic.place(traffic, current.copy(section = SectionId("major-east"), s = Meters(5), speed = MetersPerSecond(0)))
    }

    var traffic = NetworkTraffic.of(List(blocker, approaching))

    // Phase 1: the far side stays occupied the whole time (re-pinned after every real
    // physics tick, the same perturbation technique `SeamInvarianceSpec` uses for its
    // braking wave) - the approaching vehicle must stop and stay stopped.
    for (_ <- 1 to 400) {
      traffic = pinBlocker(NetworkTick.advance(traffic, index, dt).traffic)
    }

    withClue("the vehicle crossed into a movement whose far side was occupied - ") {
      traffic.vehicleWith(approachingUuid).map(_.section) shouldBe Some(SectionId("minor-approach"))
    }
    withClue("the vehicle never actually stopped for the blocked far side - ") {
      traffic.vehicleWith(approachingUuid).map(_.speed.toMetersPerSecond).get should be < nearlyStopped.toMetersPerSecond
    }

    // Phase 2: the far side clears - the same vehicle, still fully stopped at the line
    // with a clear conflict picture, now has to actually go.
    traffic = removeVehicle(traffic, blockerUuid)
    var crossedAfterClearing = false
    for (_ <- 1 to 400) {
      traffic = NetworkTick.advance(traffic, index, dt).traffic
      if (traffic.vehicleWith(approachingUuid).exists(_.section == SectionId("major-east"))) crossedAfterClearing = true
    }

    withClue("the vehicle never proceeded once the far side cleared - ") {
      crossedAfterClearing shouldBe true
    }
  }

  // ===================================================================================
  // "the junction does not deadlock under load from all approaches" - the card's own
  // design target: an all-way stop, two queued vehicles on every approach at once.
  // ===================================================================================
  "a four-way, all-way-stop crossing" should "not deadlock under load from every approach at once" in {
    val armLength = Meters(60)
    val allStop = List("northbound", "southbound", "westbound", "eastbound").map(n => MovementId(n) -> Control.Stop).toMap
    val (_, index) = NetworkFixtures.fourWayCrossing(armLength, allStop)

    val approaches = List(SectionId("south-in"), SectionId("north-in"), SectionId("east-in"), SectionId("west-in"))
    val initialVehicles = approaches.flatMap { section =>
      List(place(section, Meters(10), MetersPerSecond(6), index), place(section, Meters(30), MetersPerSecond(6), index))
    }
    val allUuids = initialVehicles.map(_.piloted.uuid).toSet

    var traffic = NetworkTraffic.of(initialVehicles)
    val departed = scala.collection.mutable.Set.empty[UUID]

    val maxTicks = 3500
    var tick = 0
    while (tick < maxTicks && departed.size < allUuids.size) {
      val result = NetworkTick.advance(traffic, index, dt)
      traffic = result.traffic
      result.departures.foreach(v => departed += v.piloted.uuid)
      tick += 1
    }

    withClue(s"only ${departed.size} of ${allUuids.size} vehicles ever made it through - the junction stalled - ") {
      departed shouldBe allUuids
    }
  }
}
