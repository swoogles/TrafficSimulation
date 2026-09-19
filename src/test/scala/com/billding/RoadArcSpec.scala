package com.billding

import com.billding.svgRendering.RoadShape
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.math.Pi

/**
  * `RoadShape.arcPathData` is the pure arithmetic behind `RoadArc`/`DividerArc`'s SVG `path`:
  * no DOM involved, just the `d` string an `A` command needs. See the long comment on
  * `arcPathData` itself for why a positive (counter-clockwise, by `ArcPath`'s convention)
  * world sweep needs no sign flip to land on SVG's own clockwise-when-positive angle
  * convention - the tests here pin that down with concrete numbers instead of just asserting it.
  */
class RoadArcSpec extends AnyFlatSpec with Matchers {

  private val CenterX = 0.0
  private val CenterY = 0.0
  private val Radius = 100.0

  "arcPathData" should "draw a counter-clockwise quarter turn from 3 o'clock toward 12 o'clock, " +
    "which SVG has to render clockwise on screen" in {
    val d = RoadShape.arcPathData(CenterX, CenterY, Radius, startAngle = 0.0, sweep = Pi / 2)

    // Start at the 3 o'clock point, end at the 12 o'clock point (world y up: sweeping +90
    // degrees counter-clockwise from (r, 0) lands on (0, r)) - a small sweep, so no large-arc
    // flag, and a positive sweep, so SVG's sweep-flag is 1 (its own clockwise direction).
    d shouldBe "M100.000,0.000 A100.000,100.000 0 0 1 0.000,100.000"
  }

  it should "draw the same quarter turn clockwise, ending on the opposite point, with the " +
    "opposite sweep-flag" in {
    val d = RoadShape.arcPathData(CenterX, CenterY, Radius, startAngle = 0.0, sweep = -Pi / 2)

    d shouldBe "M100.000,0.000 A100.000,100.000 0 0 0 0.000,-100.000"
  }

  it should "start and end at the same two points for a half turn either direction, only the " +
    "sweep-flag telling them apart" in {
    val ccw = RoadShape.arcPathData(CenterX, CenterY, Radius, startAngle = 0.0, sweep = Pi)
    val cw = RoadShape.arcPathData(CenterX, CenterY, Radius, startAngle = 0.0, sweep = -Pi)

    // Both reach the 9 o'clock point, but floating-point sine of +-pi lands on either side
    // of zero by a hair, so the two "0"s print with opposite signs - a real artifact of
    // formatting math.sin(pi), not a claim that the endpoints actually differ.
    ccw shouldBe "M100.000,0.000 A100.000,100.000 0 0 1 -100.000,0.000"
    cw shouldBe "M100.000,0.000 A100.000,100.000 0 0 0 -100.000,-0.000"
  }

  it should "not set the large-arc flag exactly at half a turn" in {
    val d = RoadShape.arcPathData(CenterX, CenterY, Radius, startAngle = 0.0, sweep = Pi)
    largeArcFlagOf(d) shouldBe 0
  }

  it should "set the large-arc flag once the sweep passes half a turn, whichever direction" in {
    val justOver = RoadShape.arcPathData(CenterX, CenterY, Radius, startAngle = 0.0, sweep = Pi + 0.01)
    val justUnder =
      RoadShape.arcPathData(CenterX, CenterY, Radius, startAngle = 0.0, sweep = -(Pi + 0.01))

    largeArcFlagOf(justOver) shouldBe 1
    largeArcFlagOf(justUnder) shouldBe 1
  }

  it should "set the sweep-flag from the sign of the sweep alone, not its size" in {
    val smallPositive = RoadShape.arcPathData(CenterX, CenterY, Radius, startAngle = 0.3, sweep = 0.1)
    val bigPositive =
      RoadShape.arcPathData(CenterX, CenterY, Radius, startAngle = 0.3, sweep = Pi + 0.4)
    val smallNegative =
      RoadShape.arcPathData(CenterX, CenterY, Radius, startAngle = 0.3, sweep = -0.1)
    val bigNegative =
      RoadShape.arcPathData(CenterX, CenterY, Radius, startAngle = 0.3, sweep = -(Pi + 0.4))

    sweepFlagOf(smallPositive) shouldBe 1
    sweepFlagOf(bigPositive) shouldBe 1
    sweepFlagOf(smallNegative) shouldBe 0
    sweepFlagOf(bigNegative) shouldBe 0
  }

  it should "carry the centre offset and radius through to the start and end points" in {
    val d = RoadShape.arcPathData(500.0, -250.0, 40.0, startAngle = 0.0, sweep = Pi / 2)

    d shouldBe "M540.000,-250.000 A40.000,40.000 0 0 1 500.000,-210.000"
  }

  /** Pulls the large-arc-flag (the first flag field) out of an `A` command. */
  private def largeArcFlagOf(d: String): Int = arcFlags(d)._1

  /** Pulls the sweep-flag (the second flag field) out of an `A` command. */
  private def sweepFlagOf(d: String): Int = arcFlags(d)._2

  private def arcFlags(d: String): (Int, Int) = {
    // "M<x>,<y> A<rx>,<ry> <x-axis-rotation> <large-arc-flag> <sweep-flag> <x>,<y>"
    val fields = d.split(" ")
    (fields(3).toInt, fields(4).toInt)
  }
}
