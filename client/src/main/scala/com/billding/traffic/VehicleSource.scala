package com.billding.traffic

import com.billding.physics.Spatial
import squants.motion.Distance
import squants.{Length, QuantityVector, Time, Velocity}

trait VehicleSource {
  def produceVehicle(t: Time): Option[PilotedVehicle]
  val spacingInTime: Time
  val spatial: Spatial // TODO This will include starting velocity. Might not belong here.
  val destination: Spatial
  val enteringSpeed: Velocity
  val enteringVelocity = spatial.vectorTo(destination).normalize.map{ x: Distance => x.toMeters * enteringSpeed}
}

case class VehicleSourceImpl(spacingInTime: Time, spatial: Spatial, destination: Spatial, enteringSpeed: Velocity) extends  VehicleSource {

  override def produceVehicle(t: Time): Option[PilotedVehicle] = {
    val startingVehicleSpatial = Spatial.withVecs(spatial.r, enteringVelocity, spatial.dimensions)
    val res = t % spacingInTime
    if (res.abs < 0.1) {
      Some(PilotedVehicle.commuter(startingVehicleSpatial, new IntelligentDriverModelImpl))
    }
    else Option.empty
  }
}

object VehicleSource {
  def withTimeSpacing(averageDt: Time): VehicleSource = ???
}
