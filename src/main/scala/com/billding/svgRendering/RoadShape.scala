package com.billding.svgRendering

import com.billding.physics.{ArcPath, Path, RingPath, StraightPath}
import squants.motion.Distance
import squants.space.{Length, Meters}
import squants.QuantityVector

import scala.math.Pi

/**
  * The road itself, in world coordinates, for the canvas to lay down before the traffic.
  *
  * Kept as shapes rather than a list of points because a ring drawn as a circle stays a
  * circle at any zoom, where a polyline would show its corners.
  */
sealed trait RoadShape {
  def width: Length
}

case class RoadRing(
  center: QuantityVector[Distance],
  radius: Length,
  width: Length
) extends RoadShape

case class RoadStrip(
  from: QuantityVector[Distance],
  to: QuantityVector[Distance],
  width: Length
) extends RoadShape

/**
  * A finite bend of road: `radius` out from `center`, from `startAngle` through `sweep`
  * radians - the same signed convention as `ArcPath`, positive counter-clockwise in world
  * terms. Drawn as a single SVG arc rather than a polyline for the same reason `RoadRing`
  * is drawn as a circle: it stays smooth at any zoom.
  */
case class RoadArc(
  center: QuantityVector[Distance],
  radius: Length,
  startAngle: Double,
  sweep: Double,
  width: Length
) extends RoadShape

/**
  * The dashed line between two lanes of a ring, drawn on top of the tarmac.
  *
  * Its width is the lane width it divides rather than its own stroke, so the dashes stay in
  * proportion to the road the way the edge lines do.
  */
case class DividerRing(
  center: QuantityVector[Distance],
  radius: Length,
  width: Length
) extends RoadShape

/** The `RoadArc` equivalent of `DividerRing`, for lane lines on a bend rather than a full ring. */
case class DividerArc(
  center: QuantityVector[Distance],
  radius: Length,
  startAngle: Double,
  sweep: Double,
  width: Length
) extends RoadShape

/**
  * The line the traffic is counted at, painted across the road rather than along it.
  *
  * Drawn because a counter with no line is a number you have to take on trust: the point of
  * marking it is that you can watch a car go over it and see the count go up. Given as the
  * two ends of the stripe rather than as an angle on a ring, so the canvas draws it the same
  * way whatever road it is measuring.
  */
case class CountingLine(
  from: QuantityVector[Distance],
  to: QuantityVector[Distance],
  width: Length
) extends RoadShape

object RoadShape {

  /** Wide enough for the 4m cars, and the same 6m that Street uses to space its lanes. */
  val LaneWidth: Length = Meters(6)

  /** How thick the counting line is painted - about a quarter of a car, so it reads as paint. */
  val CountingLineWidth: Length = Meters(1.5)

  /** The tarmac a path implies. Cars drive along the middle of it. */
  def of(path: Path, width: Length = LaneWidth): RoadShape = path match {
    case ring: RingPath         => RoadRing(ring.center, ring.radius, width)
    case straight: StraightPath => RoadStrip(straight.beginning, straight.end, width)
    case arc: ArcPath           => RoadArc(arc.center, arc.radius, arc.startAngle, arc.sweep, width)
  }

  /**
    * The `d` attribute for an SVG `path` drawing a `RoadArc`/`DividerArc`, given the shape's
    * centre, radius and angles already carried into pixel space.
    *
    * Kept as plain numbers rather than a `Projection` and a `RoadArc` so it can be unit-tested
    * with no DOM: it is arithmetic, not rendering.
    *
    * SVG measures its own angles in a frame whose y-axis points down, and `Projection` carries
    * a world point into pixels with no sign flip on either axis (`xOf`/`yOf` just add a scaled
    * offset) - so the world's `(startAngle, sweep)` can be plugged straight into the pixel-space
    * point formula `(cx + r*cos(theta), cy + r*sin(theta))` with no extra negation anywhere.
    * That is also why the flags below line up so simply: SVG's sweep-flag = 1 means its own
    * "positive-angle direction", which in a y-down frame is clockwise - and a positive `sweep`
    * here (counter-clockwise by the `ArcPath` convention, where y is conventionally "up") ends
    * up drawn clockwise on screen for exactly the same reason. Concretely: sweeping from the
    * 3 o'clock point by +pi/2 lands on the 6 o'clock point, and 3 -> 6 is the clockwise way
    * round a clock face. So `sweepFlag` is simply the sign of `sweep`, unchanged.
    */
  def arcPathData(
    centerX: Double,
    centerY: Double,
    radius: Double,
    startAngle: Double,
    sweep: Double
  ): String = {
    def pointAt(angle: Double): (Double, Double) =
      (centerX + radius * math.cos(angle), centerY + radius * math.sin(angle))

    val (startX, startY) = pointAt(startAngle)
    val (endX, endY) = pointAt(startAngle + sweep)

    val largeArcFlag = if (math.abs(sweep) > Pi) 1 else 0
    val sweepFlag = if (sweep > 0) 1 else 0

    def fmt(d: Double): String = f"$d%.3f"

    s"M${fmt(startX)},${fmt(startY)} " +
      s"A${fmt(radius)},${fmt(radius)} 0 $largeArcFlag $sweepFlag ${fmt(endX)},${fmt(endY)}"
  }
}
