package com.billding.traffic

import com.billding.physics.Spatial
import squants.Time

trait VehicleSource {
  def produceVehicle(t: Time, dt: Time): Option[PilotedVehicle]
  val spacingInTime: Time
  val spatial: Spatial // TODO This will include starting velocity. Might not belong here.
}

case class VehicleSourceImpl(spacingInTime: Time, spatial: Spatial) extends  VehicleSource {
  override def produceVehicle(t: Time, dt: Time): Option[PilotedVehicle] = {
    val res = t % spacingInTime
//    println("t: " + t)
//    println("spacingInTime: " + spacingInTime)
//    println("res: " + res)
    if (res.abs < dt.toSeconds) {
//      println("making a vehicle!")
      Some(PilotedVehicle.commuter(spatial, new IntelligentDriverModelImpl))
    }
    else Option.empty
  }
}

object VehicleSource {
  def withTimeSpacing(averageDt: Time): VehicleSource = ???
}
