package com.billding.traffic

import client.Client
import squants.motion.Acceleration
import com.billding.physics.{Spatial, SpatialForDefaults, SpatialImpl}
import com.billding.physics.SpatialForDefaults.spatialForPilotedVehicle
import squants.motion.{DistanceUnit, VelocityUnit}
import squants.time.Seconds
import squants.{Time, Velocity}

sealed trait PilotedVehicle {
  def reactTo(obstacle: Spatial, speedLimit: Velocity): Acceleration
  def reactTo(obstacle: PilotedVehicle, speedLimit: Velocity): Acceleration
  def accelerateAlongCurrentDirection(dt: Time, dP: Acceleration): PilotedVehicleImpl
  def spatial: Spatial
  def tooClose(pilotedVehicle: PilotedVehicle): Boolean
}

object PilotedVehicle {

  def commuter(
                pIn: (Double, Double, Double, DistanceUnit),
                vIn: (Double, Double, Double, VelocityUnit),
                idm: IntelligentDriverModel,
                destination: Spatial
              ): PilotedVehicleImpl = {
    val spatial = Spatial(pIn, vIn, VehicleStats.Commuter.dimensions)
      new PilotedVehicleImpl( Driver.commuter(spatial, idm), VehicleImpl.simpleCar(pIn, vIn), destination)
  }
  // TODO: Beware of arbitrary spacial. It should be locked down on Commuter.
  def commuter(
              spatial: SpatialImpl,
              idm: IntelligentDriverModel,
              destination: Spatial
              ): PilotedVehicleImpl = {
    new PilotedVehicleImpl( Driver.commuter(spatial, idm), VehicleImpl.simpleCar(spatial.r, spatial.v), destination)
  }

}

case class PilotedVehicleImpl(driver: DriverImpl, vehicle: VehicleImpl, destination: Spatial) extends PilotedVehicle {

  def spatial: Spatial = vehicle.spatial

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

  def accelerateAlongCurrentDirection(dt: Time, dP: Acceleration): PilotedVehicleImpl = {
    val updatedSpatial: SpatialImpl = Spatial.accelerateAlongCurrentDirection(spatial, dt, dP, destination)
    pprint.pprintln(updatedSpatial)
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
