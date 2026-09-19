package com.billding

import com.billding.physics.{ArcPath, RingPath}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import squants.motion.Distance
import squants.space.{Length, Meters}
import squants.QuantityVector

import scala.math.Pi

class ArcPathSpec extends AnyFlatSpec with Matchers {

  private val Tolerance = 1e-9

  private val center = QuantityVector[Distance](Meters(0), Meters(0), Meters(0))
  private val radius = Meters(10)

  private def metersFrom(vector: QuantityVector[Distance]): Seq[Double] =
    vector.coordinates.map(_.toMeters)

  private def shouldBeNear(actual: Seq[Double], expected: Seq[Double]): Unit =
    actual.zip(expected).foreach { case (a, e) => a shouldBe e +- Tolerance }

  // A quarter turn counter-clockwise: 3 o'clock around to 12 o'clock.
  private val ccwQuarter = ArcPath(center, radius, startAngle = 0.0, sweep = Pi / 2)

  // The same quarter turn, swept the other way: 3 o'clock down to 6 o'clock.
  private val cwQuarter = ArcPath(center, radius, startAngle = 0.0, sweep = -Pi / 2)

  "An arc path" should "measure its length as radius times the sweep, regardless of sign" in {
    ccwQuarter.totalLength.toMeters shouldBe (10.0 * Pi / 2) +- Tolerance
    cwQuarter.totalLength.toMeters shouldBe (10.0 * Pi / 2) +- Tolerance
  }

  it should "not be closed" in {
    ccwQuarter.isClosed shouldBe false
  }

  it should "place points along a counter-clockwise sweep against hand-computed coordinates" in {
    metersFrom(ccwQuarter.pointAt(Meters(0))) shouldBe Seq(10.0, 0.0, 0.0)
    shouldBeNear(metersFrom(ccwQuarter.pointAt(ccwQuarter.totalLength)), Seq(0.0, 10.0, 0.0))

    val halfway = ccwQuarter.pointAt(ccwQuarter.totalLength / 2.0)
    val expected = 10.0 * math.sqrt(2.0) / 2.0
    metersFrom(halfway)(0) shouldBe expected +- Tolerance
    metersFrom(halfway)(1) shouldBe expected +- Tolerance
  }

  it should "place points along a clockwise sweep against hand-computed coordinates" in {
    metersFrom(cwQuarter.pointAt(Meters(0))) shouldBe Seq(10.0, 0.0, 0.0)
    shouldBeNear(metersFrom(cwQuarter.pointAt(cwQuarter.totalLength)), Seq(0.0, -10.0, 0.0))
  }

  it should "agree with a ring's heading on a counter-clockwise arc" in {
    val ring = RingPath(center, radius)
    (0 to 4).foreach { fifth =>
      val s = ccwQuarter.totalLength * (fifth / 4.0)
      ccwQuarter.headingAt(s).coordinates.zip(ring.headingAt(s).coordinates).foreach {
        case (arc, ring) => arc shouldBe ring +- Tolerance
      }
    }
  }

  it should "reverse tangent direction when swept clockwise" in {
    // At the shared start point (10, 0), CCW heads toward +y and CW heads toward -y.
    ccwQuarter.headingAt(Meters(0)).coordinates shouldBe Seq(0.0, 1.0, 0.0)
    cwQuarter.headingAt(Meters(0)).coordinates.map(math.abs) shouldBe Seq(0.0, 1.0, 0.0)
    cwQuarter.headingAt(Meters(0)).coordinates(1) should be < 0.0
  }

  it should "point its normal at the centre when sweeping counter-clockwise" in {
    (0 to 4).foreach { fifth =>
      val s = ccwQuarter.totalLength * (fifth / 4.0)
      val point = ccwQuarter.pointAt(s)
      val towardCentre = (center - point).map { component: Distance =>
        component.toMeters
      }.normalize

      ccwQuarter.normalAt(s).coordinates.zip(towardCentre.coordinates).foreach {
        case (normal, expected) => normal shouldBe expected +- Tolerance
      }
    }
  }

  it should "point its normal away from the centre when sweeping clockwise" in {
    (0 to 4).foreach { fifth =>
      val s = cwQuarter.totalLength * (fifth / 4.0)
      val point = cwQuarter.pointAt(s)
      val awayFromCentre = (point - center).map { component: Distance =>
        component.toMeters
      }.normalize

      cwQuarter.normalAt(s).coordinates.zip(awayFromCentre.coordinates).foreach {
        case (normal, expected) => normal shouldBe expected +- Tolerance
      }
    }
  }

  it should "clamp arc lengths to its ends rather than wrapping" in {
    ccwQuarter.normalize(Meters(-5)).toMeters shouldBe 0.0
    ccwQuarter.normalize(ccwQuarter.totalLength + Meters(5)).toMeters shouldBe
      ccwQuarter.totalLength.toMeters
    ccwQuarter.normalize(Meters(3)).toMeters shouldBe 3.0
  }

  it should "report a forward gap as a plain difference, with no wrap" in {
    ccwQuarter.forwardGap(Meters(2), Meters(5)).toMeters shouldBe 3.0 +- Tolerance
    ccwQuarter.forwardGap(Meters(5), Meters(2)).toMeters shouldBe -3.0 +- Tolerance
  }

  it should "bound its extent to the quadrant it actually sweeps, not the whole circle" in {
    val extent = ccwQuarter.extent

    // A quarter turn from 3 o'clock to 12 o'clock never reaches the left or bottom of the
    // circle, so the box is a quarter the area of the full circle's, not the 20m-square
    // bounding box a whole ring at this radius would report.
    extent.width.toMeters shouldBe 10.0 +- Tolerance
    extent.height.toMeters shouldBe 10.0 +- Tolerance
    shouldBeNear(metersFrom(extent.center), Seq(5.0, 5.0, 0.0))
  }

  it should "include a cardinal point the sweep crosses even when it isn't an endpoint" in {
    // Centered on 3 o'clock, this arc sweeps from -45 to +45 degrees, crossing the 0-radian
    // cardinal (the rightmost point of the circle) without ending there.
    val crossingZero = ArcPath(center, radius, startAngle = -Pi / 4, sweep = Pi / 2)
    val extent = crossingZero.extent

    // If the far cardinal weren't picked up, maxX would stop at the endpoints'
    // cos(45deg)*10 =~ 7.07, short of the true rightmost point at x = 10.
    extent.width.toMeters shouldBe (10.0 - 10.0 * math.sqrt(2.0) / 2.0) +- Tolerance
    metersFrom(extent.center)(0) shouldBe (10.0 + 10.0 * math.sqrt(2.0) / 2.0) / 2.0 +- Tolerance
  }
}
