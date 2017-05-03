package com.billding

import org.scalatest._
import squants.motion._
import squants.space.{Kilometers, LengthUnit, Meters}

class PilotedVehicleSpec extends FlatSpec {

  def accelerationTest (
    pIn1: (Double, Double, Double, LengthUnit),
    vIn1: (Double, Double, Double, VelocityUnit),
    pIn2: (Double, Double, Double, LengthUnit),
    vIn2: (Double, Double, Double, VelocityUnit)
  ): Acceleration = {
    val drivenVehicle1 = PilotedVehicle.commuter(Spatial( pIn1, vIn1 ))
    val drivenVehicle2 = PilotedVehicle.commuter(Spatial( pIn2, vIn2 ))

    // TODO Get rid of magic value
    drivenVehicle1.reactTo(drivenVehicle2, KilometersPerHour(150))
  }

  it should "accelerate a slow car when obstacle is far away" in {
    val res = accelerationTest(
      (0, 0, 0, Kilometers),
      (120, 0, 0, KilometersPerHour),
      (2, 0, 0, Kilometers),
      (40, 0, 0, KilometersPerHour)
    )
    assert( res.toMetersPerSecondSquared > 0)
  }

  it should "accelerate a slow car when obstacle is close but moving faster" in {
    val res = accelerationTest(
      (0, 0, 0, Kilometers),
      (120, 0, 0, KilometersPerHour),
      (0.5, 0, 0, Kilometers),
      (100, 0, 0, KilometersPerHour)
    )

    assert( res.toMetersPerSecondSquared > 0)
  }

  it should "slow down a when obstacle is close, even if it's moving fast" in {
    // Oh shit. Being able to hop to a different scale here is GREAT.
    val res = accelerationTest(
      (0, 0, 0, Meters),
      (120, 0, 0, KilometersPerHour),
      (1, 0, 0, Meters),
      (140, 0, 0, KilometersPerHour)
    )

    assert( res.toMetersPerSecondSquared < 0)
  }

  it should "slow down a when obstacle is far away, if it's stopped/slow" in {
    val res = accelerationTest(
      (0, 0, 0, Kilometers),
      (150, 0, 0, KilometersPerHour),
      (1.0, 0, 0, Kilometers),
      (10, 0, 0, KilometersPerHour)
    )

    assert( res.toMetersPerSecondSquared < 0)
  }

}

