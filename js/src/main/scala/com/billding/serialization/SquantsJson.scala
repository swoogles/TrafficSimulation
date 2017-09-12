package com.billding.serialization

import squants.mass.{Kilograms, Mass}
import squants.{Acceleration, Mass, Quantity, QuantityVector, Time}
import squants.motion._
import squants.space.{Length, Meters}
import squants.time.{Milliseconds, Time, TimeConversions}
import play.api.libs.json.Reads.JsStringReads
import play.api.libs.json._
import play.api.libs.functional.syntax._

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
