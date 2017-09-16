package com.billding.serialization

import com.billding.physics.SpatialImpl
import com.billding.traffic.{
  DriverImpl,
  IntelligentDriverModelImpl,
  LaneImpl,
  PilotedVehicleImpl,
  SceneImpl,
  StreetImpl,
  VehicleImpl,
  VehicleSourceImpl
}
import play.api.libs.json.{Format, Json}
import squants.motion.Distance
import squants.{Acceleration, Mass, QuantityVector, Time, Velocity}

case class TrafficJson()(
                              implicit val localDistanceFormat: Format[Distance],
                              implicit val localDistanceFormatQv: Format[QuantityVector[Distance]],
                              implicit val localMassFormat: Format[Mass],
                              implicit val timeFormat: Format[Time],
                              implicit val accelerationFormat: Format[Acceleration],
                              implicit val velocityFormat: Format[Velocity],
                              implicit val localVelocityFormatQv: Format[QuantityVector[Velocity]]
                            ) {
  implicit val spatialFormat: Format[SpatialImpl] = Json.format[SpatialImpl]
  implicit val vehicleFormat: Format[VehicleImpl] = Json.format[VehicleImpl]
  implicit val idmFormat: Format[IntelligentDriverModelImpl] = Json.format[IntelligentDriverModelImpl]
  implicit val driverFormat: Format[DriverImpl] = Json.format[DriverImpl]
  implicit val pilotedVehicleFormat: Format[PilotedVehicleImpl] = Json.format[PilotedVehicleImpl]
  implicit val vehicleSourceFormat: Format[VehicleSourceImpl] = Json.format[VehicleSourceImpl]
  implicit val laneFormat: Format[LaneImpl] = Json.format[LaneImpl]
  implicit val streetFormat: Format[StreetImpl] = Json.format[StreetImpl]
  implicit val sceneFormats: Format[SceneImpl] = Json.format[SceneImpl]
}

object TrafficJson {
  val defaultSerialization =
    TrafficJson()(
      BillSquants.distance.format,
      BillSquants.distance.formatQv,
      BillSquants.mass.format,
      BillSquants.time.format,
      BillSquants.acceleration.format,
      BillSquants.velocity.format,
      BillSquants.velocity.formatQv
    )
}
