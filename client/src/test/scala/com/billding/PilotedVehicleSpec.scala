package com.billding

import org.scalatest._
import squants.motion._
import squants.space.{Kilometers, LengthUnit, Meters}
import SquantsMatchers._
import com.billding.physics.Spatial
import com.billding.traffic.{IntelligentDriverModel, IntelligentDriverModelImpl, PilotedVehicle}
import org.scalatest.Matchers._

/*
  I think these tests are specific to the IDM, rather than Piloted Vehicle.
 */
class PilotedVehicleSpec extends FlatSpec {
  val idm: IntelligentDriverModel = new IntelligentDriverModelImpl

  def createVehiclePair (
                         pIn1: (Double, Double, Double, LengthUnit),
                         vIn1: (Double, Double, Double, VelocityUnit),
                         pIn2: (Double, Double, Double, LengthUnit),
                         vIn2: (Double, Double, Double, VelocityUnit)
                       ): (PilotedVehicle, PilotedVehicle) = {
    (PilotedVehicle.commuter(Spatial(pIn1, vIn1), idm),
      PilotedVehicle.commuter(Spatial(pIn2, vIn2), idm))
  }

  def accelerationTest (
    pIn1: (Double, Double, Double, LengthUnit),
    vIn1: (Double, Double, Double, VelocityUnit),
    pIn2: (Double, Double, Double, LengthUnit),
    vIn2: (Double, Double, Double, VelocityUnit)
  ): Acceleration = {
    val (drivenVehicle1, drivenVehicle2)  = createVehiclePair( pIn1, vIn1 , pIn2, vIn2 )
    // TODO Get rid of magic value
    drivenVehicle1.reactTo(drivenVehicle2, KilometersPerHour(150))
  }

//  it should "drive cars over a period"

  it should "accelerate a slow car when obstacle is far away" in {
    val res = accelerationTest(
      (0, 0, 0, Kilometers),
      (120, 0, 0, KilometersPerHour),
      (2, 0, 0, Kilometers),
      (40, 0, 0, KilometersPerHour)
    )
    res shouldBe speedingUp
  }

  it should "hold steady when pacing the target car" in {
    val res = accelerationTest(
      (0, 0, 0, Meters),
      (120, 0, 0, KilometersPerHour),
      // I just wack-a-moled my way to this value...
      // It could be better to utilize the T calculation in some way
      (45, 0, 0, Meters),
      (120, 0, 0, KilometersPerHour)
    )
    implicit val tolerance: Acceleration = MetersPerSecondSquared(0.01)

    res shouldBe maintainingVelocity
  }

  it should "accelerate when obstacle is close but moving faster" in {
    val res = accelerationTest(
      (0, 0, 0, Kilometers),
      (120, 0, 0, KilometersPerHour),
      (50, 0, 0, Meters),
      (140, 0, 0, KilometersPerHour)
    )

    res shouldBe speedingUp
  }

  // TODO don't seem to be getting the desire behavior here...
//  it should "decelerate when obstacle is close but moving slower" in {
//    val res = accelerationTest(
//      (0, 0, 0, Kilometers),
//      (120, 0, 0, KilometersPerHour),
//      (50, 0, 0, Meters),
//      (60, 0, 0, KilometersPerHour)
//    )
//    assert( res.toMetersPerSecondSquared < 0)
//  }

  it should "slow down a when obstacle is close, even if it's moving fast" in {
    // Oh shit. Being able to hop to a different scale here is GREAT.
    val res = accelerationTest(
      (0, 0, 0, Meters),
      (120, 0, 0, KilometersPerHour),
      (1, 0, 0, Meters),
      (140, 0, 0, KilometersPerHour)
    )

    res shouldBe slowingDown
  }

  it should "slow down a when obstacle is far away, if it's stopped/slow" in {
    val res = accelerationTest(
      (0, 0, 0, Kilometers),
      (150, 0, 0, KilometersPerHour),
      (1.0, 0, 0, Kilometers),
      (10, 0, 0, KilometersPerHour)
    )

    res shouldBe slowingDown
  }

}

