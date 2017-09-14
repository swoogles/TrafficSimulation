package com.billding

import com.billding.physics.{Spatial, SpatialForDefaults}
import com.billding.traffic._
import org.scalatest.FlatSpec
import squants.motion._
import squants.space.{Kilometers, LengthUnit, Meters}
import squants.Length
import squants.time.{Milliseconds, Seconds}

/**
  * Full test should do accumulate:
  *   -Minimum distances between all vehicles
  *   -Max/Min speeds
  *   -Collision?
  */
class SceneSpec extends  FlatSpec{

  val idm: IntelligentDriverModel = new IntelligentDriverModelImpl
  val speedLimit = KilometersPerHour(150)
  val canvasDimensions: (Length, Length) = (Kilometers(1), Kilometers(1))

  val originSpatial = Spatial((0, 0, 0, Meters), (0.1, 0, 0, KilometersPerHour))
  val endingSpatial = Spatial((100, 0, 0, Kilometers), (0.1, 0, 0, KilometersPerHour))

  import PilotedVehicle.createVehicle
  type basicSpatial = ((Double, Double, Double, DistanceUnit), (Double, Double, Double, VelocityUnit))
  it should "do something " in {
    val vehicles = List(
      createVehicle((100, 0, 0, Meters), (0.1, 0, 0, KilometersPerHour), endingSpatial),
      createVehicle((80, 0, 0, Meters), (70, 0, 0, KilometersPerHour), endingSpatial),
      createVehicle((60, 0, 0, Meters), (140, 0, 0, KilometersPerHour), endingSpatial)
    )

    val vehicleSource = VehicleSourceImpl(Seconds(1), originSpatial, endingSpatial)
    val lane = new LaneImpl(vehicles, vehicleSource, originSpatial, endingSpatial)
    val street = StreetImpl(List(lane), originSpatial, endingSpatial, Seconds(1))
    val t = Seconds(500)
    implicit val dt = Milliseconds(500)
    val scene: Scene = SceneImpl(
      List(street),
      t,
      dt,
      speedLimit,
      canvasDimensions
    )

    val updateScene: Scene = scene.update(speedLimit)
    val updateScene2: Scene = updateScene.update(speedLimit)
    val updatedLane: Lane = Lane.update(lane, speedLimit, Seconds(1), Milliseconds(100))
  }
}