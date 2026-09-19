package com.billding

import com.billding.network._
import com.billding.physics.StraightPath
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import squants.motion.{Distance, KilometersPerHour}
import squants.space.Meters
import squants.QuantityVector

class RoadNetworkSpec extends AnyFlatSpec with Matchers {

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

  "A two-section network with one continuation" should "answer all three accessors" in {
    val a = straightSection("a", 0, 100)
    val b = straightSection("b", 100, 200)
    val movement = Movement.continuation(MovementId("a-b"), SectionId("a"), SectionId("b"))

    val network = RoadNetwork(
      sections = Map(SectionId("a") -> a, SectionId("b") -> b),
      movements = List(movement)
    )

    network.section(SectionId("a")) shouldBe Some(a)
    network.section(SectionId("b")) shouldBe Some(b)
    network.section(SectionId("missing")) shouldBe None

    network.movementsFrom(SectionId("a")) shouldBe List(movement)
    network.movementsInto(SectionId("b")) shouldBe List(movement)
    network.movementsFrom(SectionId("b")) shouldBe Nil
    network.movementsInto(SectionId("a")) shouldBe Nil
  }

  it should "make the seam an uncontrolled continuation by default" in {
    val movement = Movement.continuation(MovementId("a-b"), SectionId("a"), SectionId("b"))

    movement.kind shouldBe MovementKind.Continuation
    movement.control shouldBe Control.Uncontrolled
  }

  "A Y-split" should "answer movementsFrom with two movements in a stable declared order" in {
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

    network.movementsFrom(SectionId("trunk")) shouldBe List(toLeft, toRight)
    network.movementsInto(SectionId("left")) shouldBe List(toLeft)
    network.movementsInto(SectionId("right")) shouldBe List(toRight)
  }
}
