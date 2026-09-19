package com.billding

import com.billding.physics.{ArcPath, Path, PathGrowth, StraightPath}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import squants.motion.Distance
import squants.space.{Length, Meters}
import squants.{DoubleVector, QuantityVector}

import scala.math.Pi

class PathGrowthSpec extends AnyFlatSpec with Matchers {

  private val PositionTolerance = 0.001 // a millimetre, in metres
  private val HeadingTolerance = 1e-9

  private val origin = QuantityVector[Distance](Meters(0), Meters(0), Meters(0))
  private val east = DoubleVector(1.0, 0.0, 0.0)

  private def metersFrom(vector: QuantityVector[Distance]): Seq[Double] =
    vector.coordinates.map(_.toMeters)

  private def positionsShouldMatch(
    actual: QuantityVector[Distance],
    expected: QuantityVector[Distance]
  ): Unit =
    metersFrom(actual).zip(metersFrom(expected)).foreach {
      case (a, e) => a shouldBe e +- PositionTolerance
    }

  private def headingsShouldMatch(actual: DoubleVector, expected: DoubleVector): Unit =
    actual.coordinates.zip(expected.coordinates).foreach {
      case (a, e) => a shouldBe e +- HeadingTolerance
    }

  "straightFrom" should "run length metres in the given heading, starting at start" in {
    val path = PathGrowth.straightFrom(origin, east, Meters(25))

    positionsShouldMatch(path.pointAt(Meters(0)), origin)
    metersFrom(PathGrowth.endPoint(path)) shouldBe Seq(25.0, 0.0, 0.0)
    headingsShouldMatch(PathGrowth.endHeading(path), east)
  }

  it should "hold its start heading for a heading other than a cardinal direction" in {
    val diagonal = DoubleVector(3.0, 4.0, 0.0).normalize
    val path = PathGrowth.straightFrom(origin, diagonal, Meters(10))

    headingsShouldMatch(path.headingAt(Meters(0)), diagonal)
    headingsShouldMatch(PathGrowth.endHeading(path), diagonal)
  }

  "arcFrom" should "start exactly at start, heading exactly in the given direction" in {
    val path = PathGrowth.arcFrom(origin, east, radius = Meters(10), sweep = Pi / 2)

    positionsShouldMatch(path.pointAt(Meters(0)), origin)
    headingsShouldMatch(path.headingAt(Meters(0)), east)
  }

  it should "curve left (toward +y) when swept with a positive angle heading east" in {
    val path = PathGrowth.arcFrom(origin, east, radius = Meters(10), sweep = Pi / 2)
    val end = PathGrowth.endPoint(path)

    // A quarter turn left from due east, radius 10, ends at (10, 10) - up and to the left of
    // straight travel, not down and to the right.
    metersFrom(end)(0) shouldBe 10.0 +- PositionTolerance
    metersFrom(end)(1) shouldBe 10.0 +- PositionTolerance
    headingsShouldMatch(PathGrowth.endHeading(path), DoubleVector(0.0, 1.0, 0.0))
  }

  it should "curve right (toward -y) when swept with a negative angle heading east" in {
    val path = PathGrowth.arcFrom(origin, east, radius = Meters(10), sweep = -Pi / 2)
    val end = PathGrowth.endPoint(path)

    metersFrom(end)(0) shouldBe 10.0 +- PositionTolerance
    metersFrom(end)(1) shouldBe -10.0 +- PositionTolerance
    headingsShouldMatch(PathGrowth.endHeading(path), DoubleVector(0.0, -1.0, 0.0))
  }

  it should "start exactly at start and heading for an arbitrary starting heading" in {
    val diagonal = DoubleVector(1.0, 1.0, 0.0).normalize
    val path = PathGrowth.arcFrom(origin, diagonal, radius = Meters(7), sweep = -Pi / 3)

    positionsShouldMatch(path.pointAt(Meters(0)), origin)
    headingsShouldMatch(path.headingAt(Meters(0)), diagonal)
  }

  "a chain of growths" should "stay continuous in position to a millimetre and in heading to 1e-9" in {
    val straight1 = PathGrowth.straightFrom(origin, east, Meters(40))

    val arc = PathGrowth.arcFrom(
      PathGrowth.endPoint(straight1),
      PathGrowth.endHeading(straight1),
      radius = Meters(15),
      sweep = Pi / 3
    )

    val straight2 = PathGrowth.straightFrom(
      PathGrowth.endPoint(arc),
      PathGrowth.endHeading(arc),
      Meters(20)
    )

    val chain: Seq[Path] = Seq(straight1, arc, straight2)

    chain.zip(chain.tail).foreach {
      case (previous, next) =>
        positionsShouldMatch(next.pointAt(Meters(0)), PathGrowth.endPoint(previous))
        headingsShouldMatch(next.headingAt(Meters(0)), PathGrowth.endHeading(previous))
    }

    // And the chain actually goes somewhere - it is not three segments stacked on the origin.
    val finalPoint = PathGrowth.endPoint(straight2)
    metersFrom(finalPoint) should not be Seq(0.0, 0.0, 0.0)
  }

  it should "stay continuous through a longer chain of alternating left and right arcs" in {
    def growArc(from: Path, radius: Length, sweep: Double): ArcPath =
      PathGrowth.arcFrom(PathGrowth.endPoint(from), PathGrowth.endHeading(from), radius, sweep)

    def growStraight(from: Path, length: Length): StraightPath =
      PathGrowth.straightFrom(PathGrowth.endPoint(from), PathGrowth.endHeading(from), length)

    val s1 = PathGrowth.straightFrom(origin, east, Meters(10))
    val a1 = growArc(s1, Meters(8), Pi / 4)
    val s2 = growStraight(a1, Meters(5))
    val a2 = growArc(s2, Meters(6), -Pi / 6)
    val s3 = growStraight(a2, Meters(12))

    val chain: Seq[Path] = Seq(s1, a1, s2, a2, s3)

    chain.zip(chain.tail).foreach {
      case (previous, next) =>
        positionsShouldMatch(next.pointAt(Meters(0)), PathGrowth.endPoint(previous))
        headingsShouldMatch(next.headingAt(Meters(0)), PathGrowth.endHeading(previous))
    }
  }
}
