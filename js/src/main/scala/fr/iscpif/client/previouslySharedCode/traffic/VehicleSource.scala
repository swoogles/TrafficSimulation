package fr.iscpif.client.previouslySharedCode.traffic

import fr.iscpif.client.previouslySharedCode.physics.{Spatial, SpatialImpl}
import fr.iscpif.client.previouslySharedCode.serialization.BillSquants
import play.api.libs.json.{Format, Json}
import squants.{Time, Velocity}

trait VehicleSource {
  def produceVehicle(t: Time,
                     dt: Time,
                     destination: SpatialImpl): Option[PilotedVehicleImpl]
}

case class VehicleSourceImpl(
    spacingInTime: Time,
    spatial: SpatialImpl,
    startingVelocitySpacial: SpatialImpl
) extends VehicleSource {

  override def produceVehicle(
      t: Time,
      dt: Time,
      destination: SpatialImpl): Option[PilotedVehicleImpl] = {
    // Woohoo! this was the problem! t was coming in as ms after loading the new scene for some reason...
    // Should make this prettier/type-safe in the future.
    val res = t.toSeconds % spacingInTime.toSeconds
    if (res.abs < dt.toSeconds) {
      val vehicleSpatial = Spatial.withVecs(spatial.r,
                                            startingVelocitySpacial.v,
                                            spatial.dimensions)
      Some(
        PilotedVehicle.commuter(vehicleSpatial,
                                new IntelligentDriverModelImpl,
                                destination))
    } else Option.empty
  }

  def updateSpeed(speed: Velocity): VehicleSourceImpl = {
    val startingV = startingVelocitySpacial.v.normalize.map { x: Velocity =>
      x.value * speed
    }
    this.copy(startingVelocitySpacial =
      this.startingVelocitySpacial.copy(v = startingV))
  }
}

object VehicleSourceImpl {
  implicit val tf: Format[Time] = BillSquants.time.format
  implicit val vehicleSourceFormat: Format[VehicleSourceImpl] =
    Json.format[VehicleSourceImpl]
}
