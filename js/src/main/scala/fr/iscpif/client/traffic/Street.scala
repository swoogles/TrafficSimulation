package fr.iscpif.client.traffic

import fr.iscpif.client.Orientation
import fr.iscpif.client.physics.SpatialImpl
import squants.{Time, Velocity}
import squants.space.Meters

case class Street(
                       lanes: List[Lane],
                       beginning: SpatialImpl,
                       end: SpatialImpl
) {

  def updateLanes(f: Lane => Lane): Street =
    copy(lanes = lanes map f)
}

object Street {
  def apply(sourceTiming: Time,
            beginning: SpatialImpl,
            end: SpatialImpl,
            speed: Velocity,
            numLanes: Integer): Street = {

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
