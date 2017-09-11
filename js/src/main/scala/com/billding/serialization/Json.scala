package com.billding.serialization

import com.billding.physics.Spatial
import com.billding.traffic.{DriverImpl, PilotedVehicleImpl, VehicleImpl}
import play.api.libs.json.{JsString, Json, Writes}
import squants.mass.Kilograms
import squants.{Acceleration, Mass, QuantityVector, Time}
import squants.motion.{Distance, MetersPerSecond, MetersPerSecondSquared, Velocity}
import squants.space.Meters
import squants.time.Milliseconds

object JsonShit {
  def parseVector(quantityVector: QuantityVector[Distance]) ={
    quantityVector.coordinates.map{
      piece => Json.obj("val" -> piece.toMeters)
    }

  }

//  implicit val qvWrites  = new Writes[QuantityVector[Distance]] {
//    def writes(quantityVector: QuantityVector[Distance]) =
//      Json.toJson(
//        quantityVector.coordinates.map {
//          piece => Json.obj("val" -> piece.toMeters)
//        }
//      )
//  }

implicit val distanceWrites  = new Writes[Distance] {
  def writes(distance: Distance) = new JsString(distance.toMeters + " " + Meters.symbol)
}

  implicit val velocityWrites  = new Writes[Velocity] {
    def writes(velocity: Velocity) = new JsString(velocity.toMetersPerSecond + " " + MetersPerSecond.symbol)
  }

  implicit val timeWrites  = new Writes[Time] {
    def writes(time: Time) = new JsString(time.toMilliseconds + " " + Milliseconds.symbol)
  }

  implicit val massWrites  = new Writes[Mass] {
    def writes(mass: Mass) = new JsString(mass.toKilograms + " " + Kilograms.symbol)
  }


  implicit val accelerationWrites  = new Writes[Acceleration] {
    def writes(acceleration: Acceleration) = new JsString(acceleration.toMetersPerSecondSquared + " " + MetersPerSecondSquared.symbol)
  }






  implicit val qvWrites  = new Writes[QuantityVector[Distance]] {
    def writes(quantityVector: QuantityVector[Distance]) =
      Json.toJson(
        quantityVector.coordinates.map {
          piece => piece
        }
      )
  }

  implicit val qvVelocityWrites  = new Writes[QuantityVector[Velocity]] {
    def writes(quantityVector: QuantityVector[Velocity]) =
      Json.toJson(
        quantityVector.coordinates.map {
          piece => piece
        }
      )
  }

  implicit val spatialWrites  = new Writes[Spatial] {
    def writes(spatial: Spatial) =
      Json.toJson(
        "r" -> spatial.r,
        "v" -> spatial.v,
        "dimensions" -> spatial.dimensions
      )
  }

  implicit val vehicleWrites  = new Writes[VehicleImpl] {
    /*
    spatial: SpatialImpl,
    accelerationAbility: Acceleration,
    brakingAbility: Acceleration,
    weight: Mass
    */
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
        "minimum_distance" -> driverImpl.minimumDistance,
        "desired_speed" -> driverImpl.desiredSpeed
      )
  }


//  PilotedVehicleImpl(driver: DriverImpl, vehicle: VehicleImpl, destination: Spatial)
implicit val pilotedVehicleWrites  = new Writes[PilotedVehicleImpl] {
  def writes(pilotedVehicleImpl: PilotedVehicleImpl) =
    Json.toJson(
      "driver" -> pilotedVehicleImpl.driver,
      "vehicle" -> pilotedVehicleImpl.vehicle,
      "destination" -> pilotedVehicleImpl.destination
    )
}

}
