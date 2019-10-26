package fr.iscpif.client.traffic

import java.util.UUID

import fr.iscpif.client.physics.{Spatial, SpatialFor, SpatialForDefaults, SpatialImpl}
import squants.motion.{Acceleration, Distance}
import squants.{QuantityVector, Time, Velocity}

case class PilotedVehicleImpl(
    driver: DriverImpl,
    vehicle: VehicleImpl,
    destination: SpatialImpl,
    uuid: UUID = java.util.UUID.randomUUID // Make this pure again. Random defaults are bad juju.
) extends PilotedVehicle {

  def spatial: SpatialImpl = vehicle.spatial

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
      case vehicle: PilotedVehicleImpl => vehicle.spatial
    }
    this.reactTo(SpatialForDefaults.disect(obstacle), speedLimit)
  }

  def accelerateAlongCurrentDirection(dt: Time,
                                      dP: Acceleration): PilotedVehicleImpl = {
    val updatedSpatial: SpatialImpl =
      Spatial.accelerateAlongCurrentDirection(spatial, dt, dP, destination)
    this.copy(
      driver = driver.updateSpatial(updatedSpatial),
      vehicle = vehicle.updateSpatial(updatedSpatial)
    )
  }

  def updateSpatial(spatialImpl: SpatialImpl): PilotedVehicleImpl = {
    this.copy(
      driver = driver.updateSpatial(spatialImpl),
      vehicle = vehicle.updateSpatial(spatialImpl)
    )
  }

  override def tooClose(pilotedVehicle: PilotedVehicle): Boolean = {
    val bumperToBumperOffset = this.spatial.dimensions.coordinates.head + pilotedVehicle.spatial.dimensions.coordinates
      .head
    (this.spatial
      .distanceTo(pilotedVehicle.spatial) - bumperToBumperOffset) < this.driver.minimumDistance
  }

  def stop(): PilotedVehicleImpl = {
    val newV = this.vehicle.spatial.updateVelocity(Spatial.ZERO_VELOCITY_VECTOR)
    // TODO Think we need to stop the driver here too...
    this.copy(
      vehicle = this.vehicle.updateSpatial(newV),
      driver = this.driver.updateSpatial(newV)
    )
  }

  def move(betterVec: QuantityVector[Distance]): PilotedVehicleImpl = {
    this.copy(
      driver = driver.move(betterVec),
      vehicle = vehicle.move(betterVec)
    )
  }

  def distanceTo(target: Spatial): Distance =
    spatial.distanceTo(target)

  def distanceTo(target: PilotedVehicle): Distance =
    distanceTo(target.spatial)

  def target(spatialImpl: SpatialImpl): PilotedVehicleImpl =
    this.copy(destination = spatialImpl)

}
