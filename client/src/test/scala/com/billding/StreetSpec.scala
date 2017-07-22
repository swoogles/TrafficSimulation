package com.billding

import scala.language.postfixOps
import com.billding.physics._
import com.billding.traffic.Street
import org.scalatest.FlatSpec
import org.scalatest.Matchers._
import squants.motion._
import squants.space.{Kilometers, Meters}
import squants.time.Seconds
import squants.time.TimeConversions._

/**
  * Created by bfrasure on 7/21/17.
  */
class StreetSpec extends FlatSpec {
  val beginning = Spatial.apply((0, 0, 0, Meters))
  val destination = beginning.move(East, Meters(100))

  val beginningLane2 = Spatial.apply((0, -3, 0, Meters))
  val destinationLane2 = beginningLane2.move(East, Meters(100))

  val beginningLane3 = Spatial.apply((0, -6, 0, Meters))
  val destinationLane3 = beginningLane3.move(East, Meters(100))
  it should "make a street of multiple lanes" in {
    val street = Street(Seconds(1), beginning, destination, South, 3)
    street.lanes(1).beginning shouldBe beginningLane2
    street.lanes(1).end shouldBe destinationLane2
    street.lanes(2).beginning shouldBe beginningLane3
    street.lanes(2).end shouldBe destinationLane3
  }

}
