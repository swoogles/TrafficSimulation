package com.billding

import scala.language.postfixOps
import com.billding.physics._
import com.billding.serialization.{BillSquants, TrafficJson}
import com.billding.traffic._
import org.scalatest.FlatSpec
import org.scalatest.Matchers._
import squants.{Length, Quantity, QuantityVector}
import squants.motion._
import squants.space.{Kilometers, LengthUnit, Meters}
import squants.time.TimeConversions._
import play.api.libs.json.{JsValue, Json}
import squants.time.{Milliseconds, Seconds}

class JsonSpec extends FlatSpec{
  val destination: SpatialImpl = Spatial.apply((1, 0, 0, Kilometers))

  val pIn: (Double, Double, Double, LengthUnit) = (0, 0, 0, Meters)
  val vIn: (Double, Double, Double, VelocityUnit) = (120, 0, 0, KilometersPerHour)
  val idm: IntelligentDriverModelImpl = DefaultDriverModel.idm
  val pilotedVehicle = PilotedVehicle.commuter(Spatial(pIn, vIn), idm, destination)

  val json = TrafficJson()(
    BillSquants.distance.format,
    BillSquants.distance.formatQv,
    BillSquants.mass.format,
    BillSquants.time.format,
    BillSquants.acceleration.format,
    BillSquants.velocity.format,
    BillSquants.velocity.formatQv
  )

  it should "roundtrip a Spatial" in {
    import json.spatialFormat
    val testVal = pilotedVehicle.driver.spatial
    val jsonResults = Json.toJson(testVal)
    val result = Json.fromJson(
      jsonResults
    ).get
    result shouldBe testVal
  }

  it should "roundtrip a driver" in {
    import json.driverFormat
    val driver = pilotedVehicle.driver
    val jsonResults = Json.toJson(driver)
    val result = Json.fromJson(
      jsonResults
    ).get
    result shouldBe driver
  }

  it should "roundtrip a vehicle" in {
    import json.vehicleFormat
    val vehicle = pilotedVehicle.vehicle
    val jsonResults = Json.toJson(vehicle)
    val result = Json.fromJson(
      jsonResults
    ).get
    result shouldBe vehicle
  }

  it should "roundtrip a pilotedVehicle" in {
    import json.pilotedVehicleFormat
    val jsonResults = Json.toJson(pilotedVehicle)
    val result = Json.fromJson(
      jsonResults
    ).get
    result shouldBe pilotedVehicle
  }

  val zeroDimensions: (Double, Double, Double, LengthUnit) = (0, 2, 0, Meters)
  val laneStartingPoint = Spatial.BLANK
  val originSpatial = laneStartingPoint
  val laneEndingPoint = Spatial.apply((1, 0, 0, Kilometers))
  val endingSpatial = laneEndingPoint

  val speed = KilometersPerHour(50)
  val street = Street(Seconds(1), laneStartingPoint, laneEndingPoint, speed, 3)

  val herdSpeed = 65

  val velocitySpatial = Spatial((0, 0, 0, Meters), (herdSpeed, 0, 0, KilometersPerHour), zeroDimensions)
  val vehicleSource = VehicleSourceImpl(1.seconds, laneStartingPoint, velocitySpatial)

  val lane = LaneImpl(List(pilotedVehicle), vehicleSource, laneStartingPoint, laneEndingPoint)

  it should "roundtrip a VehicleSource" in {
    import json.vehicleSourceFormat
    val jsonResults = Json.toJson(vehicleSource)
    val result = Json.fromJson(
      jsonResults
    ).get
    result shouldBe vehicleSource
  }
  it should "roundtrip a Lane" in {
    import json.laneFormat
    val jsonResults = Json.toJson(lane)
    val result = Json.fromJson(
      jsonResults
    ).get
    result shouldBe lane
  }

  it should "roundtrip a street" in {
    import json.streetFormat
    val jsonResults = Json.toJson(street)
    val result = Json.fromJson(
      jsonResults
    ).get
    println("Goal: " )
    pprint.pprintln(street)
    println("Result: " )
    pprint.pprintln(result)
    result shouldBe street
  }

  val speedLimit = KilometersPerHour(150)
  val canvasDimensions: (Length, Length) = (Kilometers(1), Kilometers(1))

  it should "serialize a whole scene" in {
    import json.sceneFormats
    val street = StreetImpl(List(lane), originSpatial, endingSpatial, Seconds(1))
    val t = Seconds(500)
    implicit val dt = Milliseconds(500)
    val scene: SceneImpl = SceneImpl(
      List(street),
      t,
      dt,
      speedLimit,
      canvasDimensions
    )

    val jsonResults = Json.toJson(scene)
    val result = Json.fromJson(
      jsonResults
    ).get
    println("Goal: " )
    pprint.pprintln(scene)
    println("Result: " )
    pprint.pprintln(result)
    result shouldBe scene
  }

}
