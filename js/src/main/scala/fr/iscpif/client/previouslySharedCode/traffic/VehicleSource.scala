package fr.iscpif.client.previouslySharedCode.traffic

import fr.iscpif.client.previouslySharedCode.physics.{Spatial, SpatialFor, SpatialImpl}
import play.api.libs.json.{Format, Json}
import fr.iscpif.client.previouslySharedCode.serialization.BillSquants
import squants.{QuantityVector, Time, Velocity}
import squants.motion.Distance

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
  implicit val df: Format[Distance] = BillSquants.distance.format
  implicit val vf: Format[Velocity] = BillSquants.velocity.format

  implicit val driverFormat: Format[DriverImpl] = Json.format[DriverImpl]
  implicit val spatialForPilotedVehicle: SpatialFor[PilotedVehicle] = {
    case vehicle: PilotedVehicleImpl => vehicle.spatial
  }

  implicit val dQvf: Format[QuantityVector[Distance]] =
    BillSquants.distance.formatQv
  implicit val vQvf: Format[QuantityVector[Velocity]] =
    BillSquants.velocity.formatQv
  implicit val spatialFormat: Format[SpatialImpl] = Json.format[SpatialImpl]
  implicit val vehicleSourceFormat: Format[VehicleSourceImpl] =
    Json.format[VehicleSourceImpl]
}
