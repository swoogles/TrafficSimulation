package com.billding

import com.billding.network._
import com.billding.physics.{Path, PathGrowth}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import squants.motion.{Distance, KilometersPerHour}
import squants.space.{Length, Meters}
import squants.{DoubleVector, QuantityVector}

import scala.math.{cos, sin}

class NetworkValidationSpec extends AnyFlatSpec with Matchers {

  private val laneWidth = Meters(3.5)
  private val speedLimit = KilometersPerHour(50)
  private val origin = QuantityVector[Distance](Meters(0), Meters(0), Meters(0))
  private val east = DoubleVector(1.0, 0.0, 0.0)

  private def section(id: String, path: Path, width: Length = laneWidth, layer: Int = 0): LaneSection =
    LaneSection(SectionId(id), path, width, speedLimit, layer)

  private def networkOf(sections: LaneSection*)(movements: Movement*): RoadNetwork =
    RoadNetwork(sections.map(s => s.id -> s).toMap, movements.toList)

  "faults" should "find nothing wrong with a continuous straight/arc/straight chain" in {
    val straight1 = PathGrowth.straightFrom(origin, east, Meters(40))
    val arc = PathGrowth.arcFrom(
      PathGrowth.endPoint(straight1),
      PathGrowth.endHeading(straight1),
      radius = Meters(15),
      sweep = math.Pi / 3
    )
    val straight2 = PathGrowth.straightFrom(
      PathGrowth.endPoint(arc),
      PathGrowth.endHeading(arc),
      Meters(20)
    )

    val sectionA = section("a", straight1)
    val sectionB = section("b", arc)
    val sectionC = section("c", straight2)

    val network = networkOf(sectionA, sectionB, sectionC)(
      Movement.continuation(MovementId("a-b"), SectionId("a"), SectionId("b")),
      Movement.continuation(MovementId("b-c"), SectionId("b"), SectionId("c"))
    )

    NetworkValidation.faults(network) shouldBe Nil
  }

  it should "report exactly one fault, naming the movement, for a 1 m position gap" in {
    val sectionA = section("a", PathGrowth.straightFrom(origin, east, Meters(100)))
    val gap = QuantityVector[Distance](Meters(1), Meters(0), Meters(0))
    val gappedStart = PathGrowth.endPoint(sectionA.path) + gap
    val sectionB = section("b", PathGrowth.straightFrom(gappedStart, east, Meters(50)))

    val movement = Movement.continuation(MovementId("a-b"), SectionId("a"), SectionId("b"))
    val network = networkOf(sectionA, sectionB)(movement)

    val faults = NetworkValidation.faults(network)
    faults should have size 1

    faults.head shouldBe a[NetworkFault.PositionDiscontinuity]
    val fault = faults.head.asInstanceOf[NetworkFault.PositionDiscontinuity]
    fault.movement shouldBe MovementId("a-b")
    fault.gap.toMeters shouldBe 1.0 +- 1e-6
    fault.message should include("a-b")
  }

  it should "report exactly one fault, naming the movement, for a 0.5 rad heading kink" in {
    val sectionA = section("a", PathGrowth.straightFrom(origin, east, Meters(100)))
    val kinkedHeading = DoubleVector(cos(0.5), sin(0.5), 0.0)
    val sectionB = section("b", PathGrowth.straightFrom(PathGrowth.endPoint(sectionA.path), kinkedHeading, Meters(50)))

    val movement = Movement.continuation(MovementId("a-b"), SectionId("a"), SectionId("b"))
    val network = networkOf(sectionA, sectionB)(movement)

    val faults = NetworkValidation.faults(network)
    faults should have size 1

    faults.head shouldBe a[NetworkFault.TangentDiscontinuity]
    val fault = faults.head.asInstanceOf[NetworkFault.TangentDiscontinuity]
    fault.movement shouldBe MovementId("a-b")
    fault.angle shouldBe 0.5 +- 1e-6
    fault.message should include("a-b")
  }

  it should "report exactly one fault, naming the movement, for a movement between different layers" in {
    val sectionA = section("a", PathGrowth.straightFrom(origin, east, Meters(100)), layer = 0)
    val sectionB =
      section("b", PathGrowth.straightFrom(PathGrowth.endPoint(sectionA.path), east, Meters(50)), layer = 1)

    val movement = Movement.continuation(MovementId("a-b"), SectionId("a"), SectionId("b"))
    val network = networkOf(sectionA, sectionB)(movement)

    val faults = NetworkValidation.faults(network)
    faults should have size 1

    faults.head shouldBe a[NetworkFault.LayerMismatch]
    val fault = faults.head.asInstanceOf[NetworkFault.LayerMismatch]
    fault.movement shouldBe MovementId("a-b")
    fault.fromLayer shouldBe 0
    fault.toLayer shouldBe 1
    fault.message should include("a-b")
  }

  it should "report exactly one fault, naming the movement, for an incompatible width" in {
    val sectionA = section("a", PathGrowth.straightFrom(origin, east, Meters(100)), width = Meters(3.5))
    val sectionB =
      section("b", PathGrowth.straightFrom(PathGrowth.endPoint(sectionA.path), east, Meters(50)), width = Meters(5.0))

    val movement = Movement.continuation(MovementId("a-b"), SectionId("a"), SectionId("b"))
    val network = networkOf(sectionA, sectionB)(movement)

    val faults = NetworkValidation.faults(network)
    faults should have size 1

    faults.head shouldBe a[NetworkFault.IncompatibleWidth]
    val fault = faults.head.asInstanceOf[NetworkFault.IncompatibleWidth]
    fault.movement shouldBe MovementId("a-b")
    fault.message should include("a-b")
  }

  it should "tolerate the small position and heading error real arc arithmetic leaves behind" in {
    val straight = PathGrowth.straightFrom(origin, east, Meters(10))
    val arc = PathGrowth.arcFrom(PathGrowth.endPoint(straight), PathGrowth.endHeading(straight), Meters(8), math.Pi / 4)

    val sectionA = section("a", straight)
    val sectionB = section("b", arc)

    val network =
      networkOf(sectionA, sectionB)(Movement.continuation(MovementId("a-b"), SectionId("a"), SectionId("b")))

    NetworkValidation.faults(network) shouldBe Nil
  }

  it should "skip a movement whose section is missing rather than throw" in {
    val sectionA = section("a", PathGrowth.straightFrom(origin, east, Meters(100)))
    val movement = Movement.continuation(MovementId("a-missing"), SectionId("a"), SectionId("missing"))
    val network = networkOf(sectionA)(movement)

    NetworkValidation.faults(network) shouldBe Nil
  }
}
