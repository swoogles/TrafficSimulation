package com.billding

import org.scalatest.FlatSpec
import squants.motion._
import squants.space.{Kilometers, LengthUnit, Meters}
import org.scalatest.Matchers._
import SquantsMatchers._
import com.billding.physics.Spatial
import com.billding.traffic.Lane._
import com.billding.traffic._
import org.scalactic.TolerantNumerics
import squants.time.Seconds
import squants.time.TimeConversions._


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

  import PilotedVehicle.createVehicle
  it should "add a disruptive car" in {
    val originSpatial = Spatial((0, 0, 0, Meters))
    val endingSpatial = Spatial((100, 0, 0, Meters))

    val speed = KilometersPerHour(50)
    val vehicleSource = VehicleSourceImpl(1.seconds, originSpatial, endingSpatial)

    val vehicles = List(
      createVehicle((100, 0, 0, Meters)),
      createVehicle((60, 0, 0, Meters)),
      createVehicle((50, 0, 0, Meters))
    )
    val lane = LaneImpl(vehicles, vehicleSource, originSpatial, endingSpatial)
    val originalSize = lane.vehicles.size

    val disruptedLane  = lane.addDisruptiveVehicle(createVehicle((0, 0, 0, Meters)))
    val newSize = disruptedLane.vehicles.size
    val t = Seconds(1)
  }

  it should "make all vehicles accelerate from a stop together" in {
    val vehicles = List(
      createVehicle((100, 0, 0, Meters), (1, 0, 0, KilometersPerHour)),
      createVehicle((95, 0, 0, Meters)),
      createVehicle((90, 0, 0, Meters))
    )
    val lane = LaneImpl(vehicles, vehicleSource, laneStartingPoint, laneEndingPoint)
    val accelerations: List[Acceleration] = responsesInOneLanePrep(lane, speedLimit)
    every(accelerations) shouldBe speedingUp
  }

  it should "make all following vehicles slow down if the lead car is stopped" in {
    val vehicles = List(
      createVehicle((100, 0, 0, Meters)),
      createVehicle((80, 0, 0, Meters), (70, 0, 0, KilometersPerHour)),
      createVehicle((60, 0, 0, Meters), (140, 0, 0, KilometersPerHour))
    )
    val lane = LaneImpl(vehicles, vehicleSource, laneStartingPoint, laneEndingPoint)
    val accelerations: List[Acceleration] = responsesInOneLanePrep(lane, speedLimit)
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
    val accelerations: List[Acceleration] = responsesInOneLanePrep(lane, speedLimit)
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
    val accelerations: List[Acceleration] = responsesInOneLanePrep(lane, speedLimit)
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
    val accelerations: List[Acceleration] = responsesInOneLanePrep(lane, speedLimit)
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
    val updatedLane = update(lane, speedLimit, t, dt)

  }

  it should "find vehicles before and after target vehicle" in {
    val originSpatial = Spatial((0, 0, 0, Meters))
    val endingSpatial =Spatial((100, 0, 0, Kilometers))

    val leadCar = createVehicle((100, 0, 0, Meters))
    val target = createVehicle((80, 0, 0, Meters), (70, 0, 0, KilometersPerHour))
    val followingCar = createVehicle((60, 0, 0, Meters), (140, 0, 0, KilometersPerHour))
    val vehicles = List(
      leadCar,
      target,
      followingCar
    )

    val lane = new LaneImpl(vehicles, vehicleSource, originSpatial, endingSpatial)
    val foundVehicle = getVehicleBeforeAndAfter(target, lane)
//    pprint.pprintln(foundVehicle)
  }

  val epsilon = 1e-4f

  implicit val doubleEq = TolerantNumerics.tolerantDoubleEquality(epsilon)

  it should "calculate the fraction of a lane that a car has driven" in {
    val originSpatial = Spatial((0, 0, 0, Meters))
    val endingSpatial =Spatial((100, 0, 0, Meters))

    val leadCar = createVehicle((90, 0, 0, Meters))
    val target = createVehicle((80, 0, 0, Meters), (70, 0, 0, KilometersPerHour))
    val followingCar = createVehicle((60, 0, 0, Meters), (140, 0, 0, KilometersPerHour))
    val vehicles = List(
      leadCar,
      target,
      followingCar
    )

    val lane = new LaneImpl(vehicles, vehicleSource, originSpatial, endingSpatial)
    val leadFractionCompleted = Lane.fractionCompleted(leadCar, lane)
    val targetFractionCompleted = Lane.fractionCompleted(target, lane)
    val followingFractionCompleted = Lane.fractionCompleted(followingCar, lane)
    assert(leadFractionCompleted === 0.9f)
    assert(targetFractionCompleted === 0.8f)
    assert(followingFractionCompleted === 0.6f)
  }

  it should "report that a vehicle can be placed in a lane without hitting existing vehicles" in {
    val originSpatial = Spatial((0, 0, 0, Meters))
    val endingSpatial = Spatial((100, 0, 0, Meters))

    val targetOriginSpatial = Spatial((0, 10, 0, Meters))
    val targetEndingSpatial = Spatial((100, 10, 0, Meters))

    val leadCar = createVehicle((90, 0, 0, Meters))
    val target = createVehicle((80, 10, 0, Meters), (70, 0, 0, KilometersPerHour))
    val followingCar = createVehicle((60, 0, 0, Meters), (140, 0, 0, KilometersPerHour))

    val targetVehicles = List(
      leadCar,
      followingCar
    )

    val currentLaneSource = VehicleSourceImpl(1.seconds, originSpatial, endingSpatial)
    val currentLane = new LaneImpl(List(target), currentLaneSource, originSpatial, endingSpatial)
    val fractionCompleted = Lane.fractionCompleted(target, currentLane)
    val vehicleSource = VehicleSourceImpl(1.seconds, targetOriginSpatial, targetEndingSpatial)
    val targetLane = new LaneImpl(targetVehicles, vehicleSource, originSpatial, endingSpatial)

    targetLane.vehicleCanBePlaced(target, fractionCompleted) shouldBe true
  }

  it should "report that a vehicle cannot be placed in a lane without hitting existing vehicles" in {
    val originSpatial = Spatial((0, 0, 0, Meters))
    val endingSpatial = Spatial((100, 0, 0, Meters))

    val targetOriginSpatial = Spatial((0, 10, 0, Meters))
    val targetEndingSpatial = Spatial((100, 10, 0, Meters))

    val leadCar = createVehicle((90, 0, 0, Meters))
    val target = createVehicle((60, 10, 0, Meters), (70, 0, 0, KilometersPerHour))
    val followingCar = createVehicle((60, 0, 0, Meters), (140, 0, 0, KilometersPerHour))

    val targetVehicles = List(
      leadCar,
      followingCar
    )

    val currentLaneSource = VehicleSourceImpl(1.seconds, originSpatial, endingSpatial)
    val currentLane = new LaneImpl(List(target), currentLaneSource, targetOriginSpatial, targetEndingSpatial)
    val fractionCompleted = Lane.fractionCompleted(target, currentLane)

    println("fractionCompleted: " + fractionCompleted)

    val vehicleSource = VehicleSourceImpl(1.seconds, targetOriginSpatial, targetEndingSpatial)
    val targetLane = new LaneImpl(targetVehicles, vehicleSource, originSpatial, endingSpatial)

    targetLane.vehicleCanBePlaced(target, fractionCompleted) shouldBe false
  }
}
