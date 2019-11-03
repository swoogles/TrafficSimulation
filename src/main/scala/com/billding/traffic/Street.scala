package com.billding.traffic

import com.billding.Orientation
import com.billding.physics.Spatial
import squants.{Time, Velocity}
import squants.space.Meters

case class Street(
                   lanes: List[Lane],
                   beginning: Spatial,
                   end: Spatial
) {

  def updateLanes(f: Lane => Lane): Street =
    copy(lanes = lanes.map(f))
}

object Street {

  def apply(
             sourceTiming: Time,
             beginning: Spatial,
             end: Spatial,
             speed: Velocity,
             numLanes: Integer
           ): Street = {

    val lanes = for (i <- Range(0, numLanes).toList) yield {
      val offset = Meters(6) * i
      // TODO Fix hard-coded reference
      val newBeginning = beginning.move(Orientation.South, offset)
      val newEnd = end.move(Orientation.South, offset)
      Lane(sourceTiming, newBeginning, newEnd, speed)
    }
    Street(lanes, beginning, end)
  }

}
