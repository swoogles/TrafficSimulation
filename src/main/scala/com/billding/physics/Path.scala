package com.billding.physics

import squants.motion.Distance
import squants.space.{Length, Meters}
import squants.{DoubleVector, QuantityVector}

import scala.math.{cos, sin, Pi}

/**
  * The shape of a road, with nothing driving on it.
  *
  * A position on a Path is an arc length - how far you have driven from the path's
  * origin - rather than a point in space. Points and headings are derived from that
  * when something needs them, which is what lets gaps stay honest around a curve:
  * the straight line between two cars on a loop is not the road between them.
  */
sealed trait Path {

  def totalLength: Length

  /** True when driving past the end of the path brings you back to the beginning. */
  def isClosed: Boolean

  /** Where in the world arc length s falls. */
  def pointAt(s: Length): QuantityVector[Distance]

  /** Unit vector pointing the way traffic travels at arc length s. */
  def headingAt(s: Length): DoubleVector

  /**
    * Unit vector pointing to the left of travel at arc length s.
    *
    * This is what lets something sit beside the path rather than on it, which is how a car
    * caught mid-lane-change is drawn: it is between two lanes, so it is offset from the one
    * it belongs to.
    */
  def normalAt(s: Length): DoubleVector

  /** Wrap s back onto a closed path, or clamp it to the ends of an open one. */
  def normalize(s: Length): Length

  /**
    * How far you travel going forward from `from` before you reach `to`.
    * On a closed path this wraps, so it is never negative.
    */
  def forwardGap(from: Length, to: Length): Length

  /** The patch of world this path takes up, for deciding what a canvas has to show. */
  def extent: PathExtent
}

/** The world rectangle a path occupies. */
case class PathExtent(center: QuantityVector[Distance], width: Length, height: Length)

final case class StraightPath(
  beginning: QuantityVector[Distance],
  end: QuantityVector[Distance]
) extends Path {

  val totalLength: Length = (end - beginning).magnitude

  val isClosed: Boolean = false

  private val heading: DoubleVector =
    (end - beginning).map { component: Distance =>
      component.toMeters
    }.normalize

  def pointAt(s: Length): QuantityVector[Distance] =
    beginning + heading.map { component: Double =>
      s * component
    }

  def headingAt(s: Length): DoubleVector = heading

  private val normal: DoubleVector =
    DoubleVector(-heading.coordinates(1), heading.coordinates.head, 0.0)

  def normalAt(s: Length): DoubleVector = normal

  def normalize(s: Length): Length =
    if (s < Meters(0)) Meters(0)
    else if (s > totalLength) totalLength
    else s

  /** Negative when `to` is behind `from`. An open road has no way around. */
  def forwardGap(from: Length, to: Length): Length = to - from

  val extent: PathExtent = {
    val span = end - beginning
    PathExtent(
      (beginning + end).map { component: Distance =>
        component / 2.0
      },
      span.coordinates.head.abs,
      span.coordinates(1).abs
    )
  }
}

object StraightPath {

  def between(beginning: Spatial, end: Spatial): StraightPath =
    StraightPath(beginning.r, end.r)
}

/**
  * A circular track. Arc length 0 sits at the 3 o'clock position and traffic runs
  * counter-clockwise, so heading always leads the radius by a quarter turn.
  */
final case class RingPath(
  center: QuantityVector[Distance],
  radius: Length
) extends Path {

  val totalLength: Length = radius * (2 * Pi)

  val isClosed: Boolean = true

  private def angleAt(s: Length): Double = s / radius

  def pointAt(s: Length): QuantityVector[Distance] = {
    val angle = angleAt(s)
    center + QuantityVector[Distance](radius * cos(angle), radius * sin(angle), Meters(0))
  }

  def headingAt(s: Length): DoubleVector = {
    val angle = angleAt(s)
    DoubleVector(-sin(angle), cos(angle), 0.0)
  }

  /** Traffic runs counter-clockwise, so the left of travel points in at the centre. */
  def normalAt(s: Length): DoubleVector = {
    val angle = angleAt(s)
    DoubleVector(-cos(angle), -sin(angle), 0.0)
  }

  def normalize(s: Length): Length = {
    val loop = totalLength.toMeters
    val remainder = s.toMeters % loop
    Meters(if (remainder < 0) remainder + loop else remainder)
  }

  def forwardGap(from: Length, to: Length): Length = normalize(to - from)

  val extent: PathExtent = PathExtent(center, radius * 2.0, radius * 2.0)
}

object RingPath {

  val ORIGIN: QuantityVector[Distance] =
    QuantityVector[Distance](Meters(0), Meters(0), Meters(0))

  /** The ring you actually want to specify: how much road there is to drive on. */
  def ofCircumference(circumference: Length): RingPath =
    RingPath(ORIGIN, circumference / (2 * Pi))

  /**
    * Concentric lanes, outermost first, each one lane-width in from the last.
    *
    * The outermost lane is the one whose circumference you asked for, so adding a lane to a
    * ring never moves the traffic that was already on it. Index 0 is the right-hand lane:
    * traffic runs counter-clockwise, so the inner lanes are the ones you overtake in.
    */
  def concentric(circumference: Length, laneCount: Int, laneWidth: Length): List[RingPath] = {
    require(laneCount >= 1, "a road needs at least one lane")
    val outer = ofCircumference(circumference)
    require(
      outer.radius > laneWidth * (laneCount - 1).toDouble,
      "the innermost lane would fall through the middle of the ring"
    )
    (0 until laneCount).toList.map { index =>
      RingPath(outer.center, outer.radius - laneWidth * index.toDouble)
    }
  }
}
