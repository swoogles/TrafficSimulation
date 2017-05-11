package com.billding

import org.scalatest.FlatSpec
import squants.motion._
import squants.space.{Kilometers, LengthUnit, Meters}
import org.scalatest.Matchers._
import SquantsMatchers._
import squants.time.{Milliseconds, Seconds}

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
    val accelerations: List[Acceleration] = Lane.responsesInOneLanePrep(vehicles, speedLimit)
  }

  it should "make all vehicles accelerate from a stop together" in {
    val vehicles = List(
      createVehicle((100, 0, 0, Meters), (1, 0, 0, KilometersPerHour)),
      createVehicle((95, 0, 0, Meters), (0, 0, 0, KilometersPerHour)),
      createVehicle((90, 0, 0, Meters), (0, 0, 0, KilometersPerHour))
    )
    val accelerations: List[Acceleration] = Lane.responsesInOneLanePrep(vehicles, speedLimit)
    every(accelerations) shouldBe speedingUp
  }

  it should "make all following vehicles slow down if the lead car is stopped" in {
    val vehicles = List(
      createVehicle((100, 0, 0, Meters), (0.1, 0, 0, KilometersPerHour)),
      createVehicle((80, 0, 0, Meters), (70, 0, 0, KilometersPerHour)),
      createVehicle((60, 0, 0, Meters), (140, 0, 0, KilometersPerHour))
    )
    val accelerations: List[Acceleration] = Lane.responsesInOneLanePrep(vehicles, speedLimit)
    accelerations.head shouldBe speedingUp
    every(accelerations.tail) shouldBe slowingDown
  }

  implicit val speedTolerance = MetersPerSecondSquared(0.01)

  it should "have 1 car decelerate as it approaches a stopped car, and another accelerate away in front of it" in {
    val vehicles = List(
      createVehicle((1000, 0, 0, Meters), (0.1, 0, 0, KilometersPerHour)),
      createVehicle((81, 0, 0, Meters), (0, 0, 0, KilometersPerHour)),
      createVehicle((80, 0, 0, Meters), (0, 0, 0, KilometersPerHour)),
      createVehicle((60, 0, 0, Meters), (140, 0, 0, KilometersPerHour))
    )
    val accelerations: List[Acceleration] = Lane.responsesInOneLanePrep(vehicles, speedLimit)
    val (acc1 :: acc2 :: acc3 :: acc4 :: Nil) = accelerations
    acc1 shouldBe speedingUp
    acc2 shouldBe speedingUp
    acc3 shouldBe maintainingVelocity
    acc4 shouldBe slowingDown
  }

  it should "should only accelerate lead car in bumper-to-bumper traffic" in {
    val vehicles = List(
      createVehicle((100, 0, 0, Meters), (0.1, 0, 0, KilometersPerHour)),
      createVehicle((99, 0, 0, Meters), (0.0, 0, 0, KilometersPerHour)),
      createVehicle((98, 0, 0, Meters), (0.0, 0, 0, KilometersPerHour)),
      createVehicle((97, 0, 0, Meters), (0.0, 0, 0, KilometersPerHour)),
      createVehicle((96, 0, 0, Meters), (0.0, 0, 0, KilometersPerHour))
    )
    val accelerations: List[Acceleration] = Lane.responsesInOneLanePrep(vehicles, speedLimit)
    accelerations.head shouldBe speedingUp
    for (acceleration <- accelerations.tail) {
      assert(acceleration =~ MetersPerSecondSquared(0))
    }
  }

  type basicSpatial = ((Double, Double, Double, DistanceUnit), (Double, Double, Double, VelocityUnit))
  // TODO enact real tests here to ensure correct behavior
  // This might involve reusing code/test code from Spatial tests?
  /*
  These tests need to check p and v.
  At least, at first inspection.
  Is it possible to do all lane updating while ignoring that?
   */
  it should "correctly update all cars in a lane" in {
    val originSpatial = Spatial((0, 0, 0, Meters), (0.1, 0, 0, KilometersPerHour))
    val endingSpatial =Spatial((100, 0, 0, Kilometers), (0.1, 0, 0, KilometersPerHour))

    val vehicles = List(
      createVehicle((100, 0, 0, Meters), (0.1, 0, 0, KilometersPerHour)),
      createVehicle((80, 0, 0, Meters), (70, 0, 0, KilometersPerHour)),
      createVehicle((60, 0, 0, Meters), (140, 0, 0, KilometersPerHour))
    )

    val source = VehicleSourceImpl(Seconds(1), originSpatial)
    val lane = new LaneImpl(vehicles, source, originSpatial, endingSpatial)
    val updatedLane: Lane = Lane.update(lane, speedLimit, Seconds(1), Milliseconds(100))
    val accelerations: List[Acceleration] = Lane.responsesInOneLanePrep(vehicles, speedLimit)
    accelerations.head shouldBe speedingUp
    every(accelerations.tail) shouldBe slowingDown
  }

}