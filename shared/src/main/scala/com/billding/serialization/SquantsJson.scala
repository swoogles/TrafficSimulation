package com.billding.serialization

import scala.util.Try
import squants.mass.{Kilograms, Mass}
import squants.{
  Acceleration,
  Mass,
  Quantity,
  QuantityVector,
  Time,
  UnitOfMeasure
}
import squants.motion._
import squants.space.{Length, Meters}
import squants.time.{Milliseconds, Time, TimeConversions}
import play.api.libs.json.Reads.JsStringReads
import play.api.libs.json._

sealed trait BillSquants[T <: Quantity[T]] {
  def fromJsString: JsString => T // Why you gotta be a def, eh?
  val toJsString: T => JsString

  implicit val singleWrites = new Writes[T] {
    override def writes(o: T): JsValue = toJsString(o)
  }

  implicit val singleReads: Reads[T] = JsStringReads.map(fromJsString)

  implicit val format: Format[T] =
    Format(singleReads, singleWrites)

  // TODO: This is fairly confusing/unsafe shit. Take a close look at it.
  implicit val generalReads = new Reads[QuantityVector[T]] {
    def reads(jsValue: JsValue): JsResult[QuantityVector[T]] = {
      val foo: Seq[T] =
        jsValue
          .as[Seq[JsValue]]
          .map(singleReads.reads(_))
          .map(jsRes => jsRes.get)

      JsSuccess(
        QuantityVector.apply(foo: _*)
      )
    }
  }

  implicit val qvWrites: Writes[QuantityVector[T]] =
    new Writes[QuantityVector[T]] {
      def writes(quantityVector: QuantityVector[T]) =
        Json.toJson(
          quantityVector.coordinates.map { piece =>
            singleWrites.writes(piece)
          }
        )
    }

  implicit val formatQv: Format[QuantityVector[T]] =
    Format(generalReads, qvWrites)
}

case class BillSquantsImpl[T <: Quantity[T]](
    fromJsStringTry: (String => Try[T]),
    unit: UnitOfMeasure[T])
    extends BillSquants[T] {

  val toJsString: T => JsString =
    (amount: T) => new JsString((amount to unit) + " " + unit.symbol)

  def fromJsString: JsString => T =
    (s: JsString) => fromJsStringTry(s.value).get
}

/*
  I would like for this class to have its units decided by property files, rather than being hardcoded here.
  Then it would be possible to eg. Switch between metric and imperial units.
 */
object BillSquants {

  val lengthUnit = Meters
  val velocityUnit = KilometersPerHour
  val accelerationUnit = MetersPerSecondSquared
  val massUnit = Kilograms
  val timeUnit = Milliseconds

  implicit val distance = BillSquantsImpl(Length.apply, lengthUnit)
  implicit val velocity = BillSquantsImpl(Velocity.apply, velocityUnit)
  implicit val acceleration =
    BillSquantsImpl(Acceleration.apply, accelerationUnit)
  implicit val time = BillSquantsImpl(
    (s: String) => new TimeConversions.TimeStringConversions(s).toTime,
    timeUnit)
  implicit val mass = BillSquantsImpl(Mass.apply, massUnit)

}
