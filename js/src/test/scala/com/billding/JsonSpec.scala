package com.billding

import scala.language.postfixOps
import com.billding.physics._
import org.scalatest.FlatSpec
import org.scalatest.Matchers._
import shared.Orientation.{East, North, South, West}
import squants.Quantity
import squants.motion._
import squants.space.{Kilometers, Meters}
import squants.time.TimeConversions._

import org.scalatest.FlatSpec

class JsonSpec extends FlatSpec{
  val destination: SpatialImpl = Spatial.apply((1, 0, 0, Kilometers))

  it should "serialize good" in {
    import play.api.libs.json.Json
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

}
