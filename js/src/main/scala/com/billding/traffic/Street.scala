package com.billding.traffic

import com.billding.physics.{Spatial}
import shared.Orientation;
import squants.{Time, Velocity}
import squants.space.{Length, Meters}

case class Street(lanes: List[LaneImpl], beginning: Spatial, end: Spatial, sourceTiming: Time)

object Street {
  def apply(sourceTiming: Time, beginning: Spatial, end: Spatial, speed: Velocity, numLanes: Integer): Street = {

    val lanes = for (i <- Range(0, numLanes).toList) yield {
      val offset = Meters(6) * i
      // TODO Fix hard-coded reference
      val newBeginning = beginning.move(Orientation.South, offset)
      val newEnd = end.move(Orientation.South, offset)
      Lane(sourceTiming, newBeginning, newEnd, speed)
    }
    Street(lanes, beginning, end, sourceTiming)
  }
}