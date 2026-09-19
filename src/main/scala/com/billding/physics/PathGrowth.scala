package com.billding.physics

import squants.motion.Distance
import squants.space.Length
import squants.{DoubleVector, QuantityVector}

/**
  * Grow a new piece of road from an existing end.
  *
  * These are the only two shapes the endpoint-growth gesture (phase J) ever produces: a
  * straight run, or an arc that bends left or right. Both start from a point and a heading
  * rather than from whatever internal parameters `Path` happens to use, because that is what
  * an existing end actually gives you - and `endPoint`/`endHeading` are the inverse, so a
  * caller can grow the next piece from the one it just built.
  */
object PathGrowth {

  /** Unit vector to the left of `heading`: `heading` rotated a quarter turn counter-clockwise. */
  private def leftOf(heading: DoubleVector): DoubleVector =
    DoubleVector(-heading.coordinates(1), heading.coordinates.head, 0.0)

  /** A straight run of `length` starting at `start`, heading in the direction of `heading`. */
  def straightFrom(
    start: QuantityVector[Distance],
    heading: DoubleVector,
    length: Length
  ): StraightPath = {
    val end = start + heading.map { component: Double => length * component }
    StraightPath(start, end)
  }

  /**
    * An arc of `radius` starting at `start`, heading in the direction of `heading`, sweeping
    * `sweep` signed radians - positive curves left (counter-clockwise), negative curves right
    * (clockwise).
    *
    * The centre sits one radius to the left of travel for a positive sweep, one radius to the
    * right for a negative one, which is what makes the arc actually bend the way `sweep` says
    * rather than merely spin in place. `startAngle` then falls out as the angle from that
    * centre back to `start`, so `ArcPath`'s own `pointAt(0)` and `headingAt(0)` land exactly
    * on `start` and `heading`.
    */
  def arcFrom(
    start: QuantityVector[Distance],
    heading: DoubleVector,
    radius: Length,
    sweep: Double
  ): ArcPath = {
    val left = leftOf(heading)
    val centerDirection =
      if (sweep >= 0) left
      else DoubleVector(-left.coordinates.head, -left.coordinates(1), 0.0)

    val center = start + centerDirection.map { component: Double => radius * component }

    val fromCenterToStart = start - center
    val startAngle = math.atan2(
      fromCenterToStart.coordinates(1).toMeters,
      fromCenterToStart.coordinates.head.toMeters
    )

    ArcPath(center, radius, startAngle, sweep)
  }

  /** Where a path leaves off, so the next growth can start there. */
  def endPoint(path: Path): QuantityVector[Distance] = path.pointAt(path.totalLength)

  /** Which way a path is heading when it leaves off, so the next growth can continue it. */
  def endHeading(path: Path): DoubleVector = path.headingAt(path.totalLength)
}
