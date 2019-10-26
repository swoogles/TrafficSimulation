package fr.iscpif.client.previouslySharedCode.traffic

import fr.iscpif.client.previouslySharedCode.Orientation
import fr.iscpif.client.previouslySharedCode.physics.SpatialImpl
import fr.iscpif.client.previouslySharedCode.serialization.BillSquants
import play.api.libs.json.{Format, Json}
import squants.{Time, Velocity}
import squants.space.Meters

case class StreetImpl(
    lanes: List[LaneImpl],
    beginning: SpatialImpl,
    end: SpatialImpl
) {

  def updateLanes(f: LaneImpl => LaneImpl): StreetImpl =
    copy(lanes = lanes map f)
}

object StreetImpl {
  implicit val tf: Format[Time] = BillSquants.time.format
  implicit val streetFormat: Format[StreetImpl] = Json.format[StreetImpl]
}

object Street {
  def apply(sourceTiming: Time,
            beginning: SpatialImpl,
            end: SpatialImpl,
            speed: Velocity,
            numLanes: Integer): StreetImpl = {

    val lanes = for (i <- Range(0, numLanes).toList) yield {
      val offset = Meters(6) * i
      // TODO Fix hard-coded reference
      val newBeginning = beginning.move(Orientation.South, offset)
      val newEnd = end.move(Orientation.South, offset)
      Lane(sourceTiming, newBeginning, newEnd, speed)
    }
    StreetImpl(lanes, beginning, end)
  }

}
