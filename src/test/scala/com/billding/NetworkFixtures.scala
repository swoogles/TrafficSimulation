package com.billding

import com.billding.network._
import com.billding.physics.{Path, PathGrowth, StraightPath}
import squants.motion.{Distance, KilometersPerHour}
import squants.space.{Length, Meters}
import squants.{DoubleVector, QuantityVector}

/**
  * Hand-built networks for every card from C onward to test against, so no
  * later card has to spell out coordinates.
  *
  * Every builder here:
  *
  *   - grows its geometry with [[PathGrowth]], so continuity is a consequence of
  *     construction rather than something asserted afterwards;
  *   - names every section and movement with a fixed, documented id, because a
  *     test written against `SectionId("mainline-1")` needs that name to keep
  *     meaning the same section a year from now;
  *   - asserts `NetworkValidation.faults` is empty before returning. A broken
  *     fixture makes a later card's test fail for the wrong reason, which costs
  *     more than this check.
  *
  * Working defaults from `IMPLEMENTATION_TASKS.md` apply throughout: every
  * section is one-way (W4), right-hand traffic, so lane index 0 is always the
  * rightmost lane and increasing lane index moves left.
  */
object NetworkFixtures {

  private val laneWidth: Length = Meters(3.5)
  private val speedLimit = KilometersPerHour(80)

  private val origin: QuantityVector[Distance] = QuantityVector[Distance](Meters(0), Meters(0), Meters(0))
  private val east: DoubleVector = DoubleVector(1.0, 0.0, 0.0)
  private val north: DoubleVector = DoubleVector(0.0, 1.0, 0.0)

  private def section(id: SectionId, path: Path, width: Length = laneWidth, layer: Int = 0): LaneSection =
    LaneSection(id, path, width, speedLimit, layer)

  /** Builds the network, checks it against [[NetworkValidation]], and pairs it with its index. */
  private def assemble(
    sections: Seq[LaneSection],
    movements: List[Movement],
    laneNeighbours: List[LaneNeighbour] = Nil
  ): (RoadNetwork, NetworkIndex) = {
    val network = RoadNetwork(sections.map(s => s.id -> s).toMap, movements, laneNeighbours)
    val faults = NetworkValidation.faults(network)
    require(faults.isEmpty, s"NetworkFixtures produced a broken network: ${faults.map(_.message).mkString("; ")}")
    (network, NetworkIndex(network))
  }

  /**
    * `count` sections in a straight line, each `each` long, joined end to end by
    * uncontrolled continuations - the plainest possible network, for cards that
    * need seams without needing anything interesting to happen at them.
    *
    * With `lanes == 1` (the default), sections are named `SectionId("mainline-0")`,
    * `SectionId("mainline-1")`, ... and the movement joining section `i` to
    * section `i + 1` is `MovementId("mainline-i-(i+1)")`, e.g. `"mainline-0-1"`.
    *
    * With `lanes > 1`, each position in the chain gets `lanes` parallel sections
    * side by side, `SectionId("mainline-<index>-lane-<lane>")` (lane 0 is the
    * rightmost, right-hand traffic per W4), each lane continues into the next
    * segment's own lane (`MovementId("mainline-<index>-<index+1>-lane-<lane>")`),
    * and every pair of physically adjacent lanes at the same segment gets a
    * symmetric pair of [[LaneNeighbour]] entries spanning that segment's whole
    * length.
    */
  def straightChain(count: Int, each: Length, lanes: Int = 1): (RoadNetwork, NetworkIndex) = {
    require(count >= 1, "a chain needs at least one section")
    require(lanes >= 1, "a chain needs at least one lane")

    def sectionId(index: Int, lane: Int): SectionId =
      if (lanes == 1) SectionId(s"mainline-$index") else SectionId(s"mainline-$index-lane-$lane")

    def movementId(index: Int, lane: Int): MovementId =
      if (lanes == 1) MovementId(s"mainline-$index-${index + 1}")
      else MovementId(s"mainline-$index-${index + 1}-lane-$lane")

    def laneChain(lane: Int): IndexedSeq[LaneSection] = {
      val laneStart = origin + QuantityVector[Distance](Meters(0), laneWidth * lane.toDouble, Meters(0))
      val paths = (0 until count).foldLeft((laneStart, Vector.empty[Path])) {
        case ((point, builtPaths), _) =>
          val path = PathGrowth.straightFrom(point, east, each)
          (PathGrowth.endPoint(path), builtPaths :+ path)
      }._2
      paths.zipWithIndex.map { case (path, index) => section(sectionId(index, lane), path) }
    }

    val sections: IndexedSeq[LaneSection] = (0 until lanes).flatMap(laneChain)

    val movements: List[Movement] = (for {
      lane <- 0 until lanes
      index <- 0 until (count - 1)
    } yield Movement.continuation(movementId(index, lane), sectionId(index, lane), sectionId(index + 1, lane))).toList

    val laneNeighbours: List[LaneNeighbour] = (for {
      lane <- 0 until (lanes - 1)
      index <- 0 until count
    } yield {
      val here = sectionId(index, lane)
      val there = sectionId(index, lane + 1)
      List(
        LaneNeighbour(here, there, Side.LeftOf, Meters(0), each, Meters(0)),
        LaneNeighbour(there, here, Side.RightOf, Meters(0), each, Meters(0))
      )
    }).toList.flatten

    assemble(sections, movements, laneNeighbours)
  }

  /**
    * A trunk that diverges into two branches - the shape `movementsFrom` on a
    * split answers with more than one movement.
    *
    * Sections: `SectionId("trunk")` (straight, `trunkLength` long), then
    * `SectionId("branch-left")` and `SectionId("branch-right")` (each an arc
    * `branchLength` long, sweeping 30 degrees left and right respectively, so the
    * two branches visibly diverge rather than overlapping). Movements:
    * `MovementId("trunk-branch-left")` and `MovementId("trunk-branch-right")`,
    * both `Diverge`/`Uncontrolled` - a driver's branch choice is not a
    * right-of-way question, per W4/B2's rule that ordinary continuation never
    * costs a stop.
    */
  def ySplit(trunkLength: Length, branchLength: Length): (RoadNetwork, NetworkIndex) = {
    val trunkPath = PathGrowth.straightFrom(origin, east, trunkLength)
    val trunkEnd = PathGrowth.endPoint(trunkPath)
    val trunkHeading = PathGrowth.endHeading(trunkPath)

    // 30 degrees each way: enough that the two branches read as a real split
    // rather than overlapping lines.
    val divergence = math.Pi / 6
    val branchRadius = branchLength / divergence

    val leftPath = PathGrowth.arcFrom(trunkEnd, trunkHeading, branchRadius, divergence)
    val rightPath = PathGrowth.arcFrom(trunkEnd, trunkHeading, branchRadius, -divergence)

    val trunk = section(SectionId("trunk"), trunkPath)
    val branchLeft = section(SectionId("branch-left"), leftPath)
    val branchRight = section(SectionId("branch-right"), rightPath)

    val movements = List(
      Movement(
        MovementId("trunk-branch-left"),
        SectionId("trunk"),
        SectionId("branch-left"),
        MovementKind.Diverge,
        Control.Uncontrolled
      ),
      Movement(
        MovementId("trunk-branch-right"),
        SectionId("trunk"),
        SectionId("branch-right"),
        MovementKind.Diverge,
        Control.Uncontrolled
      )
    )

    assemble(List(trunk, branchLeft, branchRight), movements)
  }

  /**
    * A mainline with an on-ramp's acceleration lane running alongside it -
    * approach, acceleration lane, merge region (creativity_plan.md, "Merges and
    * splits: behavior, not just connectors"), not two lines meeting at an
    * endpoint.
    *
    * Sections: `SectionId("mainline-approach")` (straight, `mainlineLength`
    * long, the stretch before the ramp joins) continuing into
    * `SectionId("mainline-accel")` (straight, `accelLength` long - the merge
    * region proper). Separately, `SectionId("ramp-approach")` (straight,
    * `accelLength` long, off the mainline's own line - its exact coordinates
    * don't matter, only the logical mapping below does) continuing into
    * `SectionId("ramp-accel")` (straight, `accelLength` long). Movements:
    * `MovementId("mainline-approach-accel")` and
    * `MovementId("ramp-approach-accel")`, both ordinary uncontrolled
    * continuations.
    *
    * `ramp-accel` has no movement into the mainline at all: the merge itself is
    * a mandatory lane change across the [[LaneNeighbour]] window below, not an
    * endpoint connection, and `ramp-accel`'s dangling end is the pavement
    * actually running out (an implicit sink per W5) if a driver never takes it.
    * That window - `mainline-accel` paired with `ramp-accel` over their whole
    * shared length, both directions - is carried here because it is the whole
    * point of the fixture: card G2 leans on it for merge behaviour.
    */
  def rampMerge(mainlineLength: Length, accelLength: Length): (RoadNetwork, NetworkIndex) = {
    val mainlineApproachPath = PathGrowth.straightFrom(origin, east, mainlineLength)
    val mainlineAccelPath = PathGrowth.straightFrom(PathGrowth.endPoint(mainlineApproachPath), east, accelLength)

    val rampApproachStart = origin + QuantityVector[Distance](Meters(0), Meters(-20), Meters(0))
    val rampApproachPath = PathGrowth.straightFrom(rampApproachStart, east, accelLength)
    val rampAccelPath = PathGrowth.straightFrom(PathGrowth.endPoint(rampApproachPath), east, accelLength)

    val mainlineApproach = section(SectionId("mainline-approach"), mainlineApproachPath)
    val mainlineAccel = section(SectionId("mainline-accel"), mainlineAccelPath)
    val rampApproach = section(SectionId("ramp-approach"), rampApproachPath)
    val rampAccel = section(SectionId("ramp-accel"), rampAccelPath)

    val movements = List(
      Movement.continuation(
        MovementId("mainline-approach-accel"),
        SectionId("mainline-approach"),
        SectionId("mainline-accel")
      ),
      Movement.continuation(MovementId("ramp-approach-accel"), SectionId("ramp-approach"), SectionId("ramp-accel"))
    )

    val laneNeighbours = List(
      LaneNeighbour(SectionId("mainline-accel"), SectionId("ramp-accel"), Side.RightOf, Meters(0), accelLength, Meters(0)),
      LaneNeighbour(SectionId("ramp-accel"), SectionId("mainline-accel"), Side.LeftOf, Meters(0), accelLength, Meters(0))
    )

    assemble(List(mainlineApproach, mainlineAccel, rampApproach, rampAccel), movements, laneNeighbours)
  }

  /**
    * A right-angle junction: a through road with a minor stub joining it, stop
    * control on the minor approach, legal turns off the major road.
    *
    * Sections, each `armLength` long: `SectionId("major-west")` (straight,
    * arriving at the junction heading east) and `SectionId("major-east")`
    * (straight, leaving the junction heading east) form the through road.
    * `SectionId("minor-exit")` is a quarter-turn arc leaving the junction to the
    * south (a right turn off the major road), and `SectionId("minor-approach")`
    * is a quarter-turn arc arriving at the junction heading east from the south
    * (a right turn onto the major road). Both turns are built as a single arc
    * exactly `armLength` long so the turning geometry itself performs the
    * heading change; the movement it feeds is then just as continuous as an
    * ordinary seam, and only `kind`/`control` mark it as a turn.
    *
    * Movements: `MovementId("major-west-major-east")` (`Continuation`,
    * `Uncontrolled` - the through movement never stops for a turn),
    * `MovementId("major-west-minor-exit")` (`Turn`, `Uncontrolled` - turning off
    * the priority road), and `MovementId("minor-approach-major-east")` (`Turn`,
    * `Stop` - the minor road is the one that must stop before joining the
    * major road).
    */
  def tJunction(armLength: Length): (RoadNetwork, NetworkIndex) = {
    val majorWestPath = PathGrowth.straightFrom(origin, east, armLength)
    val junctionPoint = PathGrowth.endPoint(majorWestPath)

    val majorEastPath = PathGrowth.straightFrom(junctionPoint, east, armLength)

    // A quarter turn exactly `armLength` long.
    val turnRadius = armLength / (math.Pi / 2)

    // Right turn off the major road onto the minor stub: starts at the junction
    // heading east (matching major-west's end heading), sweeps clockwise to end
    // heading south.
    val minorExitPath = PathGrowth.arcFrom(junctionPoint, east, turnRadius, -math.Pi / 2)

    // Right turn from the minor stub onto the major road, built backwards from
    // the same quarter-turn shape so it lands exactly on the junction point
    // heading east.
    val minorApproachStart =
      junctionPoint + QuantityVector[Distance](Meters(0) - turnRadius, Meters(0) - turnRadius, Meters(0))
    val minorApproachPath = PathGrowth.arcFrom(minorApproachStart, north, turnRadius, -math.Pi / 2)

    val majorWest = section(SectionId("major-west"), majorWestPath)
    val majorEast = section(SectionId("major-east"), majorEastPath)
    val minorExit = section(SectionId("minor-exit"), minorExitPath)
    val minorApproach = section(SectionId("minor-approach"), minorApproachPath)

    val movements = List(
      Movement.continuation(MovementId("major-west-major-east"), SectionId("major-west"), SectionId("major-east")),
      Movement(
        MovementId("major-west-minor-exit"),
        SectionId("major-west"),
        SectionId("minor-exit"),
        MovementKind.Turn,
        Control.Uncontrolled
      ),
      Movement(
        MovementId("minor-approach-major-east"),
        SectionId("minor-approach"),
        SectionId("major-east"),
        MovementKind.Turn,
        Control.Stop
      )
    )

    assemble(List(majorWest, majorEast, minorExit, minorApproach), movements)
  }

  /**
    * A symmetric four-way crossing: two one-way lanes per axis (a two-way road is two
    * one-way sections side by side, per W4), each lane offset half a lane width to the
    * right of its direction of travel the way real right-hand-traffic lanes are.
    *
    * Sections, each `armLength` long: `SectionId("south-in")`/`SectionId("north-out")` (the
    * northbound through movement), `SectionId("north-in")`/`SectionId("south-out")`
    * (southbound), `SectionId("east-in")`/`SectionId("west-out")` (westbound), and
    * `SectionId("west-in")`/`SectionId("east-out")` (eastbound). Movements:
    * `MovementId("northbound")`, `MovementId("southbound")`, `MovementId("westbound")` and
    * `MovementId("eastbound")`, every one a `Continuation`. The north/south lanes sit on the
    * vertical lines `x = +-laneWidth/2`, the east/west lanes on the horizontal lines
    * `y = +-laneWidth/2`, so every north/south lane crosses every east/west lane exactly
    * once near the centre; the two north/south lanes never cross each other (parallel,
    * offset apart), and likewise for the two east/west lanes - `Conflicts.conflicts` reports
    * exactly those four crossings and nothing else (H1's `ConflictsSpec`).
    *
    * `controlOverrides` assigns `Control` per movement (by [[MovementId]]), defaulting every
    * movement not named to [[Control.Uncontrolled]] - `H1`'s original private fixture (an
    * all-through, all-uncontrolled crossing used only to test conflict geometry) is exactly
    * `fourWayCrossing(armLength)` with no overrides. `H2` (`AdmissionSpec`) passes `Stop` or
    * `Yield` for whichever approaches its scenario needs controlled, e.g.
    * `Map(MovementId("northbound") -> Control.Yield)` for a single yielding approach, or
    * every one of the four for an all-way stop.
    */
  def fourWayCrossing(armLength: Length, controlOverrides: Map[MovementId, Control] = Map.empty): (RoadNetwork, NetworkIndex) = {
    val offset = laneWidth / 2.0
    val negOffset = Meters(0) - offset
    val negArmLength = Meters(0) - armLength

    def point(x: Length, y: Length): QuantityVector[Distance] =
      origin + QuantityVector[Distance](x, y, Meters(0))

    val southIn = section(SectionId("south-in"), StraightPath(point(offset, negArmLength), point(offset, Meters(0))))
    val northOut = section(SectionId("north-out"), StraightPath(point(offset, Meters(0)), point(offset, armLength)))
    val northIn = section(SectionId("north-in"), StraightPath(point(negOffset, armLength), point(negOffset, Meters(0))))
    val southOut = section(SectionId("south-out"), StraightPath(point(negOffset, Meters(0)), point(negOffset, negArmLength)))
    val eastIn = section(SectionId("east-in"), StraightPath(point(armLength, offset), point(Meters(0), offset)))
    val westOut = section(SectionId("west-out"), StraightPath(point(Meters(0), offset), point(negArmLength, offset)))
    val westIn = section(SectionId("west-in"), StraightPath(point(negArmLength, negOffset), point(Meters(0), negOffset)))
    val eastOut = section(SectionId("east-out"), StraightPath(point(Meters(0), negOffset), point(armLength, negOffset)))

    def movement(id: MovementId, from: SectionId, to: SectionId): Movement =
      Movement(id, from, to, MovementKind.Continuation, controlOverrides.getOrElse(id, Control.Uncontrolled))

    val northbound = movement(MovementId("northbound"), SectionId("south-in"), SectionId("north-out"))
    val southbound = movement(MovementId("southbound"), SectionId("north-in"), SectionId("south-out"))
    val westbound = movement(MovementId("westbound"), SectionId("east-in"), SectionId("west-out"))
    val eastbound = movement(MovementId("eastbound"), SectionId("west-in"), SectionId("east-out"))

    assemble(
      List(southIn, northOut, northIn, southOut, eastIn, westOut, westIn, eastOut),
      List(northbound, southbound, westbound, eastbound)
    )
  }
}
