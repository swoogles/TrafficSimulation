package com.billding

import fr.iscpif.client.physics.Spatial
import fr.iscpif.client.traffic._
import org.scalatest.FlatSpec
import squants.Length
import squants.motion._
import squants.space.{Kilometers, Meters}
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

  type basicSpatial = ((Double, Double, Double, DistanceUnit), (Double, Double, Double, VelocityUnit))
  it should "do something " in {
    val vehicles = List(
      PilotedVehicle((100, 0, 0, Meters), (0.1, 0, 0, KilometersPerHour), endingSpatial),
      PilotedVehicle((80, 0, 0, Meters), (70, 0, 0, KilometersPerHour), endingSpatial),
      PilotedVehicle((60, 0, 0, Meters), (140, 0, 0, KilometersPerHour), endingSpatial)
    )

    val vehicleSource = VehicleSourceImpl(Seconds(1), originSpatial, endingSpatial)
    val lane = Lane(vehicles, vehicleSource, originSpatial, endingSpatial, speedLimit)
    val street = Street(List(lane), originSpatial, endingSpatial)
    val t = Seconds(500)
    implicit val dt = Milliseconds(500)
    val scene: Scene = SceneImpl(
      List(street),
      t,
      dt,
      speedLimit,
      canvasDimensions
    )

    val updatedLane: Lane = Lane.update(lane, Seconds(1), Milliseconds(100))
  }
}
