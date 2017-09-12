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

  implicit val localVelocityWrites = BillSquants.velocity.singleWrites
  implicit val distanceQvReads = BillSquants.distance.generalReads
  implicit val velocityQvReads = BillSquants.velocity.generalReads

  implicit val localDistanceFormat = BillSquants.distance.format

  implicit val localMassFormat = BillSquants.mass.format
  import BillSquants.time.singleWrites


  implicit val spatialWrites  = new Writes[SpatialImpl] {
    def writes(spatial: SpatialImpl) =
      Json.toJson(
        "r" -> com.billding.serialization.BillSquants.distance.qvWrites.writes(spatial.r),
        "v" -> com.billding.serialization.BillSquants.velocity.qvWrites.writes(spatial.v),
        "dimensions" -> com.billding.serialization.BillSquants.distance.qvWrites.writes(spatial.dimensions)
      )
  }
  implicit val spatialReads: Reads[SpatialImpl] = (
    (JsPath \ "r").read[QuantityVector[Distance]] and
      (JsPath \ "v").read[QuantityVector[Velocity]] and
      (JsPath \ "dimensions").read[QuantityVector[Distance]]
    )(SpatialImpl.apply _)



  val vehicleReads =
    (
      (JsPath \ "spatial").read[SpatialImpl] and
        (JsPath \ "acceleration_ability").read[Acceleration] and
        (JsPath \ "braking_ability").read[Acceleration] and
        (JsPath \ "weight").read[Mass]
      ) (VehicleImpl.apply _)

  implicit val vehicleWrites  = new Writes[VehicleImpl] {
    def writes(vehicleImpl: VehicleImpl) =
      Json.toJson(
        "spatial" -> vehicleImpl.spatial,
        "acceleration_ability" -> vehicleImpl.accelerationAbility,
        "braking_ability" -> vehicleImpl.brakingAbility,
        "weight" -> vehicleImpl.weight
      )
  }


  implicit val driverWrites  = new Writes[DriverImpl] {
    def writes(driverImpl: DriverImpl) =
      Json.toJson(
        "spatial" -> driverImpl.spatial,
//        "idm" -> driverImpl.idm, // TODO Get this figured out.
        "reactionTime" -> driverImpl.reactionTime,
        "preferred_dynamic_spacing" -> driverImpl.preferredDynamicSpacing,
        "minimum_distance" -> BillSquants.distance.singleWrites.writes(driverImpl.minimumDistance),
        "desired_speed" -> driverImpl.desiredSpeed
      )
  }

implicit val pilotedVehicleWrites  = new Writes[PilotedVehicleImpl] {
  def writes(pilotedVehicleImpl: PilotedVehicleImpl) =
    Json.toJson(
      "driver" -> pilotedVehicleImpl.driver,
      "vehicle" -> pilotedVehicleImpl.vehicle,
      "destination" -> pilotedVehicleImpl.destination
    )
}

  implicit val vehicleSourceWrites  = new Writes[VehicleSource] {
    def writes(vehicleSource: VehicleSource) =
      Json.toJson(
        "spacing_in_time" -> vehicleSource.spacingInTime,
        "spatial" -> vehicleSource.spatial,
        "starting_velocity_spacial" -> vehicleSource.startingVelocitySpacial
    )
  }

  implicit val laneWrites  = new Writes[LaneImpl] {
    def writes(lane: LaneImpl) =
      Json.toJson(
        "vehicles" -> lane.vehicles,
        "vehicle_source" -> lane.vehicleSource,
        "beginning" -> lane.beginning,
        "end" -> lane.end,
        "vehicle_at_infinity" -> lane.vehicleAtInfinity,
        "infinity_spatial" -> lane.infinitySpatial
      )
  }

  implicit val streetWrites  = new Writes[Street] {
    def writes(street: Street) =
      Json.toJson(
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
