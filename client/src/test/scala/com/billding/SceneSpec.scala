package com.billding

import org.scalatest.FlatSpec
import squants.motion._
import squants.space.{Kilometers, LengthUnit, Meters}
import org.scalatest.Matchers._
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

  def createVehicle(
                     pIn1: (Double, Double, Double, LengthUnit),
                     vIn1: (Double, Double, Double, VelocityUnit)): PilotedVehicle = {
    PilotedVehicle.commuter(Spatial(pIn1, vIn1), idm)
  }
  type basicSpatial = ((Double, Double, Double, DistanceUnit), (Double, Double, Double, VelocityUnit))
  it should "do something " in {
    // TODO enact real tests here to ensure correct behavior
    // This might involve reusing code/test code from Spatial tests?
    val originSpatial = Spatial((0, 0, 0, Meters), (0.1, 0, 0, KilometersPerHour))
    val endingSpatial = Spatial((100, 0, 0, Kilometers), (0.1, 0, 0, KilometersPerHour))

    val vehicles = List(
      createVehicle((100, 0, 0, Meters), (0.1, 0, 0, KilometersPerHour)),
      createVehicle((80, 0, 0, Meters), (70, 0, 0, KilometersPerHour)),
      createVehicle((60, 0, 0, Meters), (140, 0, 0, KilometersPerHour))
    )

    val source = VehicleSourceImpl(Seconds(1), originSpatial)
    val lane = new LaneImpl(vehicles, source, originSpatial, endingSpatial)
    val t = Seconds(500)
    implicit val dt = Milliseconds(500)
    val scene: Scene = SceneImpl(
      List(lane),
      t,
      dt,
      speedLimit
    )
    // TODO figure out weird drift happening here
    val updateScene: Scene = scene.update(speedLimit)
    val updateScene2: Scene = updateScene.update(speedLimit)
    import SpatialForDefaults.spatialForPilotedVehicle
//    import com.billding.SpatialForDefaults
    pprint.pprintln("original vehicle 0: " + SpatialForDefaults.disect(scene.lanes.head.vehicles.tail.head).v)
    pprint.pprintln("updated vehicle  1: " + SpatialForDefaults.disect(updateScene.lanes.head.vehicles.tail.head).v)
    pprint.pprintln("updated vehicle  2: " + SpatialForDefaults.disect(updateScene2.lanes.head.vehicles.tail.head).v)
    val updatedLane: Lane = Lane.update(lane, speedLimit, Seconds(1), Milliseconds(100))
    val accelerations: List[Acceleration] = Lane.responsesInOneLanePrep(vehicles, speedLimit)
    accelerations.head shouldBe >(MetersPerSecondSquared(0))
    every(accelerations.tail) shouldBe <(MetersPerSecondSquared(0))
  }
}