package com.billding.serialization

import scala.util.Try
import squants.mass.{Kilograms, Mass, MassUnit}
import squants.{Quantity, QuantityVector, UnitOfMeasure}
import squants.motion._
import squants.space.{Length, LengthUnit, Meters}
import squants.time.{Milliseconds, Time, TimeConversions, TimeUnit}
import play.api.libs.json.Reads.JsStringReads
import play.api.libs.json._

sealed trait BillSquants[T <: Quantity[T]] {
  def fromJsString: JsString => T // Why you gotta be a def, eh?
  val toJsString: T => JsString

  implicit val singleWrites: Writes[T] = (o: T) => toJsString(o)

  implicit val singleReads: Reads[T] = JsStringReads.map(fromJsString)

  implicit val format: Format[T] =
    Format(singleReads, singleWrites)

  // TODO: This is fairly confusing/unsafe shit. Take a close look at it.
  implicit val generalReads: Reads[QuantityVector[T]] = (jsValue: JsValue) => {
    val foo: Seq[T] =
      jsValue
        .as[Seq[JsValue]]
        .map(singleReads.reads)
        .map(jsRes => jsRes.get)

    JsSuccess(
      QuantityVector.apply(foo: _*)
    )
  }

  implicit val qvWrites: Writes[QuantityVector[T]] =
    (quantityVector: QuantityVector[T]) =>
      Json.toJson(
        quantityVector.coordinates.map { piece =>
          singleWrites.writes(piece)
        }
    )

  implicit val formatQv: Format[QuantityVector[T]] =
    Format(generalReads, qvWrites)
}

case class BillSquantsImpl[T <: Quantity[T]](
    fromJsStringTry: (String => Try[T]),
    unit: UnitOfMeasure[T])
    extends BillSquants[T] {

  val toJsString: T => JsString =
    (amount: T) => JsString((amount to unit) + " " + unit.symbol)

  def fromJsString: JsString => T =
    (s: JsString) => fromJsStringTry(s.value).get
}

/*
  I would like for this class to have its units decided by property files, rather than being hardcoded here.
  Then it would be possible to eg. Switch between metric and imperial units.
 */
object BillSquants {

  val lengthUnit: LengthUnit = Meters
  val velocityUnit: VelocityUnit = KilometersPerHour
  val accelerationUnit: AccelerationUnit = MetersPerSecondSquared
  val massUnit: MassUnit = Kilograms
  val timeUnit: TimeUnit = Milliseconds

  implicit val distance: BillSquantsImpl[Distance] =
    BillSquantsImpl(Length.apply, lengthUnit)
  implicit val velocity: BillSquantsImpl[Velocity] =
    BillSquantsImpl(Velocity.apply, velocityUnit)
  implicit val acceleration: BillSquantsImpl[Acceleration] =
    BillSquantsImpl(Acceleration.apply, accelerationUnit)
  implicit val time: BillSquantsImpl[Time] = BillSquantsImpl(
    (s: String) => new TimeConversions.TimeStringConversions(s).toTime,
    timeUnit)
  implicit val mass: BillSquantsImpl[Mass] =
    BillSquantsImpl(Mass.apply, massUnit)

}
