package com.billding.serialization

import com.billding.physics.{SpatialImpl}
import com.billding.traffic._
import squants.{Acceleration, Mass, Quantity, QuantityVector, Time}
import squants.motion._
import play.api.libs.json.Reads.JsStringReads
import play.api.libs.json._
import play.api.libs.functional.syntax._

object JsonShit {

  implicit val localAccelerationFormat = BillSquants.acceleration.format

  implicit val distanceQvReads = BillSquants.distance.generalReads
  implicit val velocityQvReads = BillSquants.velocity.generalReads

  implicit val localDistanceFormat = BillSquants.distance.format

  implicit val localMassFormat = BillSquants.mass.format
  implicit val timeFormat = BillSquants.time.format
  implicit val velocityFormat = BillSquants.velocity.format


  val spatialWrites  = new Writes[SpatialImpl] {
    def writes(spatial: SpatialImpl) =
      Json.obj(
        "r" -> com.billding.serialization.BillSquants.distance.qvWrites.writes(spatial.r),
        "v" -> com.billding.serialization.BillSquants.velocity.qvWrites.writes(spatial.v),
        "dimensions" -> com.billding.serialization.BillSquants.distance.qvWrites.writes(spatial.dimensions)
      )
  }
  val spatialReads: Reads[SpatialImpl] = (
    (JsPath \ "r").read[QuantityVector[Distance]] and
      (JsPath \ "v").read[QuantityVector[Velocity]] and
      (JsPath \ "dimensions").read[QuantityVector[Distance]]
    )(SpatialImpl.apply _)


  implicit val spatialFormat: Format[SpatialImpl] = Format(spatialReads, spatialWrites)

  val vehicleReads: Reads[VehicleImpl] =
    (
      (JsPath \ "spatial").read[SpatialImpl] and
        (JsPath \ "acceleration_ability").read[Acceleration] and
        (JsPath \ "braking_ability").read[Acceleration] and
        (JsPath \ "weight").read[Mass]
      ) (VehicleImpl.apply _)

  val vehicleWrites  = new Writes[VehicleImpl] {
    def writes(vehicleImpl: VehicleImpl) =
      Json.obj(
        "spatial" -> vehicleImpl.spatial,
        "acceleration_ability" -> vehicleImpl.accelerationAbility,
        "braking_ability" -> vehicleImpl.brakingAbility,
        "weight" -> vehicleImpl.weight
      )
  }

  implicit val vehicleFormat: Format[VehicleImpl] = Format(vehicleReads, vehicleWrites)

  /* TODO enable this for IDM serialilzation
    */
  val idmFromString = (s: JsString) => DefaultDriverModel.idm
  implicit val idmReads: Reads[IntelligentDriverModel] = JsStringReads.map(idmFromString)

  val driverWrites  = new Writes[DriverImpl] {
    def writes(driverImpl: DriverImpl) =
      Json.obj(
        "spatial" -> driverImpl.spatial,
        "idm" -> driverImpl.idm.name, // TODO Get this figured out.
        "reaction_time" -> driverImpl.reactionTime,
        "preferred_dynamic_spacing" -> driverImpl.preferredDynamicSpacing,
        "minimum_distance" -> driverImpl.minimumDistance,
        "desired_speed" -> driverImpl.desiredSpeed
      )
  }

  val driverReads: Reads[DriverImpl] =
    (
      (JsPath \ "spatial").read[SpatialImpl] and
        (JsPath \ "idm").read[IntelligentDriverModel] and
        (JsPath \ "reaction_time").read[Time] and
        (JsPath \ "preferred_dynamic_spacing").read[Time] and
        (JsPath \ "minimum_distance").read[Distance] and
        (JsPath \ "desired_speed").read[Velocity]
      ) (DriverImpl.apply _)


  implicit val driverFormat: Format[DriverImpl] = Format(driverReads, driverWrites)

val pilotedVehicleWrites  = new Writes[PilotedVehicleImpl] {
  def writes(pilotedVehicleImpl: PilotedVehicleImpl) =
    Json.obj(
      "driver" -> pilotedVehicleImpl.driver,
      "vehicle" -> pilotedVehicleImpl.vehicle,
      "destination" -> pilotedVehicleImpl.destination
    )
}

  val pilotedVehicleReads: Reads[PilotedVehicleImpl] =
    (
      (JsPath \ "driver").read[DriverImpl] and
        (JsPath \ "vehicle").read[VehicleImpl] and
        (JsPath \ "destination").read[SpatialImpl]
      ) (PilotedVehicleImpl.apply _)

  implicit val pilotedVehicleFormat: Format[PilotedVehicleImpl] = Format(pilotedVehicleReads, pilotedVehicleWrites)

  implicit val vehicleSourceWrites  = new Writes[VehicleSource] {
    def writes(vehicleSource: VehicleSource) =
      Json.obj(
        "spacing_in_time" -> vehicleSource.spacingInTime,
        "spatial" -> vehicleSource.spatial,
        "starting_velocity_spacial" -> vehicleSource.startingVelocitySpacial
    )
  }

  implicit val laneWrites  = new Writes[LaneImpl] {
    def writes(lane: LaneImpl) =
//    Json.arr(lane.vehicles)
      Json.obj(
        "vehicles" -> Json.arr(lane.vehicles), // TODO Reimplement
        "vehicle_source" -> lane.vehicleSource,
        "beginning" -> lane.beginning,
        "end" -> lane.end,
//        "vehicle_at_infinity" -> lane.vehicleAtInfinity, // TODO Reimplement
        "infinity_spatial" -> lane.infinitySpatial
      )
  }

  implicit val streetWrites  = new Writes[Street] {
    def writes(street: Street) =
      Json.obj(
        "lanes" -> street.lanes,
        "beginning" -> street.beginning,
        "end" -> street.end,
        "source_timing" -> street.sourceTiming
      )
  }

//  implicit val sceneWrites  = new Writes[Scene] {
//    def writes(scene: Scene) =
//      Json.toJson(
//        "streets" -> scene.streets,
//        "t" -> scene.t,
//        "dt" -> scene.dt,
//        "speed_limit" -> scene.speedLimit,
//        "canvas_dimensions" -> scene.canvasDimensions
//      )
//  }

}
