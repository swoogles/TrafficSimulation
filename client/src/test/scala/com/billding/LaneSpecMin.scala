package com.billding

import com.billding.SquantsMatchers._
import com.billding.physics.Spatial
import com.billding.traffic._
import org.scalatest.FlatSpec
import org.scalatest.Matchers._
import squants.motion._
import squants.space.{Kilometers, LengthUnit, Meters}
import squants.time.TimeConversions._

class LaneSpecMin extends  FlatSpec {
  val idm: IntelligentDriverModel = new IntelligentDriverModelImpl
  val speedLimit = KilometersPerHour(150)

  val zeroDimensions: (Double, Double, Double, LengthUnit) = (0, 2, 0, Meters)
  val laneStartingPoint = Spatial.apply((0, 0, 0, Meters))
  val laneEndingPoint = Spatial.apply((1, 0, 0, Kilometers))
  val herdSpeed = 65
  val velocitySpatial = Spatial((0, 0, 0, Meters), (herdSpeed, 0, 0, KilometersPerHour), zeroDimensions)
  val vehicleSource = VehicleSourceImpl(1.seconds, laneStartingPoint, velocitySpatial)


  def createVehicle(
                     pIn1: (Double, Double, Double, LengthUnit),
                     vIn1: (Double, Double, Double, VelocityUnit)): PilotedVehicle = {
    PilotedVehicle.commuter(Spatial(pIn1, vIn1), idm)
  }

  def createVehiclePair(
                         pIn1: (Double, Double, Double, LengthUnit),
                         vIn1: (Double, Double, Double, VelocityUnit),
                         pIn2: (Double, Double, Double, LengthUnit),
                         vIn2: (Double, Double, Double, VelocityUnit)
                       ): (PilotedVehicle, PilotedVehicle) = {
    (createVehicle(pIn1, vIn1),
      createVehicle(pIn2, vIn2))
  }



  // FINALLY got a test that contains this damn NaN issue
  it should "accelerate a car originally at rest" in {
    val originSpatial = Spatial((0, 0, 0, Meters), (0.1, 0, 0, KilometersPerHour))
    val endingSpatial =Spatial((100, 0, 0, Kilometers), (0.1, 0, 0, KilometersPerHour))

    val vehicles = List(
      createVehicle((100, 0, 0, Meters), (0.0, 0, 0, KilometersPerHour))
    )

    val lane = new LaneImpl(vehicles, vehicleSource, originSpatial, endingSpatial)
    val accelerations: List[Acceleration] = Lane.responsesInOneLanePrep(lane, speedLimit)

//    pprint.pprintln(lane)
    val newLane = Lane.update(lane, speedLimit, 0.2.seconds, 0.1.seconds)
//    pprint.pprintln(newLane)
    accelerations.head shouldBe speedingUp
    every(accelerations.tail) shouldBe slowingDown


  }

}
