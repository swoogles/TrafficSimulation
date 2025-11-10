package com.billding

import com.billding.Orientation.{East, North, South, West}
import com.billding.physics._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import squants.{DoubleVector, QuantityVector, Velocity}
import squants.motion._
import squants.space.{Kilometers, Meters}
import squants.time.TimeConversions._

import scala.language.postfixOps

class SpatialTest extends AnyFlatSpec with Matchers {
  private val dt = 1 seconds

  val destination: Spatial = Spatial.apply((1, 0, 0, Kilometers))

  it should "accelerate a spatial in the direction of travel." in {
    val acceleration = MetersPerSecondSquared(1)
    val endingSpatial = Spatial.accelerateAlongCurrentDirection(movableSpatial, dt, acceleration, destination)
    endingSpatial.r.magnitude > movableSpatial.r.magnitude
  }

  it should "decelerate a spatial in the direction of travel." in {
    val acceleration = -MetersPerSecondSquared(1)
    val endingSpatial = Spatial.accelerateAlongCurrentDirection(movableSpatial, dt, acceleration, destination)
    endingSpatial.r.magnitude < movableSpatial.r.magnitude
  }

  it should "calculate the distance between spatials" in {
    val spacial1 = Spatial(
      (0, 0, 0, distanceUnit),
      (0, 0, 0, KilometersPerHour)
    )
    val spacial2 = Spatial(
      (3, 4, 0, distanceUnit),
      (0, 0, 0, KilometersPerHour)
    )
    assert(spacial1.distanceTo(spacial2)== distanceUnit(5))
  }

  it should "calculate relative velocity between spatials" in {
    val spacial1 = Spatial(
      (0, 0, 0, distanceUnit),
      (0, 0, 0, KilometersPerHour)
    )
    val spacial2 = Spatial(
      (0, 0, 0, distanceUnit),
      (3, 4, 0, KilometersPerHour)
    )
    assert(spacial1.relativeVelocityMag(spacial2)== KilometersPerHour(5))
  }

  val goalSpatial = Spatial.apply((1, 0, 0, Kilometers))

  it should "accelerate along direction of travel" in {
    val spatial = Spatial(0.0, 0.0, 0.0, Meters)
    val destination = Spatial(100.0, 0.0, 0.0, Meters)
    val acceleration = MetersPerSecondSquared(1)
    val expectedResult = QuantityVector(MetersPerSecondSquared(1.0), MetersPerSecondSquared(0.0), MetersPerSecondSquared(0.0))
    val result = Spatial.accelerationAlongDirectionOfTravelWithoutPreventingBackwardsTravel(spatial, acceleration, destination)
    println("acceleration result: " + result)
    assert(result == expectedResult)

  }

  // FINALLY got a test that contains this damn NaN issue
  it should "accelerate a Spatial from rest" in {
    val startingSpatial = Spatial((100, 0, 0, Meters))
    val updatedSpatial = Spatial.accelerateAlongCurrentDirection(startingSpatial, 1.seconds, MetersPerSecondSquared(1), goalSpatial)
    updatedSpatial.v.magnitude shouldBe > (startingSpatial.v.magnitude)
  }

  it should "sit still if accelerating backwards" in {
    val startingSpatial = Spatial((100, 0, 0, Meters))
    val updatedSpatial: Spatial = Spatial.accelerateAlongCurrentDirection(startingSpatial, 1.seconds, -MetersPerSecondSquared(1), goalSpatial)
    updatedSpatial shouldBe startingSpatial
  }

  it should "determine if 2 vectors are pointed in opposite directions" in {
    val vec1: QuantityVector[Velocity] =
      Spatial.convertToSVector(1.0, 0.0, 0.0, MetersPerSecond)

    val vec2 = DoubleVector(-1, 0, 0)
    assert(Spatial.vectorsAreInOppositeDirections(vec1, vec2) == true)
    println(Spatial.vectorsAreInOppositeDirections(
      Spatial.convertToSVector(1.0, 0.0, 0.0, MetersPerSecond)
      , DoubleVector(-1, 0, 0)))
    assert(Spatial.vectorsAreInOppositeDirections(
      Spatial.convertToSVector(5.0, 0.0, 0.0, MetersPerSecond)
      , DoubleVector(-5, 0, 0)) == true)
    assert(Spatial.vectorsAreInOppositeDirections(
      Spatial.convertToSVector(10.0, 0.0, 0.0, MetersPerSecond)
      , DoubleVector(-1, 0, 0)) == true)

  }

  it should "deccelerate a moving Spatial" in {
    val startingSpatial = Spatial((100, 0, 0, Meters), (5.0, 0, 0, KilometersPerHour))
    val updatedSpatial = Spatial.accelerateAlongCurrentDirection(startingSpatial, 1.seconds, -MetersPerSecondSquared(1), goalSpatial)
    updatedSpatial.v.magnitude shouldBe < (startingSpatial.v.magnitude)
  }

  val offset = 100

  val distanceUnit = Meters
  val movableSpatial = Spatial((0, 0, 0, distanceUnit))
  it should "make a new spatial North of the original" in {
    val newSpatial = movableSpatial.move(North, distanceUnit(offset))
    newSpatial shouldBe Spatial((0, -offset, 0, distanceUnit))
  }

  it should "make a new spatial South of the original" in {
    val newSpatial = movableSpatial.move(South, distanceUnit(offset))
    newSpatial shouldBe Spatial((0, offset, 0, distanceUnit))
  }

  it should "make a new spatial East of the original" in {
    val newSpatial = movableSpatial.move(East, distanceUnit(offset))
    newSpatial shouldBe Spatial((offset, 0, 0, distanceUnit))
  }

  it should "make a new spatial West of the original" in {
    val newSpatial = movableSpatial.move(West, distanceUnit(offset))
    newSpatial shouldBe Spatial((-offset, 0, 0, distanceUnit))
  }
}
