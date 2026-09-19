package com.billding.network

import com.billding.physics.PathGrowth
import squants.space.{Length, Meters}

/**
  * A reason a [[Movement]] does not describe a real, drivable connection.
  *
  * Every fault names the offending movement and carries enough of the
  * geometry it disagreed about to make `message` readable on its own -
  * nothing here is meant to be caught and handled, only shown to whoever is
  * looking at the network.
  */
sealed trait NetworkFault {
  def movement: MovementId
  def message: String
}

object NetworkFault {

  /** The end of `from` and the start of `to` are further apart than [[NetworkValidation.PositionTolerance]]. */
  final case class PositionDiscontinuity(
    movement: MovementId,
    from: SectionId,
    to: SectionId,
    gap: Length
  ) extends NetworkFault {

    def message: String =
      f"movement ${movement.value}: end of ${from.value} is ${gap.toMeters}%.3f m from " +
        f"the start of ${to.value}, past the ${NetworkValidation.PositionTolerance.toMeters}%.2f m tolerance"
  }

  /** The end heading of `from` and the start heading of `to` differ by more than [[NetworkValidation.HeadingTolerance]]. */
  final case class TangentDiscontinuity(
    movement: MovementId,
    from: SectionId,
    to: SectionId,
    angle: Double
  ) extends NetworkFault {

    def message: String =
      f"movement ${movement.value}: heading kinks ${angle}%.4f rad between ${from.value} and " +
        f"${to.value}, past the ${NetworkValidation.HeadingTolerance}%.2f rad tolerance"
  }

  /**
    * `from` and `to` sit on different layers. The plan requires an explicit transition
    * piece for a layer change, so a movement that crosses layers on its own is never valid -
    * there is no tolerance here to loosen.
    */
  final case class LayerMismatch(
    movement: MovementId,
    from: SectionId,
    to: SectionId,
    fromLayer: Int,
    toLayer: Int
  ) extends NetworkFault {

    def message: String =
      s"movement ${movement.value}: connects layer $fromLayer (${from.value}) to layer " +
        s"$toLayer (${to.value}) with no explicit transition piece"
  }

  /** `from` and `to` have widths too different to be the same lane continuing, with no explicit taper. */
  final case class IncompatibleWidth(
    movement: MovementId,
    from: SectionId,
    to: SectionId,
    fromWidth: Length,
    toWidth: Length
  ) extends NetworkFault {

    def message: String =
      f"movement ${movement.value}: width changes from ${fromWidth.toMeters}%.2f m (${from.value}) " +
        f"to ${toWidth.toMeters}%.2f m (${to.value}) with no explicit taper"
  }
}

/**
  * Checks a [[RoadNetwork]]'s movements for connections that are not actually real.
  *
  * Snapping proposes a connection; this is what makes one true. A movement is only as
  * good as the geometry it claims to join - two sections whose ends merely look close on
  * screen are not connected unless their ends coincide, their tangents agree, they sit on
  * the same layer, and their widths match. This module only reports: it does not move a
  * section, does not invent a transition piece, and does not drop a movement. Phase J
  * decides what, if anything, to do about a fault.
  */
object NetworkValidation {

  /**
    * How far apart the end of `from` and the start of `to` may sit before a movement counts
    * as discontinuous.
    *
    * Loose enough that ordinary arc/straight construction - accumulated floating-point error
    * through sin/cos and vector addition as in [[PathGrowth]] - never trips it on purpose-built
    * geometry; tight enough that a road end left a real gap short of its neighbour still fails.
    */
  val PositionTolerance: Length = Meters(0.05)

  /**
    * How far the end heading of `from` may differ from the start heading of `to`, in radians,
    * before a movement counts as kinked rather than continuous.
    *
    * A little over a degree: loose enough to absorb the same arc arithmetic that motivates
    * [[PositionTolerance]], tight enough that two pieces actually grown at different angles
    * still fail.
    */
  val HeadingTolerance: Double = 0.02

  /**
    * Every fault found across `network`'s movements, in the order the movements were declared.
    * A movement whose `from` or `to` section is not in `network.sections` is skipped rather than
    * reported - a dangling reference is a different kind of problem than a bad connection, and
    * out of scope here.
    */
  def faults(network: RoadNetwork): List[NetworkFault] =
    network.movements.flatMap(checkMovement(network, _))

  private def checkMovement(network: RoadNetwork, movement: Movement): List[NetworkFault] =
    (network.section(movement.from), network.section(movement.to)) match {
      case (Some(from), Some(to)) =>
        List(
          positionFault(movement, from, to),
          headingFault(movement, from, to),
          layerFault(movement, from, to),
          widthFault(movement, from, to)
        ).flatten
      case _ => Nil
    }

  private def positionFault(movement: Movement, from: LaneSection, to: LaneSection): Option[NetworkFault] = {
    val end = PathGrowth.endPoint(from.path)
    val start = to.path.pointAt(Meters(0))
    val gap = (start - end).magnitude
    if (gap > PositionTolerance)
      Some(NetworkFault.PositionDiscontinuity(movement.id, movement.from, movement.to, gap))
    else None
  }

  private def headingFault(movement: Movement, from: LaneSection, to: LaneSection): Option[NetworkFault] = {
    val endHeading = PathGrowth.endHeading(from.path)
    val startHeading = to.path.headingAt(Meters(0))
    val cosAngle = math.max(-1.0, math.min(1.0, endHeading.dotProduct(startHeading)))
    val angle = math.acos(cosAngle)
    if (angle > HeadingTolerance)
      Some(NetworkFault.TangentDiscontinuity(movement.id, movement.from, movement.to, angle))
    else None
  }

  private def layerFault(movement: Movement, from: LaneSection, to: LaneSection): Option[NetworkFault] =
    if (from.layer != to.layer)
      Some(NetworkFault.LayerMismatch(movement.id, movement.from, movement.to, from.layer, to.layer))
    else None

  private def widthFault(movement: Movement, from: LaneSection, to: LaneSection): Option[NetworkFault] = {
    // Width compatibility is measured on the same physical scale as position: a difference
    // smaller than a real misalignment would be is noise, not a lane-count change worth a fault.
    val diff = (from.width - to.width).abs
    if (diff > PositionTolerance)
      Some(NetworkFault.IncompatibleWidth(movement.id, movement.from, movement.to, from.width, to.width))
    else None
  }
}
