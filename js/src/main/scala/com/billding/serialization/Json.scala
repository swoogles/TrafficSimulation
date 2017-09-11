package com.billding.serialization

import com.billding.physics.Spatial
import play.api.libs.json.{JsString, Json, Writes}
import squants.QuantityVector
import squants.motion.{Distance, MetersPerSecond, Velocity}
import squants.space.Meters

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

}
