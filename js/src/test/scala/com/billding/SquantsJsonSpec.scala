package com.billding

import com.billding.serialization.{BillSquants, BillSquants}
import org.scalatest.Matchers._
import org.scalatest.{Assertion, FlatSpec}
import play.api.libs.json.{JsString, Json}
import squants.mass.Kilograms
import squants.motion.{KilometersPerHour, MetersPerSecond, MetersPerSecondSquared}
import squants.space.{Kilometers, Meters}
import squants.time.Seconds
import squants.{Quantity, QuantityVector}

import scala.language.postfixOps

class SquantsJsonSpec extends FlatSpec {
  it should "roundtrip serialize a distance" in {
    boilerTest(BillSquants.distance, Meters(10), JsString("10.0 m"))
    boilerTest(BillSquants.distance, Kilometers(.01), JsString("10.0 m"))
  }

  it should "roundtrip serialize a time" in {
    boilerTest(BillSquants.time, Seconds(10), JsString("10000.0 ms"))
  }

  it should "roundtrip serialize a velocity" in {
    boilerTest(BillSquants.velocity, MetersPerSecond(10), JsString("36.0 km/h"))
    boilerTest(BillSquants.velocity, KilometersPerHour(36), JsString("36.0 km/h"))
  }

  it should "roundtrip serialize a mass" in {
    boilerTest(BillSquants.mass, Kilograms(10), JsString("10.0 kg"))
  }

  it should "roundtrip serialize an acceleration" in {
    // TODO Make sure ^2 isn't a problem in the String result.
    boilerTest(BillSquants.acceleration, MetersPerSecondSquared(10), JsString("10.0 m/s\u00b2"))
  }

  it should "roundtrip serialize a distance Quantity Vector" in {
    boilerTestQv(BillSquants.distance,
      QuantityVector(
        Meters(10),
        Meters(30),
        Meters(7)
      )
    )
  }

  it should "roundtrip serialize a Velocity Quantity Vector" in {
    boilerTestQv(BillSquants.velocity,
      QuantityVector(
        MetersPerSecond(10),
        MetersPerSecond(30),
        MetersPerSecond(7)
      )
    )
  }

  def boilerTest[T <: Quantity[T]](billSquants: BillSquants[T], testVal: T, serializedTarget: JsString): Assertion = {
    import billSquants.format
    val serializedJson = Json.toJson(testVal)
    pprint.pprintln(serializedJson)
    serializedJson shouldBe serializedTarget
    val result = Json.fromJson(
      serializedJson
    ).get
    result shouldBe testVal
  }

  def boilerTestQv[T <: Quantity[T]](billSquants: BillSquants[T], testVal: QuantityVector[T]): Assertion = {
    import billSquants.formatQv
    val serializedJson = Json.toJson(testVal)
    pprint.pprintln(serializedJson)
    val result = Json.fromJson(
      serializedJson
    ).get
    result shouldBe testVal
  }

}
