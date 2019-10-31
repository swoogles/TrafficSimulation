package com.billding

import com.billding.SquantsMatchers._
import com.billding.physics.Spatial
import com.billding.traffic.{IntelligentDriverModelImpl, PilotedVehicle}
import org.scalatest.Matchers._
import org.scalatest._
import squants.motion._
import squants.space.{Kilometers, LengthUnit, Meters}

/*
  I think these tests are specific to the IDM, rather than Piloted Vehicle.
 */
class PilotedVehicleSpec extends FlatSpec {
  val speedLimit = KilometersPerHour(150)
  val idm: IntelligentDriverModelImpl = new IntelligentDriverModelImpl

  val destination = Spatial.apply((100.0, 0.0, 0.0, Meters))

  def createVehiclePair (
                         pIn1: (Double, Double, Double, LengthUnit),
                         vIn1: (Double, Double, Double, VelocityUnit),
                         pIn2: (Double, Double, Double, LengthUnit),
                         vIn2: (Double, Double, Double, VelocityUnit)
                       ): (PilotedVehicle, PilotedVehicle) = {
    (PilotedVehicle.commuter2(Spatial(pIn1, vIn1), idm, destination),
      PilotedVehicle.commuter2(Spatial(pIn2, vIn2), idm, destination))
  }

  def accelerationTest (
    pIn1: (Double, Double, Double, LengthUnit),
    vIn1: (Double, Double, Double, VelocityUnit),
    pIn2: (Double, Double, Double, LengthUnit),
    vIn2: (Double, Double, Double, VelocityUnit)
  ): Acceleration = {
    val (drivenVehicle1, drivenVehicle2)  = createVehiclePair( pIn1, vIn1 , pIn2, vIn2 )
    drivenVehicle1.reactTo(drivenVehicle2, speedLimit)
  }

  it should "accelerate a slow car when obstacle is far away" in {
    val res = accelerationTest(
      (0, 0, 0, Kilometers),
      (120, 0, 0, KilometersPerHour),
      (2, 0, 0, Kilometers),
      (40, 0, 0, KilometersPerHour)
    )
    res shouldBe SpeedingUp
  }

  it should "hold steady when pacing the target car" in {
    val res: Acceleration = accelerationTest(
      (0, 0, 0, Meters),
      (120, 0, 0, KilometersPerHour),
      (45, 0, 0, Meters),
      (120, 0, 0, KilometersPerHour)
    )
    implicit val tolerance: Acceleration = MetersPerSecondSquared(0.1)

    res shouldBe MaintainingVelocity
  }

  it should "accelerate when obstacle is close but moving faster" in {
    val res = accelerationTest(
      (0, 0, 0, Kilometers),
      (120, 0, 0, KilometersPerHour),
      (50, 0, 0, Meters),
      (140, 0, 0, KilometersPerHour)
    )

    res shouldBe SpeedingUp
  }

  it should "decelerate when obstacle is close but moving slower" in {
    val res = accelerationTest(
      (0, 0, 0, Kilometers),
      (120, 0, 0, KilometersPerHour),
      (40, 0, 0, Meters),
      (60, 0, 0, KilometersPerHour)
    )
    assert( res.toMetersPerSecondSquared < 0)
  }

  it should "slow down a when obstacle is close, even if it's moving fast" in {
    // Oh shit. Being able to hop to a different scale here is GREAT.
    val res = accelerationTest(
      (0, 0, 0, Meters),
      (120, 0, 0, KilometersPerHour),
      (1, 0, 0, Meters),
      (140, 0, 0, KilometersPerHour)
    )

    res shouldBe SlowingDown
  }

  it should "slow down a when obstacle is far away, if it's stopped/slow" in {
    val res = accelerationTest(
      (0, 0, 0, Kilometers),
      (150, 0, 0, KilometersPerHour),
      (1.0, 0, 0, Kilometers),
      (10, 0, 0, KilometersPerHour)
    )

    res shouldBe SlowingDown
  }

}

