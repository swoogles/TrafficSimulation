package com.billding.traffic

import com.billding.physics.{Spatial, SpatialImpl}
import shared.Orientation
import squants.{Time, Velocity}
import squants.space.{Length, Meters}

case class StreetImpl(lanes: List[LaneImpl], beginning: SpatialImpl, end: SpatialImpl, sourceTiming: Time)

object Street {
  def apply(sourceTiming: Time, beginning: SpatialImpl, end: SpatialImpl, speed: Velocity, numLanes: Integer): StreetImpl = {

    val lanes = for (i <- Range(0, numLanes).toList) yield {
      val offset = Meters(6) * i
      // TODO Fix hard-coded reference
      val newBeginning = beginning.move(Orientation.South, offset)
      val newEnd = end.move(Orientation.South, offset)
      Lane(sourceTiming, newBeginning, newEnd, speed)
    }
    StreetImpl(lanes, beginning, end, sourceTiming)
  }

  def isVehicleBlockingTargetLane(street: StreetImpl, agent: PilotedVehicleImpl) = {
    ???
  }
}
