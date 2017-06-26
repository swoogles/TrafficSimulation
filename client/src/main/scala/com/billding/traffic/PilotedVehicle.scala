package com.billding.traffic

import squants.motion.Acceleration
import com.billding.physics.{Spatial, SpatialForDefaults}
import squants.motion.{DistanceUnit, VelocityUnit}
import squants.{Time, Velocity}

sealed trait PilotedVehicle {
  def reactTo(obstacle: Spatial, speedLimit: Velocity): Acceleration
  def reactTo(obstacle: PilotedVehicle, speedLimit: Velocity): Acceleration
  def accelerateAlongCurrentDirection(dt: Time, dP: Acceleration): PilotedVehicle
  val spatial: Spatial
  def tooClose(pilotedVehicle: PilotedVehicle): Boolean
}

object PilotedVehicle {

  def commuter(
                pIn: (Double, Double, Double, DistanceUnit),
                vIn: (Double, Double, Double, VelocityUnit),
                idm: IntelligentDriverModel
              ): PilotedVehicle = {
    val spatial = Spatial(pIn, vIn, VehicleStats.Commuter.dimensions)
      new PilotedVehicleImpl( Driver.commuter(spatial, idm), VehicleImpl.simpleCar(pIn, vIn))
  }
  // TODO: Beware of arbitrary spacial. It should be locked down on Commuter.
  def commuter(
              spatial: Spatial,
                idm: IntelligentDriverModel
              ): PilotedVehicle = {
    new PilotedVehicleImpl( Driver.commuter(spatial, idm), VehicleImpl.simpleCar(spatial.r, spatial.v))
  }

}

case class PilotedVehicleImpl(driver: DriverImpl, vehicle: VehicleImpl) extends PilotedVehicle {
  val spatial: Spatial = vehicle.spatial

  def reactTo(obstacle: Spatial, speedLimit: Velocity): Acceleration = {
    driver.idm.deltaVDimensionallySafe(
      spatial.v.magnitude, // TODO Make a Spatial function
      speedLimit,
      spatial.relativeVelocityMag(obstacle),
      driver.preferredDynamicSpacing,
      vehicle.accelerationAbility,
      vehicle.brakingAbility,
      spatial.distanceTo(obstacle),
      driver.minimumDistance
    )
  }

  def reactTo(obstacle: PilotedVehicle, speedLimit: Velocity): Acceleration = {
    this.reactTo(SpatialForDefaults.disect(obstacle), speedLimit)
  }

  def accelerateAlongCurrentDirection(dt: Time, dP: Acceleration): PilotedVehicle = {
    val updatedSpatial: Spatial = Spatial.accelerateAlongCurrentDirection(spatial, dt, dP)
    this.copy(
      driver = driver.copy(spatial=updatedSpatial),
      vehicle = vehicle.copy(spatial = updatedSpatial)
    )
  }

  override def tooClose(pilotedVehicle: PilotedVehicle): Boolean = {
    val bumperToBumperOffset = this.spatial.dimensions.coordinates.head + pilotedVehicle.spatial.dimensions.coordinates.head
    (this.spatial.distanceTo(pilotedVehicle.spatial) - bumperToBumperOffset) < this.driver.minimumDistance
  }
}
