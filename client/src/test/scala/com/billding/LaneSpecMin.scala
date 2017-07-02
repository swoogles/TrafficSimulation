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
    PilotedVehicle.commuter(Spatial(pIn1, vIn1), idm, laneEndingPoint)
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
  it should "copy a vehicle with an at-rest spacial" in {
    val atRestSpatial =Spatial((100, 0, 0, Meters), (0.0, 0, 0, KilometersPerHour))
    val pilotedVehicle = PilotedVehicle.commuter(atRestSpatial, new IntelligentDriverModelImpl, laneEndingPoint)
    pprint.pprintln(pilotedVehicle.spatial)
    val acceleratedVehicle = pilotedVehicle.accelerateAlongCurrentDirection(0.1.seconds, MetersPerSecondSquared(1))
    pprint.pprintln(acceleratedVehicle.spatial)
  }

}
