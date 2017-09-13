package com.billding

import scala.language.postfixOps
import com.billding.physics._
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
  val idm: IntelligentDriverModel = DefaultDriverModel.idm
  val pilotedVehicle = PilotedVehicle.commuter(Spatial(pIn, vIn), idm, destination)

  it should "roundtrip a Spatial" in {
    import com.billding.serialization.JsonShit.spatialFormat
    val testVal = pilotedVehicle.driver.spatial
    val jsonResults = Json.toJson(testVal)
    val result = Json.fromJson(
      jsonResults
    ).get
    result shouldBe testVal
  }

  it should "roundtrip a driver" in {
    import com.billding.serialization.JsonShit.driverFormat
    val driver = pilotedVehicle.driver
    val jsonResults = Json.toJson(driver)
    val result = Json.fromJson(
      jsonResults
    ).get
    result shouldBe driver
  }

  it should "roundtrip a vehicle" in {
    import com.billding.serialization.JsonShit.vehicleFormat
    val vehicle = pilotedVehicle.vehicle
    val jsonResults = Json.toJson(vehicle)
    val result = Json.fromJson(
      jsonResults
    ).get
    result shouldBe vehicle
  }

  it should "roundtrip a pilotedVehicle" in {
    import com.billding.serialization.JsonShit.pilotedVehicleFormat
    val jsonResults = Json.toJson(pilotedVehicle)
    pprint.pprintln(jsonResults)
    val result = Json.fromJson(
      jsonResults
    ).get
    println("Goal: " )
    pprint.pprintln(pilotedVehicle)
    println("Result: " )
    pprint.pprintln(result)
    result shouldBe pilotedVehicle
  }

  it should "serialize a Lane" in {
    val idm: IntelligentDriverModel = new IntelligentDriverModelImpl
    val speedLimit = KilometersPerHour(150)

    val zeroDimensions: (Double, Double, Double, LengthUnit) = (0, 2, 0, Meters)
    val laneStartingPoint = Spatial.BLANK
    val laneEndingPoint = Spatial.apply((1, 0, 0, Kilometers))
    val herdSpeed = 65
    val velocitySpatial = Spatial((0, 0, 0, Meters), (herdSpeed, 0, 0, KilometersPerHour), zeroDimensions)
    val vehicleSource = VehicleSourceImpl(1.seconds, laneStartingPoint, velocitySpatial)

    val emptyLane = LaneImpl(Nil, vehicleSource, laneStartingPoint, laneEndingPoint)
    import com.billding.serialization.JsonShit.laneWrites
        Json.toJson(emptyLane)
  }

  val zeroDimensions: (Double, Double, Double, LengthUnit) = (0, 2, 0, Meters)
  val laneStartingPoint = Spatial.BLANK
  val originSpatial = laneStartingPoint
  val laneEndingPoint = Spatial.apply((1, 0, 0, Kilometers))
  val endingSpatial = laneEndingPoint

  val speed = KilometersPerHour(50)
  val street = Street(Seconds(1), laneStartingPoint, laneEndingPoint, speed, 3)

  it should "serialize a Street" in {

    import com.billding.serialization.JsonShit.streetWrites
        Json.toJson(street)

  }

  val speedLimit = KilometersPerHour(150)
  val canvasDimensions: (Length, Length) = (Kilometers(1), Kilometers(1))

  it should "serialize a whole scene" in {
    val vehicleSource = VehicleSourceImpl(Seconds(1), originSpatial, endingSpatial)
    val lane = new LaneImpl(List(pilotedVehicle), vehicleSource, originSpatial, endingSpatial)
    val street = Street(List(lane), originSpatial, endingSpatial, Seconds(1))
    val t = Seconds(500)
    implicit val dt = Milliseconds(500)
    val scene: Scene = SceneImpl(
      List(street),
      t,
      dt,
      speedLimit,
      canvasDimensions
    )

//    import com.billding.serialization.JsonShit.sceneWrites
//        Json.toJson(scene)
  }

}
