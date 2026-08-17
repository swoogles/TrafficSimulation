package com.billding.svgRendering

/**
  * A car telling you, before it moves, that it is about to.
  *
  * This is the rendering half of a driver's intent, and it carries the two things a drawing
  * of one needs: which way it is going, and how close it is to going. Everything else about
  * the decision - which lane, on what grounds, whether it will hold up - belongs to the
  * traffic model and is none of the canvas's business.
  *
  * Direction is given as left or right of travel, which is the sense a path's normal already
  * runs in and, on a ring, the sense a driver would use: the inner lanes are the ones you
  * overtake in.
  */
case class LaneChangeSignal(toTheLeft: Boolean, progress: Double) {

  /** +1 to the driver's left, -1 to its right, matching a path's normal. */
  val direction: Double = if (toTheLeft) 1.0 else -1.0
}
