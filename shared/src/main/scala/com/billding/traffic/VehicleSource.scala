package com.billding.traffic

import com.billding.physics.{Spatial, SpatialImpl}
import com.billding.serialization.BillSquants
import play.api.libs.json.{Format, Json}
import squants.motion.KilometersPerHour
import squants.{Time, Velocity}

trait VehicleSource {
  def produceVehicle(t: Time, dt: Time, destination: SpatialImpl): Option[PilotedVehicleImpl]
  val spacingInTime: Time
  val spatial: SpatialImpl // TODO This will include starting velocity. Might not belong here.
  val startingVelocitySpacial: SpatialImpl
}

object VehicleSource {
  def withTimeSpacing(averageDt: Time): VehicleSource = ???
}

case class VehicleSourceImpl(
  spacingInTime: Time,
  spatial: SpatialImpl,
  startingVelocitySpacial: SpatialImpl
) extends  VehicleSource {

  override def produceVehicle(t: Time, dt: Time, destination: SpatialImpl): Option[PilotedVehicleImpl] = {
    val res = t % spacingInTime
    if (res.abs < dt.toSeconds) {
      val vehicleSpatial = Spatial.withVecs(spatial.r, startingVelocitySpacial.v, spatial.dimensions)
      Some(PilotedVehicle.commuter(vehicleSpatial, new IntelligentDriverModelImpl, destination))
    }
    else Option.empty
  }

  def updateSpeed(speed: Velocity): VehicleSourceImpl = {
    val startingV = startingVelocitySpacial.v.normalize.map { x: Velocity => x.value * speed }
    //    val velocitySpatial = SpatialImpl(beginning.r, startingV, beginning.dimensions)
    this.copy(startingVelocitySpacial = this.startingVelocitySpacial.copy(v = startingV))
  }
}

object VehicleSourceImpl {
  implicit val tf = BillSquants.time.format
  implicit val vehicleSourceFormat: Format[VehicleSourceImpl] = Json.format[VehicleSourceImpl]
}

