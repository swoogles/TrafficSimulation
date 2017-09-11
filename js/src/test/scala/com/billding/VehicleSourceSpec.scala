package com.billding

import org.scalatest.FlatSpec
import squants.motion._
import squants.space.{Kilometers, LengthUnit, Meters}
import org.scalatest.Matchers._
import SquantsMatchers._
import com.billding.physics.Spatial
import com.billding.traffic._
import squants.time.{Milliseconds, Seconds, Time}
import squants.time.TimeConversions._
import squants.space.LengthConversions._

class VehicleSourceSpec extends FlatSpec {
  val idm: IntelligentDriverModel = new IntelligentDriverModelImpl
  val speedLimit = KilometersPerHour(150)

  val zeroDimensions: (Double, Double, Double, LengthUnit) = (0, 2, 0, Meters)
  val laneStartingPoint = Spatial.apply((0, 0, 0, Meters))
  val laneEndingPoint = Spatial.apply((1, 0, 0, Kilometers))
  val spacingInTime = 1.seconds
  val herdSpeed = 65
  val velocitySpatial = Spatial((0, 0, 0, Meters), (herdSpeed, 0, 0, KilometersPerHour), zeroDimensions)
  val vehicleSource = VehicleSourceImpl(1.seconds, laneStartingPoint, velocitySpatial)

  it should "only add 1 vehicle after an appropriate amount of time" in {
    val dt = 0.1.seconds
    val dtTicksPerProduction = (spacingInTime / dt).toInt
    println("dtTicksPerProduction: " + dtTicksPerProduction)
    val ts = Range(0, dtTicksPerProduction).scanLeft(0.5.seconds){ (curT, _) => curT+dt}

//    val moments = Stream.range(0.5.seconds, 1.5.seconds, dt)
    val startT: Time = 0.seconds
    val vehicleProductions: Seq[Option[PilotedVehicle]] = ts.map{ case (curT) => vehicleSource.produceVehicle(curT, dt, laneEndingPoint)}
    vehicleProductions.flatten.size shouldBe 1
  }

}
