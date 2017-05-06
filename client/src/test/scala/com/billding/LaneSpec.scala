package com.billding

import org.scalatest.FlatSpec
import squants.motion.{Acceleration, KilometersPerHour, VelocityUnit}
import squants.space.{Kilometers, LengthUnit, Meters}

/**
  * Created by bfrasure on 5/6/17.
  */
class LaneSpec extends  FlatSpec {
  val idm: IntelligentDriverModel = new IntelligentDriverModelImpl
  val speedLimit = KilometersPerHour(150)

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


  it should "make all vehicles respond appropriately" in {
    val (drivenVehicle1, drivenVehicle2) = createVehiclePair(
      (200, 0, 0, Meters), (40, 0, 0, KilometersPerHour),
      (180, 0, 0, Meters), (120, 0, 0, KilometersPerHour)
    )

    val (drivenVehicle3, drivenVehicle4) = createVehiclePair(
      (100, 0, 0, Meters), (70, 0, 0, KilometersPerHour),
      (80, 0, 0, Meters), (150, 0, 0, KilometersPerHour)
    )
    val vehicles = List(
      drivenVehicle1, drivenVehicle2, drivenVehicle3, drivenVehicle4
    )
    val acccelerations: List[Acceleration] = Lane.responsesInOneLanePrep(vehicles, speedLimit)
    acccelerations.foreach { x => println("accceleration: " + x) }
  }

  it should "make all vehicles accelerate from a stop together" in {
    val (drivenVehicle1, drivenVehicle2) = createVehiclePair(
      (200, 0, 0, Meters), (40, 0, 0, KilometersPerHour),
      (180, 0, 0, Meters), (120, 0, 0, KilometersPerHour)
    )

    val (drivenVehicle3, drivenVehicle4) = createVehiclePair(
      (100, 0, 0, Meters), (70, 0, 0, KilometersPerHour),
      (80, 0, 0, Meters), (150, 0, 0, KilometersPerHour)
    )
    val vehicles = List(
      createVehicle((100, 0, 0, Meters), (1, 0, 0, KilometersPerHour)),
      createVehicle((95, 0, 0, Meters), (0, 0, 0, KilometersPerHour)),
      createVehicle((90, 0, 0, Meters), (0, 0, 0, KilometersPerHour))
    )
    val acccelerations: List[Acceleration] = Lane.responsesInOneLanePrep(vehicles, speedLimit)
    acccelerations.foreach { x => println("accceleration together: " + x) }
  }

}
