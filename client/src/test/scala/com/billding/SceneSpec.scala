package com.billding

import com.billding.physics.{Spatial, SpatialForDefaults}
import com.billding.traffic._
import org.scalatest.FlatSpec
import squants.motion._
import squants.space.{Kilometers, LengthUnit, Meters}
import org.scalatest.Matchers._
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

  def createVehicle(
                     pIn1: (Double, Double, Double, LengthUnit),
                     vIn1: (Double, Double, Double, VelocityUnit)): PilotedVehicle = {
    PilotedVehicle.commuter(Spatial(pIn1, vIn1), idm, endingSpatial)
  }
  type basicSpatial = ((Double, Double, Double, DistanceUnit), (Double, Double, Double, VelocityUnit))
  it should "do something " in {
    // TODO enact real tests here to ensure correct behavior
    // This might involve reusing code/test code from Spatial tests?

    val vehicles = List(
      createVehicle((100, 0, 0, Meters), (0.1, 0, 0, KilometersPerHour)),
      createVehicle((80, 0, 0, Meters), (70, 0, 0, KilometersPerHour)),
      createVehicle((60, 0, 0, Meters), (140, 0, 0, KilometersPerHour))
    )

    val zeroDimensions: (Double, Double, Double, LengthUnit) = (0, 2, 0, Meters)
    val herdSpeed = 65
    val velocitySpatial = Spatial((0, 0, 0, Meters), (herdSpeed, 0, 0, KilometersPerHour), zeroDimensions)
    val vehicleSource = VehicleSourceImpl(Seconds(1), originSpatial, endingSpatial)
    val lane = new LaneImpl(vehicles, vehicleSource, originSpatial, endingSpatial)
    val t = Seconds(500)
    implicit val dt = Milliseconds(500)
    val scene: Scene = SceneImpl(
      List(lane),
      t,
      dt,
      speedLimit,
      canvasDimensions
    )
    // TODO figure out weird drift happening here
    val updateScene: Scene = scene.update(speedLimit)
    val updateScene2: Scene = updateScene.update(speedLimit)
    import com.billding.physics.SpatialForDefaults.spatialForPilotedVehicle
//    import com.billding.SpatialForDefaults
    pprint.pprintln("original vehicle 0: " + SpatialForDefaults.disect(scene.lanes.head.vehicles.tail.head).v)
    pprint.pprintln("updated vehicle  1: " + SpatialForDefaults.disect(updateScene.lanes.head.vehicles.tail.head).v)
    pprint.pprintln("updated vehicle  2: " + SpatialForDefaults.disect(updateScene2.lanes.head.vehicles.tail.head).v)
    val updatedLane: Lane = Lane.update(lane, speedLimit, Seconds(1), Milliseconds(100))

  }
}
