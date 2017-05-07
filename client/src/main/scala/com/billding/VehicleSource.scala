package com.billding

import squants.Time

trait VehicleSource {
  def produceVehicle(t: Time): Option[PilotedVehicle]
  val spacingInTime: Time
  val spatial: Spatial // TODO This will include starting velocity. Might not belong here.
}

case class VehicleSourceImpl(spacingInTime: Time, spatial: Spatial) extends  VehicleSource {
  override def produceVehicle(t: Time): Option[PilotedVehicle] = {
    val res = t / spacingInTime
    if (res.abs < 0.1) Some(PilotedVehicle.commuter(spatial, new IntelligentDriverModelImpl))
    else Option.empty
  }
}

object VehicleSource {
  def withTimeSpacing(averageDt: Time): VehicleSource = ???
}
