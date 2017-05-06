package com.billding

import squants.mass.Kilograms
import squants.motion.{Acceleration, Distance, KilogramForce}
import squants.{Mass, Time, Velocity}
import squants.space.LengthConversions._
import squants.time.TimeConversions._

sealed trait Vehicle {
  val spatial: Spatial
  val weight: Mass
  val accelerationAbility: Acceleration
  val brakingAbility: Acceleration
}

case class Car(
                spatial: Spatial,
                weight: Mass = Kilograms(800),
                accelerationAbility: Acceleration = (0.3.meters.per((1 seconds).squared)),
                brakingAbility: Acceleration = (3.0.meters.per((1 seconds).squared))
              ) extends Vehicle

case class Truck(
                  spatial: Spatial,
                  weight: Mass = Kilograms(3000),
                  accelerationAbility: Acceleration = (0.3.meters.per((1 seconds).squared)),
                  brakingAbility: Acceleration = (2.0.meters.per((1 seconds).squared))
                ) extends Vehicle


trait PilotedVehicle {
  val spatial: Spatial // Can this be hidden? I'd really like that.
  def reactTo(obstacle: Spatial, speedLimit: Velocity): Acceleration
  def reactTo(obstacle: PilotedVehicle, speedLimit: Velocity): Acceleration
}

class PilotedVehicleImpl(driver: Driver, vehicle: Vehicle) extends PilotedVehicle {
  // TODO make a parameter
  val idm = driver.idm
  val desiredSpeed: Velocity = driver.desiredSpeed
  val reactionTime: Time = driver.reactionTime
  val weight = vehicle.weight
  override val spatial: Spatial = vehicle.spatial
  val maneuverTakenAt: Time = 1 seconds
  val accelerationAbility = vehicle.accelerationAbility
  val brakingAbility = vehicle.brakingAbility
  val preferredDynamicSpacing = driver.preferredDynamicSpacing
  val minimumDistance = driver.minimumDistance
  // TODO Get rid of this meddling junk of p, v, and dimensions!!
  // Use a SpatialFor[PilotedVehicle] instead.
  val p = spatial.p
  val v = spatial.v
  val dimensions = spatial.dimensions

  def reactTo(obstacle: Spatial, speedLimit: Velocity): Acceleration = {
    idm.deltaVDimensionallySafe(
      spatial.v.magnitude, // TODO Make a Spatial function
      speedLimit,
      spatial.relativeVelocityMag(obstacle),
      preferredDynamicSpacing,
      accelerationAbility,
      brakingAbility,
      spatial.distanceTo(obstacle),
      minimumDistance
    )
  }

  def reactTo(obstacle: PilotedVehicle, speedLimit: Velocity): Acceleration =
    this.reactTo(obstacle.spatial, speedLimit)
}

object PilotedVehicle {
  def commuter(spatial: Spatial, idm: IntelligentDriverModel): PilotedVehicle = {
      new PilotedVehicleImpl(
        Commuter(spatial, idm), Car(spatial))
  }

}

