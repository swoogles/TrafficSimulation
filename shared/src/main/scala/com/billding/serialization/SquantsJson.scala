package com.billding.serialization

import squants.mass.{Kilograms, Mass}
import squants.{Acceleration, Mass, Quantity, QuantityVector, Time, UnitOfMeasure}
import squants.motion._
import squants.space.{Length, Meters}
import squants.time.{Milliseconds, Time, TimeConversions}
import play.api.libs.json.Reads.JsStringReads
import play.api.libs.json._

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
      val der: QuantityVector[T] = QuantityVector.apply(foo: _*)
      JsSuccess(
        der
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

import com.typesafe.config.ConfigFactory.parseString
import pureconfig.loadConfig
import pureconfig.module.squants._

case class HowConfiguration(velocityUnit: String)

class ApplicationConfig() {
  val conf = parseString("""
    {
      velocity-unit: "km/h"
    }
  """)

  // conf: com.typesafe.config.Config = Config(SimpleConfigObject({"far":"42.195 km","hot":"56.7° C"}))

  val config = loadConfig[HowConfiguration](conf).right.get
  println("config: " + config)
  println("config velocity unit: " + config.velocityUnit)

  println("test velocity: " + Velocity("0 " + config.velocityUnit))

}

/*
  I would like for this class to have its units decided by property files, rather than being hardcoded here.
  Then it would be possible to eg. Switch between metric and imperial units.
 */
object BillSquants {

  val distanceConverterJs: (JsString) => Distance = (s: JsString) =>
    Length.apply(s.value).get

  val velocityConverterJs = (s: JsString) =>
    Velocity.apply(s.value).get

  val accelerationConverterJs = (s: JsString) =>
    Acceleration.apply(s.value).get

  val timeConverterJs = (s: JsString) =>
    new TimeConversions.TimeStringConversions(s.value).toTime.get

  val massConverterJs = (s: JsString) =>
    Mass(s.value).get

  val conf = parseString("""
    {
      velocity-unit: "km/h"
    }
  """)

  // conf: com.typesafe.config.Config = Config(SimpleConfigObject({"far":"42.195 km","hot":"56.7° C"}))

  val config = new ApplicationConfig().config

  println("test velocity: " + Velocity("0 " + config.velocityUnit))

  val lengthUnit = Meters
  val velocityUnit = KilometersPerHour
  val accelerationUnit = MetersPerSecondSquared
  val massUnit = Kilograms
  val timeUnit = Milliseconds

  // Fucking awesome.
  def mkString[A <: Quantity[A], B <: UnitOfMeasure[A]](amount: A, unit: B): JsString = {
    new JsString( (amount to unit) + " " + unit.symbol)
  }

  val distanceToJsString =
    (distance: Distance) =>
      mkString(distance, lengthUnit)

  val velocityToJsString =
    (velocity: Velocity) =>
      mkString(velocity, velocityUnit)

  val accelerationToJsString =
    (acceleration: Acceleration) =>
      mkString(acceleration, accelerationUnit)

  val timeToJsString =
    (time: Time) =>
      mkString(time, timeUnit)

  val massToJsString =
    (mass: Mass) =>
      mkString(mass, massUnit)

  implicit val distance = BillSquantsImpl(distanceConverterJs, distanceToJsString)
  implicit val velocity = BillSquantsImpl(velocityConverterJs, velocityToJsString)
  implicit val acceleration: BillSquants[Acceleration] = BillSquantsImpl(accelerationConverterJs, accelerationToJsString)
  implicit val time = BillSquantsImpl(timeConverterJs, timeToJsString)
  implicit val mass = BillSquantsImpl(massConverterJs, massToJsString)
}
