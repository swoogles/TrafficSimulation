package com.billding

import com.billding.network._
import com.billding.physics.{Path, PathGrowth, StraightPath}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import squants.motion.{Distance, KilometersPerHour}
import squants.space.{Length, Meters}
import squants.{DoubleVector, QuantityVector}

/**
  * H1: conflicts are a property of a pair of movements, computed from geometry, not
  * from the graph's endpoint connectivity. These fixtures are built locally rather
  * than added to `NetworkFixtures` (the card restricts this session to the two new
  * files); see the report for what a shared four-way-crossing fixture would look
  * like if it moved there later.
  *
  * None of these networks are run through `NetworkValidation` - `Conflicts` only
  * reads `RoadNetwork.sections`/`movements` and does its own sampling, so the
  * geometry here only needs to be right, not continuous the way a driveable network
  * would need to be.
  */
class ConflictsSpec extends AnyFlatSpec with Matchers {

  private val laneWidth: Length = Meters(3.5)
  private val speedLimit = KilometersPerHour(50)

  private def section(id: String, path: Path): LaneSection =
    LaneSection(SectionId(id), path, laneWidth, speedLimit, layer = 0)

  private val origin: QuantityVector[Distance] = QuantityVector[Distance](Meters(0), Meters(0), Meters(0))
  private val north: DoubleVector = DoubleVector(0.0, 1.0, 0.0)
  private val south: DoubleVector = DoubleVector(0.0, -1.0, 0.0)

  private def point(x: Double, y: Double): QuantityVector[Distance] =
    origin + QuantityVector[Distance](Meters(x), Meters(y), Meters(0))

  // ---------------------------------------------------------------------------------
  // A symmetric four-way crossing: two one-way lanes per axis (a two-way road is two
  // one-way sections side by side, per W4), each lane offset half a lane width to the
  // right of its direction of travel the way real right-hand-traffic lanes are. Arm
  // length 40 m, offset 1.75 m (half of `laneWidth`).
  //
  // Through movements only - straight lines, so whether they cross is checkable by
  // hand: the two north/south lanes sit on the vertical lines x = +-1.75, the two
  // east/west lanes on the horizontal lines y = +-1.75. Every north/south lane
  // crosses every east/west lane exactly once, near the centre; the two north/south
  // lanes never cross each other (parallel, offset apart), and likewise for the two
  // east/west lanes.
  // ---------------------------------------------------------------------------------
  private val offset = 1.75

  private def fourWayCrossing(): (RoadNetwork, Set[MovementId]) = {
    val southIn = section("south-in", StraightPath(point(offset, -40), point(offset, 0)))
    val northOut = section("north-out", StraightPath(point(offset, 0), point(offset, 40)))
    val northIn = section("north-in", StraightPath(point(-offset, 40), point(-offset, 0)))
    val southOut = section("south-out", StraightPath(point(-offset, 0), point(-offset, -40)))
    val eastIn = section("east-in", StraightPath(point(40, offset), point(0, offset)))
    val westOut = section("west-out", StraightPath(point(0, offset), point(-40, offset)))
    val westIn = section("west-in", StraightPath(point(-40, -offset), point(0, -offset)))
    val eastOut = section("east-out", StraightPath(point(0, -offset), point(40, -offset)))

    val northbound = Movement.continuation(MovementId("northbound"), SectionId("south-in"), SectionId("north-out"))
    val southbound = Movement.continuation(MovementId("southbound"), SectionId("north-in"), SectionId("south-out"))
    val westbound = Movement.continuation(MovementId("westbound"), SectionId("east-in"), SectionId("west-out"))
    val eastbound = Movement.continuation(MovementId("eastbound"), SectionId("west-in"), SectionId("east-out"))

    val network = RoadNetwork(
      sections = List(southIn, northOut, northIn, southOut, eastIn, westOut, westIn, eastOut)
        .map(s => s.id -> s)
        .toMap,
      movements = List(northbound, southbound, westbound, eastbound)
    )

    (network, Set(northbound.id, southbound.id, westbound.id, eastbound.id))
  }

  "a four-way crossing" should "report a conflict between every north/south lane and every east/west lane" in {
    val (network, junction) = fourWayCrossing()
    val found = Conflicts.conflicts(network, junction)

    val crossingPairs = Set(
      Set(MovementId("northbound"), MovementId("westbound")),
      Set(MovementId("northbound"), MovementId("eastbound")),
      Set(MovementId("southbound"), MovementId("westbound")),
      Set(MovementId("southbound"), MovementId("eastbound"))
    )

    found.map(c => Set(c.a, c.b)).toSet shouldBe crossingPairs
  }

  it should "not conflict between the two parallel lanes of the same axis" in {
    val (network, junction) = fourWayCrossing()
    val found = Conflicts.conflicts(network, junction)

    found.map(c => Set(c.a, c.b)) should not contain Set(MovementId("northbound"), MovementId("southbound"))
    found.map(c => Set(c.a, c.b)) should not contain Set(MovementId("westbound"), MovementId("eastbound"))
  }

  it should "place each crossing's sA/sB near where the lanes actually meet" in {
    val (network, junction) = fourWayCrossing()
    val found = Conflicts.conflicts(network, junction)

    // northbound (x = 1.75) crosses westbound (y = 1.75) at (1.75, 1.75): 41.75 m
    // into northbound's curve (40 m of south-in plus 1.75 m of north-out) and
    // 38.25 m into westbound's (40 m of east-in minus 1.75 m).
    val northVsWest = found.find(c => Set(c.a, c.b) == Set(MovementId("northbound"), MovementId("westbound"))).get
    val (sOnNorthbound, sOnWestbound) =
      if (northVsWest.a == MovementId("northbound")) (northVsWest.sA, northVsWest.sB) else (northVsWest.sB, northVsWest.sA)

    sOnNorthbound.toMeters shouldBe 41.75 +- 0.3
    sOnWestbound.toMeters shouldBe 38.25 +- 0.3
  }

  // ---------------------------------------------------------------------------------
  // Two right turns off opposite approaches (northbound turning toward east, southbound
  // turning toward west - the mirror image) land in diagonally opposite quadrants and
  // never come near each other.
  // ---------------------------------------------------------------------------------
  "two right turns from opposite approaches" should "not conflict" in {
    val southIn = section("south-in", StraightPath(point(offset, -40), point(offset, 0)))
    val northIn = section("north-in", StraightPath(point(-offset, 40), point(-offset, 0)))

    val turnRadius = Meters(8)
    val southRightTurn = section("south-right-turn", PathGrowth.arcFrom(point(offset, 0), north, turnRadius, -math.Pi / 2))
    val northRightTurn = section("north-right-turn", PathGrowth.arcFrom(point(-offset, 0), south, turnRadius, -math.Pi / 2))

    val fromSouth = Movement(
      MovementId("south-right"),
      SectionId("south-in"),
      SectionId("south-right-turn"),
      MovementKind.Turn,
      Control.Uncontrolled
    )
    val fromNorth = Movement(
      MovementId("north-right"),
      SectionId("north-in"),
      SectionId("north-right-turn"),
      MovementKind.Turn,
      Control.Uncontrolled
    )

    val network = RoadNetwork(
      sections = List(southIn, northIn, southRightTurn, northRightTurn).map(s => s.id -> s).toMap,
      movements = List(fromSouth, fromNorth)
    )

    Conflicts.conflicts(network, Set(fromSouth.id, fromNorth.id)) shouldBe Nil
  }

  // ---------------------------------------------------------------------------------
  // NetworkFixtures.tJunction: through road major-west -> major-east, a right turn off
  // it (major-west -> minor-exit), and a stop-controlled right turn onto it
  // (minor-approach -> major-east). Read fully in NetworkFixtures.scala.
  // ---------------------------------------------------------------------------------
  "a T-junction" should "not flag the through movement against the turn that shares its start" in {
    val (network, _) = NetworkFixtures.tJunction(Meters(20))
    val junction = Set(
      MovementId("major-west-major-east"),
      MovementId("major-west-minor-exit"),
      MovementId("minor-approach-major-east")
    )

    val found = Conflicts.conflicts(network, junction)

    // Both share `from` = major-west: an ordinary diverge, not a crossing.
    found.map(c => Set(c.a, c.b)) should not contain
      Set(MovementId("major-west-major-east"), MovementId("major-west-minor-exit"))
  }

  it should "not flag the through movement against the turn that shares its end" in {
    val (network, _) = NetworkFixtures.tJunction(Meters(20))
    val junction = Set(
      MovementId("major-west-major-east"),
      MovementId("major-west-minor-exit"),
      MovementId("minor-approach-major-east")
    )

    val found = Conflicts.conflicts(network, junction)

    // Both share `to` = major-east: an ordinary merge, not a crossing.
    found.map(c => Set(c.a, c.b)) should not contain
      Set(MovementId("major-west-major-east"), MovementId("minor-approach-major-east"))
  }

  it should "flag the two turns that share only the junction corner itself" in {
    val (network, _) = NetworkFixtures.tJunction(Meters(20))
    val junction = Set(
      MovementId("major-west-major-east"),
      MovementId("major-west-minor-exit"),
      MovementId("minor-approach-major-east")
    )

    val found = Conflicts.conflicts(network, junction)

    // major-west-minor-exit and minor-approach-major-east share neither endpoint, but
    // both pass through the physical junction corner (the point where major-west ends
    // and minor-exit begins is the same point where minor-approach ends and major-east
    // begins) - a right turn off the major road and a right turn onto it sweep through
    // the same patch of pavement, so this is a real conflict, at the seam in the middle
    // of each movement's own from+to curve.
    val turnVsTurn =
      found.find(c => Set(c.a, c.b) == Set(MovementId("major-west-minor-exit"), MovementId("minor-approach-major-east")))
    turnVsTurn shouldBe defined
    turnVsTurn.get.sA.toMeters shouldBe 20.0 +- 0.3
    turnVsTurn.get.sB.toMeters shouldBe 20.0 +- 0.3
  }

  // ---------------------------------------------------------------------------------
  // Ordering and robustness
  // ---------------------------------------------------------------------------------
  "conflicts" should "come back in the network's declared movement order regardless of the input set's order" in {
    val (network, junction) = fourWayCrossing()

    val declaredOrder = network.movements.map(_.id)
    val forward = Conflicts.conflicts(network, junction)
    val reversedInput = Conflicts.conflicts(network, junction.toList.reverse.toSet)

    forward shouldBe reversedInput

    forward.foreach { c =>
      declaredOrder.indexOf(c.a) should be < declaredOrder.indexOf(c.b)
    }
  }

  it should "ignore a movement whose section is missing from the network, rather than fail" in {
    val (network, _) = NetworkFixtures.tJunction(Meters(20))
    val danglingMovement =
      Movement.continuation(MovementId("dangling"), SectionId("major-west"), SectionId("does-not-exist"))
    val withDangling = network.copy(movements = danglingMovement :: network.movements)
    val junction = Set(MovementId("dangling"), MovementId("minor-approach-major-east"))

    noException should be thrownBy Conflicts.conflicts(withDangling, junction)
    Conflicts.conflicts(withDangling, junction) shouldBe Nil
  }

  it should "return nothing for an empty or single-movement junction" in {
    val (network, _) = NetworkFixtures.tJunction(Meters(20))

    Conflicts.conflicts(network, Set.empty) shouldBe Nil
    Conflicts.conflicts(network, Set(MovementId("major-west-major-east"))) shouldBe Nil
  }
}
