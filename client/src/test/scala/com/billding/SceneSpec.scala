package com.billding

import org.scalatest.FlatSpec
import squants.motion._
import squants.space.{Kilometers, LengthUnit, Meters}
import org.scalatest.Matchers._
import squants.time.{Milliseconds, Seconds}

/**
  * Created by bfrasure on 5/7/17.
  */
class SceneSpec extends  FlatSpec{
//  SceneImpl(
//                        lanes: List[Lane],
//                        t: Time,
//                        dt: Time,
//                        speedLimit: Velocity
//                      ) extends Scene

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
      val startingSpec: basicSpatial = ((0, 0, 0, Meters), (0.1, 0, 0, KilometersPerHour))
      val endingSpec: basicSpatial = ((100, 0, 0, Kilometers), (0.1, 0, 0, KilometersPerHour))

      val originSpatial = Spatial(startingSpec._1, startingSpec._2)
      val endingSpatial = Spatial(endingSpec._1, endingSpec._2)

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
    val updateScene: Scene = scene.update(speedLimit, t)
      val updatedLane: Lane = Lane.update(lane, speedLimit, Seconds(1), Milliseconds(100))
      val accelerations: List[Acceleration] = Lane.responsesInOneLanePrep(vehicles, speedLimit)
      accelerations.head shouldBe >(MetersPerSecondSquared(0))
      every(accelerations.tail) shouldBe <(MetersPerSecondSquared(0))
    }
}
