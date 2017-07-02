package com.billding.traffic

import com.billding.physics.Spatial
import squants.Time

trait VehicleSource {
  def produceVehicle(t: Time, dt: Time, destination: Spatial): Option[PilotedVehicle]
  val spacingInTime: Time
  val spatial: Spatial // TODO This will include starting velocity. Might not belong here.
  val startingVelocitySpacial: Spatial
}

case class VehicleSourceImpl(spacingInTime: Time, spatial: Spatial, startingVelocitySpacial: Spatial) extends  VehicleSource {
  override def produceVehicle(t: Time, dt: Time, destination: Spatial): Option[PilotedVehicle] = {
    val res = t % spacingInTime
    if (res.abs < dt.toSeconds) {
      val vehicleSpatial = Spatial.withVecs(spatial.r, startingVelocitySpacial.v, spatial.dimensions)
      Some(PilotedVehicle.commuter(vehicleSpatial, new IntelligentDriverModelImpl, destination))
    }
    else Option.empty
  }
}

object VehicleSource {
  def withTimeSpacing(averageDt: Time): VehicleSource = ???
}
