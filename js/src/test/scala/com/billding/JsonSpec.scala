package com.billding

import scala.language.postfixOps
import com.billding.physics._
import com.billding.traffic.{IntelligentDriverModel, IntelligentDriverModelImpl, PilotedVehicle}
import org.scalatest.FlatSpec
import org.scalatest.Matchers._
import shared.Orientation.{East, North, South, West}
import squants.Quantity
import squants.motion._
import squants.space.{Kilometers, LengthUnit, Meters}
import squants.time.TimeConversions._
import org.scalatest.FlatSpec
import play.api.libs.json.Json

class JsonSpec extends FlatSpec{
  val destination: SpatialImpl = Spatial.apply((1, 0, 0, Kilometers))

  it should "serialize good" in {
    import com.billding.serialization.JsonShit.qvWrites
    import com.billding.serialization.JsonShit.qvVelocityWrites
    pprint.pprintln(Json.toJson(destination.r))
    println(
      Json.prettyPrint(
        Json.toJson(destination.r)
      )
    )

    pprint.pprintln(Json.toJson(destination.v))
    println(
      Json.prettyPrint(
        Json.toJson(destination.v)
      )
    )

    import com.billding.serialization.JsonShit.spatialWrites
    println(
      Json.prettyPrint(
        Json.toJson(destination)
      )
    )
  }

  it should "serialize vehicles" in {
    val pIn: (Double, Double, Double, LengthUnit) = (0, 0, 0, Kilometers)
    val vIn: (Double, Double, Double, VelocityUnit) = (120, 0, 0, KilometersPerHour)
    val idm: IntelligentDriverModel = new IntelligentDriverModelImpl
    val pilotedVehicle = PilotedVehicle.commuter(Spatial(pIn, vIn), idm, destination)
    import com.billding.serialization.JsonShit.pilotedVehicleWrites
    println(
      Json.prettyPrint(
        Json.toJson(pilotedVehicle)
      )
    )
  }

}
