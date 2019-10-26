package fr.iscpif.client.previouslySharedCode.traffic

import java.util.UUID

import squants.motion.{Acceleration, Distance, DistanceUnit, KilometersPerHour, VelocityUnit}
import fr.iscpif.client.previouslySharedCode.physics.{Spatial, SpatialFor, SpatialForDefaults, SpatialImpl}
import fr.iscpif.client.previouslySharedCode.serialization.BillSquants
import play.api.libs.json.{Format, Json}
import squants.space.LengthUnit
import squants.{Mass, QuantityVector, Time, Velocity}

sealed trait PilotedVehicle {
  def reactTo(obstacle: Spatial, speedLimit: Velocity): Acceleration
  def reactTo(obstacle: PilotedVehicle, speedLimit: Velocity): Acceleration
  def accelerateAlongCurrentDirection(dt: Time,
                                      dP: Acceleration): PilotedVehicleImpl
  def spatial: SpatialImpl
  def tooClose(pilotedVehicle: PilotedVehicle): Boolean
  val width: Distance
  val height: Distance
  def stop(): PilotedVehicleImpl
  def move(betterVec: QuantityVector[Distance]): PilotedVehicleImpl
  def updateSpatial(spatialImpl: SpatialImpl): PilotedVehicleImpl
  def distanceTo(target: Spatial): Distance
  def distanceTo(target: PilotedVehicle): Distance
  def target(spatialImpl: SpatialImpl): PilotedVehicleImpl
  val uuid: UUID
}

object PilotedVehicle {

  val idm: IntelligentDriverModelImpl = new IntelligentDriverModelImpl
  def apply(
      pIn1: (Double, Double, Double, LengthUnit),
      vIn1: (Double, Double, Double, VelocityUnit) =
        (0, 0, 0, KilometersPerHour),
      endingSpatial: SpatialImpl = Spatial.BLANK): PilotedVehicleImpl = {
    PilotedVehicle.commuter(Spatial(pIn1, vIn1), idm, endingSpatial)
  }

  def commuter(
      pIn: (Double, Double, Double, DistanceUnit),
      vIn: (Double, Double, Double, VelocityUnit),
      idm: IntelligentDriverModelImpl,
      destination: SpatialImpl
  ): PilotedVehicleImpl = {
    val spatial = Spatial(pIn, vIn, VehicleStats.Commuter.dimensions)
    PilotedVehicleImpl(Driver.commuter(spatial, idm),
                       VehicleImpl.apply(pIn, vIn),
                       destination)
  }
  // TODO: Beware of arbitrary spacial. It should be locked down on Commuter.
  def commuter(
      spatial: SpatialImpl,
      idm: IntelligentDriverModelImpl,
      destination: SpatialImpl = Spatial.BLANK
  ): PilotedVehicleImpl = {
    PilotedVehicleImpl(Driver.commuter(spatial, idm),
                       VehicleImpl(spatial.r, spatial.v),
                       destination)
  }

}

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

object PilotedVehicleImpl {
  implicit val tf: Format[Time] = BillSquants.time.format
  implicit val df: Format[Distance] = BillSquants.distance.format
  implicit val vf: Format[Velocity] = BillSquants.velocity.format
  implicit val dQvf: Format[QuantityVector[Distance]] =
    BillSquants.distance.formatQv
  implicit val vQvf: Format[QuantityVector[Velocity]] =
    BillSquants.velocity.formatQv
  implicit val spatialFormat: Format[SpatialImpl] = Json.format[SpatialImpl]
  implicit val driverFormat: Format[DriverImpl] = Json.format[DriverImpl]
  implicit val mf: Format[Mass] = BillSquants.mass.format
  implicit val af: Format[Acceleration] = BillSquants.acceleration.format

  implicit val vehicleFormat: Format[VehicleImpl] = Json.format[VehicleImpl]
  implicit val spatialForPilotedVehicle: SpatialFor[PilotedVehicle] = {
    case vehicle: PilotedVehicleImpl => vehicle.spatial
  }
  implicit val pilotedVehicleFormat: Format[PilotedVehicleImpl] =
    Json.format[PilotedVehicleImpl]
}
