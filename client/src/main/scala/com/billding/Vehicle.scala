package com.billding

import client.Client
import squants.mass.Kilograms
import squants.motion._
import squants.space.{Kilometers, LengthUnit, Meters}
import squants.{Length, Mass, QuantityVector, SVector, Time, Velocity}
import squants.space.LengthConversions._
import squants.time.TimeConversions._

sealed trait Vehicle {
  val spatial: Spatial
  val weight: Mass
  val accelerationAbility: Acceleration
  val brakingAbility: Acceleration
}

case class Car(
                p: QuantityVector[Distance],
                v: QuantityVector[Velocity],
                accelerationAbility: Acceleration = (0.3.meters.per((1 seconds).squared)),
                brakingAbility: Acceleration = (3.0.meters.per((1 seconds).squared)),
                weight: Mass = Kilograms(800)
              ) extends Vehicle {
  val commuterDimensions: (Double, Double, Double, LengthUnit) = (4, 2, 0, Meters)
  val (dX, dY, dZ, dUnit: DistanceUnit) = commuterDimensions
  val d: QuantityVector[Length] = SVector(dX, dY, dZ).map{x=>dUnit(x)}
  val spatial = Spatial.withVecs(p, v, d)
}

object Car {
  def withVecs(
                pIn: (Double, Double, Double, DistanceUnit),
                vIn: (Double, Double, Double, VelocityUnit)
              ): Car = {
    val commuterDimensions: (Double, Double, Double, LengthUnit) = (4, 2, 0, Meters)
    val (pX, pY, pZ, pUnit) = pIn
    val (vX, vY, vZ, vUnit) = vIn
    val (dX, dY, dZ, dUnit: DistanceUnit) = commuterDimensions
    val p: QuantityVector[Distance] = SVector(pX, pY, pZ) .map(pUnit(_))
    val v: QuantityVector[Velocity] = SVector(vX, vY, vZ).map(vUnit(_))
    val d: QuantityVector[Length] = SVector(dX, dY, dZ).map{x=>dUnit(x)}
    Car(p, v)
  }

}

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
  val spatial: Spatial
}

case class PilotedVehicleImpl(driver: Commuter, vehicle: Car) extends PilotedVehicle {
  // TODO make a parameter
  private val idm = driver.idm
  private val weight = vehicle.weight
  val spatial: Spatial = vehicle.spatial
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
    val updatedSpatial: Spatial = Spatial.accelerateAlongCurrentDirection(spatial, dt, dP)
    this.copy(
      driver = driver.copy(spatial=updatedSpatial),
      vehicle = Car(updatedSpatial.r, updatedSpatial.v )
    )
  }

  def createInfiniteVehicle: PilotedVehicle = {
    /* This is a clue that it's in the wrong spot!
      The infinite vehicle shouldn't be based on particular vehicles dimensions.

     */
    val newPosition: QuantityVector[Distance] = this.spatial.r + this.spatial.v.normalize.map{ x: Velocity =>x * 1.hours}
    val newVelocity: QuantityVector[Velocity] = this.spatial.v.map{ x: Velocity =>x *2}
//    val newAcelerration: QuantityVector[Acceleration] = this.spatial.v.map{ x: Velocity =>x *2}.map { x: Velocity = x / Seconds(1)}
    /** TODO Get these passed in the right way. It will make much more sense
      when this exists in the [[com.billding.Lane]] class, and dt/dP are not need at all.
      */
    val dt = Client.dt
    val t = Client.t
    val dP = MetersPerSecondSquared(100000)
    val updatedSpatial: Spatial = Spatial.accelerateAlongCurrentDirection(spatial, dt, dP)
    this.copy(
      driver = driver.copy(spatial=updatedSpatial),
      vehicle = Car(updatedSpatial.r, updatedSpatial.v )
    )
//    Car( newPosition, newVelocity)
  }

}

object PilotedVehicle {

  val commuterDimensions: (Double, Double, Double, LengthUnit) = (4, 2, 0, Meters)

  def commuter(
                pIn: (Double, Double, Double, DistanceUnit),
                vIn: (Double, Double, Double, VelocityUnit),
                idm: IntelligentDriverModel
              ): PilotedVehicle = {
    val (pX, pY, pZ, pUnit) = pIn
    val (vX, vY, vZ, vUnit) = vIn
    val (dX, dY, dZ, dUnit: DistanceUnit) = commuterDimensions


    val p: QuantityVector[Distance] = SVector(pX, pY, pZ) .map(pUnit(_))
    val v: QuantityVector[Velocity] = SVector(vX, vY, vZ).map(vUnit(_))
    val d: QuantityVector[Length] = SVector(dX, dY, dZ).map{x=>dUnit(x)}
    val spatial = Spatial.withVecs(p, v, d)
      new PilotedVehicleImpl( Commuter(spatial, idm), Car(p, v))
  }
  /* TODO: Beware of arbitrary spacial.
    It should be locked down on Commuter.
   */

  def commuter(
              spatial: Spatial,
                idm: IntelligentDriverModel
              ): PilotedVehicle = {
    new PilotedVehicleImpl( Commuter(spatial, idm), Car(spatial.r, spatial.v))
  }

}

object TypeClassUsage {
  import com.billding.SpatialForDefaults.spatialForPilotedVehicle
  val idm: IntelligentDriverModel = new IntelligentDriverModelImpl
  val drivenVehicle1: PilotedVehicle = PilotedVehicle.commuter( (0, 0, 0, Kilometers), (120, 0, 0, KilometersPerHour), idm)
  val drivenVehicle2: PilotedVehicle = PilotedVehicle.commuter( (0, 2, 0, Kilometers), (120, 0, 0, KilometersPerHour), idm)

  val res: Spatial = SpatialForDefaults.disect(drivenVehicle1)

}
