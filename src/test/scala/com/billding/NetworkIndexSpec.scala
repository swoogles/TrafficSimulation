package com.billding

import com.billding.network._
import com.billding.physics.StraightPath
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import squants.motion.{Distance, KilometersPerHour}
import squants.space.Meters
import squants.QuantityVector

class NetworkIndexSpec extends AnyFlatSpec with Matchers {

  private val laneWidth = Meters(3.5)
  private val speedLimit = KilometersPerHour(50)

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

  "The index of a Y-split" should "be correct in both directions" in {
    val trunk = straightSection("trunk", 0, 100)
    val left = straightSection("left", 100, 200)
    val right = straightSection("right", 100, 200)

    val toLeft = Movement(
      MovementId("trunk-left"),
      SectionId("trunk"),
      SectionId("left"),
      MovementKind.Diverge,
      Control.Uncontrolled
    )
    val toRight = Movement(
      MovementId("trunk-right"),
      SectionId("trunk"),
      SectionId("right"),
      MovementKind.Diverge,
      Control.Uncontrolled
    )

    val network = RoadNetwork(
      sections = Map(
        SectionId("trunk") -> trunk,
        SectionId("left") -> left,
        SectionId("right") -> right
      ),
      movements = List(toLeft, toRight)
    )

    val index = NetworkIndex(network)

    index.outgoing(SectionId("trunk")) shouldBe List(toLeft, toRight)
    index.successors(SectionId("trunk")) shouldBe List(SectionId("left"), SectionId("right"))

    index.incoming(SectionId("left")) shouldBe List(toLeft)
    index.incoming(SectionId("right")) shouldBe List(toRight)
    index.predecessors(SectionId("left")) shouldBe List(SectionId("trunk"))
    index.predecessors(SectionId("right")) shouldBe List(SectionId("trunk"))
  }

  it should "preserve declared order of the two branches, not sort them" in {
    val trunk = straightSection("trunk", 0, 100)
    val left = straightSection("left", 100, 200)
    val right = straightSection("right", 100, 200)

    // Declare "right" before "left" here - the index must not reorder them.
    val toRight = Movement(
      MovementId("trunk-right"),
      SectionId("trunk"),
      SectionId("right"),
      MovementKind.Diverge,
      Control.Uncontrolled
    )
    val toLeft = Movement(
      MovementId("trunk-left"),
      SectionId("trunk"),
      SectionId("left"),
      MovementKind.Diverge,
      Control.Uncontrolled
    )

    val network = RoadNetwork(
      sections = Map(
        SectionId("trunk") -> trunk,
        SectionId("left") -> left,
        SectionId("right") -> right
      ),
      movements = List(toRight, toLeft)
    )

    val index = NetworkIndex(network)

    index.outgoing(SectionId("trunk")) shouldBe List(toRight, toLeft)
    index.successors(SectionId("trunk")) shouldBe List(SectionId("right"), SectionId("left"))
  }

  "The index of a merge" should "be correct in both directions" in {
    val onRamp = straightSection("on-ramp", 0, 100)
    val mainline = straightSection("mainline", 0, 300)
    val merged = straightSection("merged", 300, 400)

    val fromRamp = Movement.continuation(MovementId("ramp-merged"), SectionId("on-ramp"), SectionId("merged"))
    val fromMainline =
      Movement.continuation(MovementId("mainline-merged"), SectionId("mainline"), SectionId("merged"))

    val network = RoadNetwork(
      sections = Map(
        SectionId("on-ramp") -> onRamp,
        SectionId("mainline") -> mainline,
        SectionId("merged") -> merged
      ),
      movements = List(fromRamp, fromMainline)
    )

    val index = NetworkIndex(network)

    index.outgoing(SectionId("on-ramp")) shouldBe List(fromRamp)
    index.outgoing(SectionId("mainline")) shouldBe List(fromMainline)
    index.incoming(SectionId("merged")) shouldBe List(fromRamp, fromMainline)
    index.predecessors(SectionId("merged")) shouldBe List(SectionId("on-ramp"), SectionId("mainline"))
  }

  "A section with no outgoing movement" should "return Nil rather than throwing" in {
    val onlySection = straightSection("lonely", 0, 100)
    val network = RoadNetwork(sections = Map(SectionId("lonely") -> onlySection), movements = Nil)

    val index = NetworkIndex(network)

    index.successors(SectionId("lonely")) shouldBe Nil
    index.predecessors(SectionId("lonely")) shouldBe Nil
    index.successors(SectionId("missing")) shouldBe Nil
    index.predecessors(SectionId("missing")) shouldBe Nil
  }
}
