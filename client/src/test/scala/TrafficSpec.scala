package com.billding

import collection.mutable.Stack
import org.scalatest._
import cats.data.{NonEmptyList, Validated}
import com.billding.behavior.{IntelligentDriverImpl, IntelligentDriverModel}
import squants.{Mass, Time, Velocity, motion}
import squants.motion._
import squants.space.{Kilometers, Meters}

class TrafficSpec extends FlatSpec {
  val idm = new IntelligentDriverImpl

  it should "accelerate a slow car when obstacle is far away" in {
    val drivenVehicle1 = PilotedVehicle.commuter(Spatial(
      (0, 0, 0, Kilometers),
      (120, 0, 0, KilometersPerHour)
    ))

    val drivenVehicle2 = PilotedVehicle.commuter(Spatial(
      (2, 0, 0, Kilometers),
      (40, 0, 0, KilometersPerHour)
    ))

    val res: Acceleration = idm.reactTo(drivenVehicle1, drivenVehicle2, KilometersPerHour(150))
    assert( res.toMetersPerSecondSquared > 0)
  }

  it should "accelerate a slow car when obstacle is close but moving faster" in {
    val drivenVehicle1 = PilotedVehicle.commuter(Spatial(
      (0, 0, 0, Kilometers),
      (120, 0, 0, KilometersPerHour)
    ))

    val drivenVehicle2 = PilotedVehicle.commuter(Spatial(
      (0.5, 0, 0, Kilometers),
      (100, 0, 0, KilometersPerHour)
    ))

    val res: Acceleration = idm.reactTo(drivenVehicle1, drivenVehicle2, KilometersPerHour(150))
    assert( res.toMetersPerSecondSquared > 0)
  }

  it should "slow down a when obstacle is close, even if it's moving fast" in {
    val drivenVehicle1 = PilotedVehicle.commuter(Spatial(
      (0, 0, 0, Kilometers),
      (120, 0, 0, KilometersPerHour)
    ))

    val drivenVehicle2 = PilotedVehicle.commuter(Spatial(
      (0.1, 0, 0, Kilometers),
      (140, 0, 0, KilometersPerHour)
    ))

    val res: Acceleration = idm.reactTo(drivenVehicle1, drivenVehicle2, KilometersPerHour(150))
    assert( res.toMetersPerSecondSquared < 0)
  }

  it should "slow down a when obstacle is far away, if it's stopped/slow" in {
    val drivenVehicle1 = PilotedVehicle.commuter(Spatial(
      (0, 0, 0, Kilometers),
      (150, 0, 0, KilometersPerHour)
    ))

    val drivenVehicle2 = PilotedVehicle.commuter(Spatial(
      (1.0, 0, 0, Kilometers),
      (10, 0, 0, KilometersPerHour)
    ))

    val res: Acceleration = idm.reactTo(drivenVehicle1, drivenVehicle2, KilometersPerHour(150))
    assert( res.toMetersPerSecondSquared < 0)
  }

}

