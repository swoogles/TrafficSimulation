package com.billding

import org.scalatest.FlatSpec
import squants.motion._
import squants.space.{Kilometers, LengthUnit, Meters}
import org.scalatest.Matchers._
import SquantsMatchers._
import com.billding.physics.Spatial
import com.billding.traffic._
import squants.time.{Milliseconds, Seconds, Time}
import squants.time.TimeConversions._
import squants.space.LengthConversions._

class LaneSpec extends  FlatSpec {
  val idm: IntelligentDriverModel = new IntelligentDriverModelImpl
  val speedLimit = KilometersPerHour(150)

  val zeroDimensions: (Double, Double, Double, LengthUnit) = (0, 2, 0, Meters)
  val laneStartingPoint = Spatial.BLANK
  val laneEndingPoint = Spatial.apply((1, 0, 0, Kilometers))
  val herdSpeed = 65
  val velocitySpatial = Spatial((0, 0, 0, Meters), (herdSpeed, 0, 0, KilometersPerHour), zeroDimensions)
  val vehicleSource = VehicleSourceImpl(1.seconds, laneStartingPoint, velocitySpatial)

  val emptyLane = LaneImpl(Nil, vehicleSource, laneStartingPoint, laneEndingPoint)

  def createVehicle(
                     pIn1: (Double, Double, Double, LengthUnit),
                     vIn1: (Double, Double, Double, VelocityUnit) = (0, 0, 0, KilometersPerHour),
                     destination: Spatial = emptyLane.vehicleAtInfinity.spatial
                   ): PilotedVehicle = {
    PilotedVehicle.commuter(Spatial(pIn1, vIn1), idm, destination)
  }

  it should "make all vehicles accelerate from a stop together" in {
    val vehicles = List(
      createVehicle((100, 0, 0, Meters), (1, 0, 0, KilometersPerHour)),
      createVehicle((95, 0, 0, Meters)),
      createVehicle((90, 0, 0, Meters))
    )
    val lane = LaneImpl(vehicles, vehicleSource, laneStartingPoint, laneEndingPoint)
    val accelerations: List[Acceleration] = Lane.responsesInOneLanePrep(lane, speedLimit)
    every(accelerations) shouldBe speedingUp
  }

  it should "make all following vehicles slow down if the lead car is stopped" in {
    val vehicles = List(
      createVehicle((100, 0, 0, Meters)),
      createVehicle((80, 0, 0, Meters), (70, 0, 0, KilometersPerHour)),
      createVehicle((60, 0, 0, Meters), (140, 0, 0, KilometersPerHour))
    )
    val lane = LaneImpl(vehicles, vehicleSource, laneStartingPoint, laneEndingPoint)
    val accelerations: List[Acceleration] = Lane.responsesInOneLanePrep(lane, speedLimit)
    accelerations.head shouldBe speedingUp
    every(accelerations.tail) shouldBe slowingDown
  }

  implicit val speedTolerance = MetersPerSecondSquared(0.01)

  it should "have 1 car decelerate as it approaches a stopped car, and another accelerate away in front of it" in {
    val vehicles = List(
      createVehicle((82, 0, 0, Meters)),
      createVehicle((80, 0, 0, Meters)),
      createVehicle((60, 0, 0, Meters), (140, 0, 0, KilometersPerHour))
    )
    val lane = LaneImpl(vehicles, vehicleSource, laneStartingPoint, laneEndingPoint)
    val accelerations: List[Acceleration] = Lane.responsesInOneLanePrep(lane, speedLimit)
    val (acc2 :: acc3 :: acc4 :: Nil) = accelerations
    acc2 shouldBe speedingUp
    acc3 shouldBe maintainingVelocity
    acc4 shouldBe slowingDown
  }

  it should "should only accelerate lead car in bumper-to-bumper traffic" in {
    val vehicles = List(
      createVehicle((100, 0, 0, Meters)),
      createVehicle((98, 0, 0, Meters)),
      createVehicle((96, 0, 0, Meters)),
      createVehicle((94, 0, 0, Meters)),
      createVehicle((92, 0, 0, Meters))
    )
    val lane = LaneImpl(vehicles, vehicleSource, laneStartingPoint, laneEndingPoint)
    val accelerations: List[Acceleration] = Lane.responsesInOneLanePrep(lane, speedLimit)
    accelerations.head shouldBe speedingUp
    for (acceleration <- accelerations.tail) {
      assert(acceleration =~ MetersPerSecondSquared(0))
    }
  }

  type basicSpatial = ((Double, Double, Double, DistanceUnit), (Double, Double, Double, VelocityUnit))
  // TODO enact real tests here to ensure correct behavior
  // This might involve reusing code/test code from Spatial tests?
  /*
  These tests need to check p and v.
  At least, at first inspection.
  Is it possible to do all lane updating while ignoring that?
   */
  it should "correctly update all cars in a lane" in {
    val originSpatial = Spatial((0, 0, 0, Meters))
    val endingSpatial =Spatial((100, 0, 0, Kilometers))

    val vehicles = List(
      createVehicle((100, 0, 0, Meters)),
      createVehicle((80, 0, 0, Meters), (70, 0, 0, KilometersPerHour)),
      createVehicle((60, 0, 0, Meters), (140, 0, 0, KilometersPerHour))
    )

    val lane = new LaneImpl(vehicles, vehicleSource, originSpatial, endingSpatial)
    val accelerations: List[Acceleration] = Lane.responsesInOneLanePrep(lane, speedLimit)
    accelerations.head shouldBe speedingUp
    every(accelerations.tail) shouldBe slowingDown
  }

  it should "make sure all cars have the right starting velocity" in {
    val originSpatial = Spatial((0, 0, 0, Meters))
    val endingSpatial = Spatial((100, 0, 0, Kilometers))

    val speed = KilometersPerHour(50)
    val lane = Lane(Seconds(1), originSpatial, endingSpatial, speed)
    val t = Seconds(1)
    val dt = Seconds(.1)
    val updatedLane = Lane.update(lane, speedLimit, t, dt)
    pprint.pprintln(updatedLane)

  }
}
