package com.billding.serialization

import com.billding.physics.{Spatial, SpatialImpl}
import com.billding.traffic._
import play.api.libs.json._
import squants.mass.{Kilograms, Mass}
import squants.{Acceleration, Mass, Quantity, QuantityVector, Time}
import squants.motion._
import squants.space.{Length, Meters}
import squants.time.{Milliseconds, Time, TimeConversions}
import play.api.libs.json.Reads.JsStringReads
import play.api.libs.json._
import play.api.libs.functional.syntax._


object JsonShit {
  sealed trait BillSquants[T <: Quantity[T]] {
    val fromJsString: JsString => T
    val toJsString: T => JsString

    implicit val singleWrites  = new Writes[T] {
      override def writes(o: T): JsValue = toJsString(o)
    }

    implicit val singleReads: Reads[T] = JsStringReads.map(fromJsString)

    implicit val format: Format[T] =
      Format(singleReads, singleWrites)

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

    implicit val qvWrites: Writes[QuantityVector[T]] = new Writes[QuantityVector[T]] {
      def writes(quantityVector: QuantityVector[T]) =
        Json.toJson(
          quantityVector.coordinates.map {
            piece => singleWrites.writes(piece)
          }
        )
    }

    implicit val formatQv: Format[QuantityVector[T]] =
      Format(generalReads, qvWrites)
  }
  case class BillSquantsImpl[T <: Quantity[T]](fromJsString: JsString=>T, toJsString: T =>JsString) extends BillSquants[T]

  object BillSquants {
    val distanceConverterJs = (s: JsString) =>
      Length.apply(s.value).get

    val velocityConverterJs = (s: JsString) =>
      Velocity.apply(s.value).get

    val accelerationConverterJs = (s: JsString) =>
      Acceleration.apply(s.value).get

    val timeConverterJs = (s: JsString) =>
      new TimeConversions.TimeStringConversions(s.value).toTime.get

    val massConverterJs = (s: JsString) =>
      Mass(s.value).get

    val distanceToJsString = (distance: Distance) => new JsString(distance.toMeters + " " + Meters.symbol)
    val velocityToJsString =  (velocity: Velocity) => new JsString(velocity.toMetersPerSecond + " " + MetersPerSecond.symbol)
    val accelerationToJsString  = (acceleration: Acceleration) => new JsString(acceleration.toMetersPerSecondSquared + " " + MetersPerSecondSquared.symbol)
    val timeToJsString = (time: Time) => new JsString(time.toMilliseconds + " " + Milliseconds.symbol)
    val massToJsString = (mass: Mass) => new JsString(mass.toKilograms + " " + Kilograms.symbol)

    implicit val distance = BillSquantsImpl(distanceConverterJs, distanceToJsString)
    implicit val velocity = BillSquantsImpl(velocityConverterJs, velocityToJsString)
    implicit val acceleration: BillSquants[Acceleration] = BillSquantsImpl(accelerationConverterJs, accelerationToJsString)
    implicit val time = BillSquantsImpl(timeConverterJs, timeToJsString)
    implicit val mass = BillSquantsImpl(massConverterJs, massToJsString)
  }

  implicit val spatialWrites  = new Writes[SpatialImpl] {
    def writes(spatial: SpatialImpl) =
      Json.toJson(
        "r" -> com.billding.serialization.JsonShit.BillSquants.distance.qvWrites.writes(spatial.r),
        "v" -> com.billding.serialization.JsonShit.BillSquants.velocity.qvWrites.writes(spatial.v),
        "dimensions" -> com.billding.serialization.JsonShit.BillSquants.distance.qvWrites.writes(spatial.dimensions)
      )
  }
  implicit val localAccelerationWrites = BillSquants.acceleration.singleWrites
  implicit val localAccelerationReads = BillSquants.acceleration.singleReads
  implicit val localMassReads = BillSquants.mass.singleReads
  implicit val localVelocityWrites = BillSquants.velocity.singleWrites
  implicit val localDistanceWrites = BillSquants.distance.singleWrites
  implicit val distanceQvReads = BillSquants.distance.generalReads
  implicit val velocityQvReads = BillSquants.velocity.generalReads
  implicit val distanceReads = BillSquants.distance.singleReads
  implicit val localMassWrites = BillSquants.mass.singleWrites
  import BillSquants.time.singleWrites

  implicit val spatialReads: Reads[SpatialImpl] = (
    (JsPath \ "r").read[QuantityVector[Distance]] and
      (JsPath \ "v").read[QuantityVector[Velocity]] and
      (JsPath \ "dimensions").read[QuantityVector[Distance]]
    )(SpatialImpl.apply _)

//  val spatialRead: Reads[Spatial] = (JsPath \ "spatial").read[Spatial]
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
