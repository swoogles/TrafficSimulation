package com.billding

import com.billding.physics.Spatial
import com.billding.traffic._
import org.scalatest.flatspec.AnyFlatSpec
import squants.Length
import squants.motion._
import squants.space.{Kilometers, LengthUnit, Meters}
import squants.time.{Milliseconds, Seconds}

/**
  * Full test should do accumulate:
  *   -Minimum distances between all vehicles
  *   -Max/Min speeds
  *   -Collision?
  */
class SceneSpec extends AnyFlatSpec {

  val idm: IntelligentDriverModel = new IntelligentDriverModelImpl
  val speedLimit = KilometersPerHour(150)
  val canvasDimensions: (Length, Length) = (Kilometers(1), Kilometers(1))

  val originSpatial = Spatial((0, 0, 0, Meters), (0.1, 0, 0, KilometersPerHour))
  val endingSpatial = Spatial((100, 0, 0, Kilometers), (0.1, 0, 0, KilometersPerHour))
  val lengthUnit: LengthUnit = Meters
  val velocityUnit: VelocityUnit = KilometersPerHour

  it should "do something " in {
    val vehicles = List(
      PilotedVehicle((100.0, 0.0, 0.0, lengthUnit), endingSpatial, (0.1, 0.0, 0.0, velocityUnit)),
      PilotedVehicle((80.0, 0.0, 0.0, lengthUnit), endingSpatial, (70.0, 0.0, 0.0, velocityUnit)),
      PilotedVehicle((60.0, 0.0, 0.0, lengthUnit), endingSpatial, (140.0, 0.0, 0.0, velocityUnit))
    )

    val vehicleSource = VehicleSourceImpl(Seconds(1), originSpatial, endingSpatial)
    val lane = Lane(vehicles, vehicleSource, originSpatial, endingSpatial, speedLimit)
    val street = Street(List(lane), originSpatial, endingSpatial)
    val t = Seconds(500)
    implicit val dt = Milliseconds(500)
    val scene: Scene = Scene(
      List(street),
      t,
      dt,
      speedLimit,
      canvasDimensions
    )

    val updatedLane: Lane = Lane.update(lane, Seconds(1), Milliseconds(100))
  }

  it should "simulate one car encountering a stopped car" in {
//    SampleSceneCreation

  }
}
