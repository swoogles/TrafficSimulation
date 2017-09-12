package com.billding

import scala.language.postfixOps
import com.billding.physics._
import com.billding.serialization.JsonShit.BillSquants
import com.billding.traffic._
import org.scalatest.FlatSpec
import org.scalatest.Matchers._
import squants.{Length, Quantity, QuantityVector}
import squants.motion._
import squants.space.{Kilometers, LengthUnit, Meters}
import squants.time.TimeConversions._
import play.api.libs.json.{JsArray, JsString, JsValue, Json}
import squants.mass.Kilograms
import squants.time.{Milliseconds, Seconds, Time}

class SquantsJsonSpec extends FlatSpec {
  it should "roundtrip serialize a distance" in {
    boilerTest(BillSquants.distance, Meters(10), JsString("10 m"))
  }

  it should "roundtrip serialize a time" in {
    boilerTest(BillSquants.time, Seconds(10), JsString("10000 ms"))
  }

  it should "roundtrip serialize a velocity" in {
    boilerTest(BillSquants.velocity, MetersPerSecond(10), JsString("10 m/s"))
  }

  it should "roundtrip serialize a mass" in {
    boilerTest(BillSquants.mass, Kilograms(10), JsString("10 kg"))
  }

  it should "roundtrip serialize an acceleration" in {
    // TODO Make sure ^2 isn't a problem in the String result.
    boilerTest(BillSquants.acceleration, MetersPerSecondSquared(10), JsString("10 m/s\u00b2"))
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

  def boilerTest[T <: Quantity[T]](billSquants: BillSquants[T], testVal: T, serializedTarget: JsString) = {
    import billSquants.format
    val serializedJson = Json.toJson(testVal)
    pprint.pprintln(serializedJson)
    serializedJson shouldBe serializedTarget
    val result = Json.fromJson(
      serializedJson
    ).get
    result shouldBe testVal
  }

  def boilerTestQv[T <: Quantity[T]](billSquants: BillSquants[T], testVal: QuantityVector[T]) = {
    import billSquants.formatQv
    val serializedJson = Json.toJson(testVal)
    pprint.pprintln(serializedJson)
    val result = Json.fromJson(
      serializedJson
    ).get
    result shouldBe testVal
  }

}
