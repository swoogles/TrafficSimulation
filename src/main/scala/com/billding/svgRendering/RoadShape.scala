package com.billding.svgRendering

import com.billding.physics.{Path, RingPath, StraightPath}
import squants.motion.Distance
import squants.space.{Length, Meters}
import squants.QuantityVector

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

object RoadShape {

  /** Wide enough for the 4m cars, and the same 6m that Street uses to space its lanes. */
  val LaneWidth: Length = Meters(6)

  /** The tarmac a path implies. Cars drive along the middle of it. */
  def of(path: Path, width: Length = LaneWidth): RoadShape = path match {
    case ring: RingPath         => RoadRing(ring.center, ring.radius, width)
    case straight: StraightPath => RoadStrip(straight.beginning, straight.end, width)
  }
}
