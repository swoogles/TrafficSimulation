package com.billding.serialization

import com.billding.physics.Spatial
import com.billding.traffic._
import play.api.libs.json._
import squants.mass.Kilograms
import squants.{Acceleration, Mass, Quantity, QuantityVector, Time}
import squants.motion.{Distance, MetersPerSecond, MetersPerSecondSquared, Velocity}
import squants.space.{Length, Meters}
import squants.time.Milliseconds
import play.api.libs.json.Reads.JsStringReads
import play.api.libs.json._
import play.api.libs.functional.syntax._


object JsonShit {
  sealed trait BillSquants[T <: Quantity[T]] {
    val singleReads: Reads[T]
    val singleWrites: Writes[T]
    //    val theReads: Reads[QuantityVector[T]]

    implicit val generalReads = new Reads[QuantityVector[T]] {
      def reads(jsValue: JsValue): JsResult[QuantityVector[T]] = {
        val blah: Seq[JsValue] = jsValue.as[Seq[JsValue]]
        val egh: Seq[JsResult[T]] = blah.map(x => singleReads.reads(x))
        val foo: Seq[T] = egh.map(jsRes => jsRes.get)
        JsSuccess(
          QuantityVector.apply(foo: _*)
        )
      }
    }

    implicit val qvWrites: Writes[QuantityVector[T]] {
      def writes(quantityVector: QuantityVector[T]): JsValue
    } = new Writes[QuantityVector[T]] {
      def writes(quantityVector: QuantityVector[T]) =
        Json.toJson(
          quantityVector.coordinates.map {
            piece => singleWrites.writes(piece)
          }
        )
    }
  }
  object BillSquants {
    implicit val distance = new BillSquants[Distance]{
      override val singleReads = distanceReads
      override val singleWrites = distanceWrites
    }
    implicit val dReads = distance.singleReads

    implicit val length = new BillSquants[Length]{
      override val singleReads = distanceReads
      override val singleWrites = distanceWrites
    }
    implicit val lReads = length.singleReads


    implicit val velocity = new BillSquants[Velocity]{
      override val singleReads = velocityReads
      override val singleWrites = velocityWrites
    }
    implicit val vReads = velocity.singleReads
  }

implicit val distanceWrites  = new Writes[Distance] {
  def writes(distance: Distance) = new JsString(distance.toMeters + " " + Meters.symbol)
}

  implicit val lengthWrites  = new Writes[Length] {
    def writes(length: Length) = new JsString(length.toMeters + " " + Meters.symbol)
  }

  implicit val timeWrites  = new Writes[Time] {
    def writes(time: Time) = new JsString(time.toMilliseconds + " " + Milliseconds.symbol)
  }



  def distanceConverterJs(s: JsString) =
    Length.apply(s.value).get

  def velocityConverterJs(s: JsString) =
    Velocity.apply(s.value).get

  implicit val distanceReads: Reads[Distance]  =
    JsStringReads.map(distanceConverterJs)

  implicit val velocityWrites  = new Writes[Velocity] {
    def writes(velocity: Velocity) = new JsString(velocity.toMetersPerSecond + " " + MetersPerSecond.symbol)
  }

  implicit val velocityReads: Reads[Velocity]  =
    JsStringReads.map(velocityConverterJs)

  implicit val massWrites  = new Writes[Mass] {
    def writes(mass: Mass) = new JsString(mass.toKilograms + " " + Kilograms.symbol)
  }


  implicit val accelerationWrites  = new Writes[Acceleration] {
    def writes(acceleration: Acceleration) = new JsString(acceleration.toMetersPerSecondSquared + " " + MetersPerSecondSquared.symbol)
  }


  implicit val spatialWrites  = new Writes[Spatial] {
    def writes(spatial: Spatial) =
      Json.toJson(
        "r" -> com.billding.serialization.JsonShit.BillSquants.distance.qvWrites.writes(spatial.r),
        "v" -> com.billding.serialization.JsonShit.BillSquants.velocity.qvWrites.writes(spatial.v),
        "dimensions" -> com.billding.serialization.JsonShit.BillSquants.distance.qvWrites.writes(spatial.dimensions)
      )
  }

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

  implicit val laneWrites  = new Writes[Lane] {
    def writes(lane: Lane) =
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