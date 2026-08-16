package com.billding.svgRendering

import java.util.UUID

import squants.motion.Distance
import squants.{DoubleVector, QuantityVector}

/**
  * Everything the canvas needs to know about one car, and nothing else.
  *
  * Heading comes from the road rather than the car's velocity, so a stopped car still
  * points the way it was going.
  */
case class RenderedVehicle(
  position: QuantityVector[Distance],
  heading: DoubleVector,
  width: Distance,
  height: Distance,
  uuid: UUID,
  motion: Motion = Motion.Steady
) {

  /** Clockwise degrees from east, in screen space, where y already points down. */
  val headingInDegrees: Double =
    math.toDegrees(math.atan2(heading.coordinates(1), heading.coordinates.head))
}
