package com.billding.network

/**
  * What kind of continuation a [[Movement]] represents.
  *
  * `Continuation` is the ordinary case: one section flows into the next with no
  * change in lane count. `Merge` and `Diverge` are where lane count changes -
  * two sections feeding one, or one feeding two. `Turn` is a movement at a
  * junction that crosses other traffic rather than simply extending a lane.
  */
sealed trait MovementKind
object MovementKind {
  case object Continuation extends MovementKind
  case object Merge extends MovementKind
  case object Diverge extends MovementKind
  case object Turn extends MovementKind
}

/**
  * The right-of-way rule a [[Movement]] carries.
  *
  * `Uncontrolled` is the boring default a plain road seam gets: nothing about
  * continuing along the same road should ever cost a driver a stop. `Stop` and
  * `Yield` are for movements at junctions, where a movement crosses or waits on
  * other traffic.
  */
sealed trait Control
object Control {
  case object Uncontrolled extends Control
  case object Stop extends Control
  case object Yield extends Control
}

/**
  * The permission to continue from the end of `from` into the start of `to`.
  *
  * A seam between two straight pieces of the same road is the boring case: an
  * `Uncontrolled` `Continuation`. The plan is explicit that road seams must not
  * add stops, so that combination is the one a caller reaches for without
  * having to think - [[Movement.continuation]] builds exactly it.
  */
final case class Movement(
  id: MovementId,
  from: SectionId,
  to: SectionId,
  kind: MovementKind,
  control: Control
)

object Movement {

  /** An ordinary road seam: no lane count change, nothing to stop for. */
  def continuation(id: MovementId, from: SectionId, to: SectionId): Movement =
    Movement(id, from, to, MovementKind.Continuation, Control.Uncontrolled)
}
