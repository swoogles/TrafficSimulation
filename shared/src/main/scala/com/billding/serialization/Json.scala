package com.billding.serialization

import com.billding.physics.SpatialImpl
import com.billding.traffic._
import squants.{Acceleration, Length, Mass, Quantity, QuantityVector, Time}
import squants.motion._
import play.api.libs.json._
import play.api.libs.functional.syntax._

object JsonShit {

  import BillSquants.acceleration.format

  implicit private val localDistanceFormat: Format[Distance] = BillSquants.distance.format
  implicit private val localDistanceFormatQv: Format[QuantityVector[Distance]] = BillSquants.distance.formatQv
  implicit private val localVelocityFormatQv: Format[QuantityVector[Velocity]] = BillSquants.velocity.formatQv

  implicit private val localMassFormat: Format[Mass] = BillSquants.mass.format
  implicit private val timeFormat: Format[Time] = BillSquants.time.format
  implicit private val velocityFormat: Format[Velocity] = BillSquants.velocity.format


  implicit val spatialFormat: Format[SpatialImpl] = Json.format[SpatialImpl]
  implicit val vehicleFormat = Json.format[VehicleImpl]
  implicit val idmFormat = Json.format[IntelligentDriverModelImpl]
  implicit val driverFormat = Json.format[DriverImpl]
  implicit val pilotedVehicleFormat = Json.format[PilotedVehicleImpl]
  implicit val vehicleSourceFormat: Format[VehicleSourceImpl] = Json.format[VehicleSourceImpl]
  implicit val laneFormat: Format[LaneImpl] = Json.format[LaneImpl]
  implicit val streetFormat: Format[StreetImpl] = Json.format[StreetImpl]
  implicit val sceneFormats: Format[SceneImpl] = Json.format[SceneImpl]
}
