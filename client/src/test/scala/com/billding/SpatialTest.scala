package com.billding

import scala.language.postfixOps
import com.billding.physics.{Spatial, SpatialImpl}
import org.scalatest.FlatSpec
import org.scalatest.Matchers._
import squants.motion._
import squants.space.{Kilometers, Meters}
import squants.time.TimeConversions._

class SpatialTest extends FlatSpec {
  private val dt = 1 seconds

  val destination = Spatial.apply((1, 0, 0, Kilometers))

  it should "accelerate a spatial in the direction of travel." in {
    val startingSpatial = Spatial(
      (0, 0, 0, Kilometers),
      (120, 0, 0, KilometersPerHour)
    )
    val acceleration = MetersPerSecondSquared(1)
    val endingSpatial = Spatial.accelerateAlongCurrentDirection(startingSpatial, dt, acceleration, destination)
    endingSpatial.r.magnitude > startingSpatial.r.magnitude
  }

  it should "decelerate a spatial in the direction of travel." in {
    val startingSpacial = Spatial(
      (0, 0, 0, Kilometers),
      (120, 0, 0, KilometersPerHour)
    )
    val acceleration = -MetersPerSecondSquared(1)
    val endingSpatial = Spatial.accelerateAlongCurrentDirection(startingSpacial, dt, acceleration, destination)
    endingSpatial.r.magnitude < startingSpacial.r.magnitude
  }

  it should "calculate the distance between spatials" in {
    val spacial1 = Spatial(
      (0, 0, 0, Kilometers),
      (0, 0, 0, KilometersPerHour)
    )
    val spacial2 = Spatial(
      (3, 4, 0, Kilometers),
      (0, 0, 0, KilometersPerHour)
    )
    assert(spacial1.distanceTo(spacial2)== Kilometers(5))
  }

  it should "calculate relative velocity between spatials" in {
    val spacial1 = Spatial(
      (0, 0, 0, Kilometers),
      (0, 0, 0, KilometersPerHour)
    )
    val spacial2 = Spatial(
      (0, 0, 0, Kilometers),
      (3, 4, 0, KilometersPerHour)
    )
    assert(spacial1.relativeVelocityMag(spacial2)== KilometersPerHour(5))
  }

  val goalSpatial = Spatial.apply((1, 0, 0, Kilometers))

  // FINALLY got a test that contains this damn NaN issue
  it should "accelerate a Spatial from rest" in {
    val startingSpatial = Spatial((100, 0, 0, Meters), (0.0, 0, 0, KilometersPerHour))
    val updatedSpatial = Spatial.accelerateAlongCurrentDirection(startingSpatial, 1.seconds, MetersPerSecondSquared(1), goalSpatial)
    updatedSpatial.v.magnitude shouldBe > (startingSpatial.v.magnitude)
  }

  it should "sit still if accelerating backwards" in {
    val startingSpatial = Spatial((100, 0, 0, Meters), (0.0, 0, 0, KilometersPerHour))
    val updatedSpatial: SpatialImpl = Spatial.accelerateAlongCurrentDirection(startingSpatial, 1.seconds, -MetersPerSecondSquared(1), goalSpatial)
    updatedSpatial shouldBe startingSpatial
  }

  it should "deccelerate a moving Spatial" in {
    val startingSpatial = Spatial((100, 0, 0, Meters), (5.0, 0, 0, KilometersPerHour))
    val updatedSpatial = Spatial.accelerateAlongCurrentDirection(startingSpatial, 1.seconds, -MetersPerSecondSquared(1), goalSpatial)
    updatedSpatial.v.magnitude shouldBe < (startingSpatial.v.magnitude)
  }
}
