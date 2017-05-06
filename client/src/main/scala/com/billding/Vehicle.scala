package com.billding

import squants.mass.Kilograms
import squants.motion.{Acceleration, Distance, KilogramForce, KilometersPerHour}
import squants.space.Kilometers
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


sealed trait PilotedVehicle {
  def reactTo(obstacle: Spatial, speedLimit: Velocity): Acceleration
  def reactTo(obstacle: PilotedVehicle, speedLimit: Velocity): Acceleration
  def accelerateAlongCurrentDirection(dt: Time, dP: Acceleration): PilotedVehicle
  def createInfiniteVehicle: PilotedVehicle
}

case class PilotedVehicleImpl(driver: Commuter, vehicle: Car) extends PilotedVehicle {
  // TODO make a parameter
  private val idm = driver.idm
  private val weight = vehicle.weight
  val spatial: Spatial = vehicle.spatial
  val otherSpatial = spatial
  private val accelerationAbility = vehicle.accelerationAbility
  private val brakingAbility = vehicle.brakingAbility
  private val preferredDynamicSpacing = driver.preferredDynamicSpacing
  private val minimumDistance = driver.minimumDistance
  // TODO Get rid of this meddling junk of p, v, and dimensions!!
  // Use a SpatialFor[PilotedVehicle] instead.
  private val dimensions = spatial.dimensions

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

  def reactTo(obstacle: PilotedVehicle, speedLimit: Velocity): Acceleration = {
    import com.billding.SpatialForDefaults.spatialForPilotedVehicle
    this.reactTo(SpatialForDefaults.disect(obstacle), speedLimit)
  }

  def accelerateAlongCurrentDirection(dt: Time, dP: Acceleration): PilotedVehicle = {
    import com.billding.SpatialForDefaults.spatialForPilotedVehicle
    val updatedSpatial = Spatial.accelerateAlongCurrentDirection(spatial, dt, dP)
    this.copy(driver=
    driver.copy(spatial=updatedSpatial),
    vehicle = vehicle.copy(spatial=updatedSpatial))
  }

  def createInfiniteVehicle: PilotedVehicle = {
    val newPosition = this.spatial.p + this.spatial.v.map{x: Velocity =>x * 1.hours}
    val newVelocity = this.spatial.v.map{x: Velocity =>x *2}
    this.copy(vehicle=
      vehicle.copy(spatial=
        Spatial.withVecs(newPosition, newVelocity)
      )
    )
  }
}

object PilotedVehicle {
  def commuter(spatial: Spatial, idm: IntelligentDriverModel): PilotedVehicle = {
      new PilotedVehicleImpl(
        Commuter(spatial, idm), Car(spatial))
  }

}

object TypeClassUsage {
  import com.billding.SpatialForDefaults.spatialForPilotedVehicle
  val idm: IntelligentDriverModel = new IntelligentDriverModelImpl
  val drivenVehicle1: PilotedVehicle = PilotedVehicle.commuter(Spatial( (0, 0, 0, Kilometers), (120, 0, 0, KilometersPerHour) ), idm)
  val drivenVehicle2: PilotedVehicle = PilotedVehicle.commuter(Spatial( (0, 2, 0, Kilometers), (120, 0, 0, KilometersPerHour) ), idm)

  val res: Spatial = SpatialForDefaults.disect(drivenVehicle1)

}
