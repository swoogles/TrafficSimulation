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

  val laneStartingPoint = Spatial.apply((0, 0, 0, Meters))
  val laneEndingPoint = Spatial.apply((1, 0, 0, Kilometers))
  val vehicleSource = VehicleSourceImpl(1.seconds, laneStartingPoint)


  def createVehicle(
                     pIn1: (Double, Double, Double, LengthUnit),
                     vIn1: (Double, Double, Double, VelocityUnit)): PilotedVehicle = {
    PilotedVehicle.commuter(Spatial(pIn1, vIn1), idm)
  }

  def createVehiclePair(
                         pIn1: (Double, Double, Double, LengthUnit),
                         vIn1: (Double, Double, Double, VelocityUnit),
                         pIn2: (Double, Double, Double, LengthUnit),
                         vIn2: (Double, Double, Double, VelocityUnit)
                       ): (PilotedVehicle, PilotedVehicle) = {
    (createVehicle(pIn1, vIn1),
      createVehicle(pIn2, vIn2))
  }


  it should "make all vehicles respond appropriately" in {
    val (drivenVehicle1, drivenVehicle2) = createVehiclePair(
      (200, 0, 0, Meters), (40, 0, 0, KilometersPerHour),
      (180, 0, 0, Meters), (120, 0, 0, KilometersPerHour)
    )

    val (drivenVehicle3, drivenVehicle4) = createVehiclePair(
      (100, 0, 0, Meters), (70, 0, 0, KilometersPerHour),
      (80, 0, 0, Meters), (150, 0, 0, KilometersPerHour)
    )
    val vehicles = List(
      drivenVehicle1, drivenVehicle2, drivenVehicle3, drivenVehicle4
    )
    val lane = LaneImpl(vehicles, vehicleSource, laneStartingPoint, laneEndingPoint)
    val accelerations: List[Acceleration] = Lane.responsesInOneLanePrep(lane, speedLimit)
  }

  it should "make all vehicles accelerate from a stop together" in {
    val vehicles = List(
      createVehicle((100, 0, 0, Meters), (1, 0, 0, KilometersPerHour)),
      createVehicle((95, 0, 0, Meters), (0, 0, 0, KilometersPerHour)),
      createVehicle((90, 0, 0, Meters), (0, 0, 0, KilometersPerHour))
    )
    val lane = LaneImpl(vehicles, vehicleSource, laneStartingPoint, laneEndingPoint)
    val accelerations: List[Acceleration] = Lane.responsesInOneLanePrep(lane, speedLimit)
    every(accelerations) shouldBe speedingUp
  }

  it should "make all following vehicles slow down if the lead car is stopped" in {
    val vehicles = List(
      createVehicle((100, 0, 0, Meters), (0.1, 0, 0, KilometersPerHour)),
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
      createVehicle((82, 0, 0, Meters), (0, 0, 0, KilometersPerHour)),
      createVehicle((80, 0, 0, Meters), (0, 0, 0, KilometersPerHour)),
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
      createVehicle((100, 0, 0, Meters), (0.1, 0, 0, KilometersPerHour)),
      createVehicle((98, 0, 0, Meters), (0.0, 0, 0, KilometersPerHour)),
      createVehicle((96, 0, 0, Meters), (0.0, 0, 0, KilometersPerHour)),
      createVehicle((94, 0, 0, Meters), (0.0, 0, 0, KilometersPerHour)),
      createVehicle((92, 0, 0, Meters), (0.0, 0, 0, KilometersPerHour))
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
    val originSpatial = Spatial((0, 0, 0, Meters), (0.1, 0, 0, KilometersPerHour))
    val endingSpatial =Spatial((100, 0, 0, Kilometers), (0.1, 0, 0, KilometersPerHour))

    val vehicles = List(
      createVehicle((100, 0, 0, Meters), (0.1, 0, 0, KilometersPerHour)),
      createVehicle((80, 0, 0, Meters), (70, 0, 0, KilometersPerHour)),
      createVehicle((60, 0, 0, Meters), (140, 0, 0, KilometersPerHour))
    )

    val lane = new LaneImpl(vehicles, vehicleSource, originSpatial, endingSpatial)
    val accelerations: List[Acceleration] = Lane.responsesInOneLanePrep(lane, speedLimit)
    accelerations.head shouldBe speedingUp
    every(accelerations.tail) shouldBe slowingDown
  }

//  it should "add a new vehicle after an appropriate amount of time" in {
//    val vehicles = List(
//      createVehicle((60, 0, 0, Meters), (140, 0, 0, KilometersPerHour))
//    )
//    val lane = LaneImpl(vehicles, vehicleSource, laneStartingPoint, laneEndingPoint)
//    val updatedLane = Lane.update(lane, speedLimit, 0.seconds, 0.0001.seconds)
//    updatedLane.vehicles.size shouldBe  lane.vehicles.size + 1
//  }

  it should "only add 1 vehicle after an appropriate amount of time" in {
    val dt = 0.1.seconds
    val moments = Stream.continually(dt).take(10)
    val vehicles = List(
      createVehicle((60, 0, 0, Meters), (140, 0, 0, KilometersPerHour))
    )
    val lane: Lane = LaneImpl(vehicles, vehicleSource, laneStartingPoint, laneEndingPoint)
    val startT: Time = 0.seconds
    moments.foldLeft(lane, startT){ case ((curLane, t: Time), nextDt) => (Lane.update(lane, speedLimit, t, nextDt), t+dt)}

    val updatedLane = Lane.update(lane, speedLimit, 0.5.seconds, dt)
    updatedLane.vehicles.size shouldBe  lane.vehicles.size + 1
  }

}
