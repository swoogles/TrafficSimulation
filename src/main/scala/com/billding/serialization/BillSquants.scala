package com.billding.serialization

import scala.util.Try
import squants.mass.{Kilograms, Mass, MassUnit}
import squants.{Quantity, QuantityVector, UnitOfMeasure}
import squants.space.{Length, LengthUnit, Meters}
import squants.time.{Milliseconds, Time, TimeConversions, TimeUnit}
import play.api.libs.json.Reads.JsStringReads
import play.api.libs.json._
import squants.motion.{Acceleration, AccelerationUnit, Distance, KilometersPerHour, MetersPerSecondSquared, Velocity, VelocityUnit}

case class BillSquants[T <: Quantity[T]](
    fromJsStringTry: (String => Try[T]),
    unit: UnitOfMeasure[T]
) {

  val toJsString: T => JsString =
    (amount: T) => JsString((amount to unit) + " " + unit.symbol)

  def fromJsString: JsString => T =
    (s: JsString) => fromJsStringTry(s.value).get

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

  implicit val distance: BillSquants[Distance] =
    BillSquants(Length.apply, lengthUnit)
  implicit val velocity: BillSquants[Velocity] =
    BillSquants(Velocity.apply, velocityUnit)
  implicit val acceleration: BillSquants[Acceleration] =
    BillSquants(Acceleration.apply, accelerationUnit)
  implicit val time: BillSquants[Time] = BillSquants(
    (s: String) => new TimeConversions.TimeStringConversions(s).toTime,
    timeUnit)
  implicit val mass: BillSquants[Mass] =
    BillSquants(Mass.apply, massUnit)

}
