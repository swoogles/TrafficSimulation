package com.billding.network

/**
  * A named right-of-way policy for one junction.
  *
  * A preset is a pure `RoadNetwork => RoadNetwork`: it rewrites only the movements named
  * in `junction`, leaves geometry and every unrelated movement alone, and carries no state
  * into the simulation. [[Admission]] therefore sees the resulting [[Control]] values in
  * exactly the same way whether they came from a connection preview, a saved network, or an
  * explicit edit.
  */
sealed trait PriorityPreset extends (RoadNetwork => RoadNetwork) {
  def junction: Set[MovementId]
  protected def controlFor(movement: Movement): Control

  final override def apply(network: RoadNetwork): RoadNetwork =
    network.copy(
      movements = network.movements.map { movement =>
        if (junction.contains(movement.id)) movement.copy(control = controlFor(movement))
        else movement
      }
    )
}

object PriorityPreset {

  /** Every approach must make a full stop before conflict admission is considered. */
  final case class AllWayStop(junction: Set[MovementId]) extends PriorityPreset {
    override protected def controlFor(movement: Movement): Control = Control.Stop
  }

  /**
    * Movements arriving from `majorApproaches` keep priority; every other movement in the
    * junction must stop. A two-way road is represented by two one-way approach sections, so
    * callers normally name both directions of the major road here.
    */
  final case class TwoWayStop(junction: Set[MovementId], majorApproaches: Set[SectionId])
      extends PriorityPreset {
    override protected def controlFor(movement: Movement): Control =
      if (majorApproaches.contains(movement.from)) Control.Uncontrolled else Control.Stop
  }

  /** Major-road movements keep priority; movements from every minor approach must yield. */
  final case class YieldOnMinor(junction: Set[MovementId], majorApproaches: Set[SectionId])
      extends PriorityPreset {
    override protected def controlFor(movement: Movement): Control =
      if (majorApproaches.contains(movement.from)) Control.Uncontrolled else Control.Yield
  }

  /**
    * Tune one movement after applying a preset.
    *
    * This deliberately stores no separate override flag. Applying another preset later
    * replaces the old controls, including an older override; applying this after the latest
    * preset leaves the explicit choice in the network. That gives the editor the ordering the
    * interaction promises without hidden precedence state.
    */
  def overrideMovement(network: RoadNetwork, movementId: MovementId, control: Control): RoadNetwork =
    network.copy(
      movements = network.movements.map { movement =>
        if (movement.id == movementId) movement.copy(control = control) else movement
      }
    )
}
