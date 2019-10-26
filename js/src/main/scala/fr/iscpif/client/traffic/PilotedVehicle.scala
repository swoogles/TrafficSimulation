package fr.iscpif.client.traffic

import java.util.UUID

import squants.motion.{DistanceUnit, KilometersPerHour, VelocityUnit}
import squants.space.LengthUnit

import fr.iscpif.client.physics.{Spatial, SpatialFor, SpatialForDefaults}
import squants.motion.{Acceleration, Distance}
import squants.{QuantityVector, Time, Velocity}

case class PilotedVehicle(
                           driver: Driver,
                           vehicle: Vehicle,
                           destination: Spatial,
                           uuid: UUID
) {

  def spatial: Spatial = vehicle.spatial

  val width: Distance = vehicle.width
  val height: Distance = vehicle.height

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
    implicit val spatialForPilotedVehicle: SpatialFor[PilotedVehicle] = {
      case vehicle: PilotedVehicle => vehicle.spatial
    }
    this.reactTo(SpatialForDefaults.disect(obstacle), speedLimit)
  }

  def accelerateAlongCurrentDirection(dt: Time,
                                      dP: Acceleration): PilotedVehicle = {
    val updatedSpatial: Spatial =
      Spatial.accelerateAlongCurrentDirection(spatial, dt, dP, destination)
    this.copy(
      driver = driver.updateSpatial(updatedSpatial),
      vehicle = vehicle.updateSpatial(updatedSpatial)
    )
  }

  def updateSpatial(spatialImpl: Spatial): PilotedVehicle = {
    this.copy(
      driver = driver.updateSpatial(spatialImpl),
      vehicle = vehicle.updateSpatial(spatialImpl)
    )
  }

  def tooClose(pilotedVehicle: PilotedVehicle): Boolean = {
    val bumperToBumperOffset = this.spatial.dimensions.coordinates.head + pilotedVehicle.spatial.dimensions.coordinates
      .head
    (this.spatial
      .distanceTo(pilotedVehicle.spatial) - bumperToBumperOffset) < this.driver.minimumDistance
  }

  def stop(): PilotedVehicle = {
    val newV = this.vehicle.spatial.updateVelocity(Spatial.ZERO_VELOCITY_VECTOR)
    // TODO Think we need to stop the driver here too...
    this.copy(
      vehicle = this.vehicle.updateSpatial(newV),
      driver = this.driver.updateSpatial(newV)
    )
  }

  def move(betterVec: QuantityVector[Distance]): PilotedVehicle = {
    this.copy(
      driver = driver.move(betterVec),
      vehicle = vehicle.move(betterVec)
    )
  }

  def distanceTo(target: Spatial): Distance =
    spatial.distanceTo(target)

  def distanceTo(target: PilotedVehicle): Distance =
    distanceTo(target.spatial)

  def target(spatialImpl: Spatial): PilotedVehicle =
    this.copy(destination = spatialImpl)

}

object PilotedVehicle {

  val idm: IntelligentDriverModelImpl = new IntelligentDriverModelImpl

  def apply(
             driver: Driver,
             vehicle: Vehicle,
             destination: Spatial
   ): PilotedVehicle =
    PilotedVehicle(
      driver: Driver,
      vehicle: Vehicle,
      destination: Spatial,
      java.util.UUID.randomUUID // Make this pure again. Random defaults are bad juju.
    )

  def apply(
             pIn1: (Double, Double, Double, LengthUnit),
             vIn1: (Double, Double, Double, VelocityUnit) =
             (0, 0, 0, KilometersPerHour),
             endingSpatial: Spatial = Spatial.BLANK): PilotedVehicle = {
    PilotedVehicle.commuter2(Spatial(pIn1, vIn1), idm, endingSpatial)
  }

  def commuter(
                pIn: (Double, Double, Double, DistanceUnit),
                vIn: (Double, Double, Double, VelocityUnit),
                idm: IntelligentDriverModelImpl,
                destination: Spatial
              ): PilotedVehicle = {
    val spatial = Spatial(pIn, vIn, VehicleStats.Commuter.dimensions)
    PilotedVehicle(Driver.commuter(spatial, idm),
      Vehicle.apply(pIn, vIn),
      destination)
  }
  // TODO: Beware of arbitrary spacial. It should be locked down on Commuter.
  def commuter2(
                 spatial: Spatial,
                 idm: IntelligentDriverModelImpl,
                 destination: Spatial
              ): PilotedVehicle = {
    PilotedVehicle(Driver.commuter(spatial, idm),
      Vehicle(spatial.r, spatial.v),
      destination)
  }

}
