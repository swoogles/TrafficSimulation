package com.billding.traffic

import com.billding.physics.{Orientation, Spatial}
import squants.Time
import squants.space.{Length, Meters}

case class Street(
                   lanes: List[LaneImpl],
                   beginning: Spatial,
                   end: Spatial
                 )

object Street {
  def apply(
             sourceTiming: Time,
             beginning: Spatial,
             end: Spatial,
             orientation: Orientation,
             numLanes: Integer
           ): Street = {

    val lanes = for (i <- Range(0, numLanes).toList) yield {
      val offset = Meters(6) * i
      val newBeginning = beginning.move(orientation, offset)
      val newEnd = end.move(orientation, offset)
      Lane(sourceTiming, newBeginning, newEnd)
    }
    Street(lanes, beginning, end)
  }
}
