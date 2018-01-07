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

  // TODO: This is fairly confusing shit. Take a close look at it.
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
  private def mkString[A <: Quantity[A]](
      unit: UnitOfMeasure[A]): A => JsString =
    (amount: A) => new JsString((amount to unit) + " " + unit.symbol)

  val toJsString: T => JsString = mkString(unit)

  private def quantityConverterJs[A <: Quantity[A]](
      quantityCreator: (String => Try[A])): (JsString) => A =
    (s: JsString) => quantityCreator(s.value).get

  def fromJsString: JsString => T = quantityConverterJs(fromJsStringTry)
}
// Then new instances only need to pass the UnitOfMeasure in.
//case class BillSquantsImpl[T <: Quantity[T]](fromJsString: JsString=>T, toJsString: T =>JsString) extends BillSquants[T]

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
  def mkString[A <: Quantity[A]](unit: UnitOfMeasure[A]): A => JsString =
    (amount: A) => new JsString((amount to unit) + " " + unit.symbol)

  implicit val distance = BillSquantsImpl(Length.apply, lengthUnit)
  implicit val velocity = BillSquantsImpl(Velocity.apply, velocityUnit)
  implicit val acceleration =
    BillSquantsImpl(Acceleration.apply, accelerationUnit)
  implicit val time = BillSquantsImpl(
    (s: String) => new TimeConversions.TimeStringConversions(s).toTime,
    timeUnit)
  implicit val mass = BillSquantsImpl(Mass.apply, massUnit)

}
