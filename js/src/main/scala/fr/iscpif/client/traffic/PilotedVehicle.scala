package fr.iscpif.client.traffic

import java.util.UUID

import squants.motion.{Acceleration, Distance, DistanceUnit, KilometersPerHour, VelocityUnit}
import fr.iscpif.client.physics.{Spatial, SpatialImpl}
import squants.space.LengthUnit
import squants.{QuantityVector, Time, Velocity}

trait PilotedVehicle {
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
