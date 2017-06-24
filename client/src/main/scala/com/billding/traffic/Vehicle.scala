package com.billding.traffic

import client.Client
import com.billding.physics.SpatialForDefaults.spatialForPilotedVehicle
import com.billding.physics.{Spatial, SpatialForDefaults}
import squants.mass.Kilograms
import squants.motion._
import squants.space.LengthConversions._
import squants.space.{Kilometers, LengthUnit, Meters}
import squants.time.TimeConversions._
import squants.{Length, Mass, QuantityVector, SVector, Time, Velocity}

/** Instead of a sealed vehicle trait, I think I should just have a
  * VehicleParams instance that the Vehicle (Automobile?) case class
  * accepts.
  *
  */
sealed trait Vehicle {
  val spatial: Spatial
  val weight: Mass
  val accelerationAbility: Acceleration
  val brakingAbility: Acceleration
}

case class VehicleImpl(
                p: QuantityVector[Distance],
                v: QuantityVector[Velocity],
                accelerationAbility: Acceleration,
                brakingAbility: Acceleration,
                weight: Mass,
                d: QuantityVector[Length]
              ) extends Vehicle {
//  val dimensions: (Double, Double, Double, LengthUnit) = (4, 2, 0, Meters)
//  val (dX, dY, dZ, dUnit: DistanceUnit) = dimensions
//  = SVector(dX, dY, dZ).map{x=>dUnit(x)}
//  val d: QuantityVector[Length] = SVector(dX, dY, dZ).map{x=>dUnit(x)}
  val spatial = Spatial.withVecs(p, v, d)
}

/*
Parameter	        Value Car	  Value Truck	Remarks
Desired speed v0	120 km/h	  80 km/h	    For city traffic, one would adapt the desired speed while the other parameters essentially can be left unchanged.
Time headway T	  1.5 s	      1.7 s	      Recommendation in German driving schools: 1.8 s; realistic values vary between 2 s and 0.8 s and even below.
Minimum gap s0	  2.0 m	      2.0 m	      Kept at complete standstill, also in queues that are caused by red traffic lights.
Acceleration a	  0.3 m/s2	  0.3 m/s2	  Very low values to enhance the formation of stop-and go traffic. Realistic values are 1-2 m/s2
Deceleration b	  3.0 m/s2	  2.0 m/s2	  Very high values to enhance the formation of stop-and go traffic. Realistic values are 1-2 m/s2
 */
object VehicleImpl {
  def withVecs(
                pIn: (Double, Double, Double, DistanceUnit),
                vIn: (Double, Double, Double, VelocityUnit)
              ): VehicleImpl = {
    val commuterDimensions: (Double, Double, Double, LengthUnit) = (4, 2, 0, Meters)
    val (dX, dY, dZ, dUnit: DistanceUnit) = commuterDimensions
    val d: QuantityVector[Length] = SVector(dX, dY, dZ).map{x=>dUnit(x)}
    val (pX, pY, pZ, pUnit) = pIn
    val (vX, vY, vZ, vUnit) = vIn
    val p: QuantityVector[Distance] = SVector(pX, pY, pZ) .map(pUnit(_))
    val v: QuantityVector[Velocity] = SVector(vX, vY, vZ).map(vUnit(_))

    VehicleImpl(p, v,
      (1.meters.per((1 seconds).squared)),
      (3.0.meters.per((1 seconds).squared)),
    Kilograms(800),
    d)
  }

  def simpleCar(p: QuantityVector[Distance],
                v: QuantityVector[Velocity]): VehicleImpl = {
    val dimensions: (Double, Double, Double, LengthUnit) = (4, 2, 0, Meters)
    val (dX, dY, dZ, dUnit: DistanceUnit) = dimensions
    val d: QuantityVector[Length] = SVector(dX, dY, dZ).map{x=>dUnit(x)}
    VehicleImpl(p, v,
      (1.meters.per((1 seconds).squared)),
      (3.0.meters.per((1 seconds).squared)),
      Kilograms(800),
      d
    )
  }

}

sealed trait PilotedVehicle {
  def reactTo(obstacle: Spatial, speedLimit: Velocity): Acceleration
  def reactTo(obstacle: PilotedVehicle, speedLimit: Velocity): Acceleration
  def accelerateAlongCurrentDirection(dt: Time, dP: Acceleration): PilotedVehicle
  def createInfiniteVehicle: PilotedVehicle
  val spatial: Spatial
  def tooClose(pilotedVehicle: PilotedVehicle): Boolean
}

case class PilotedVehicleImpl(driver: DriverImpl, vehicle: VehicleImpl) extends PilotedVehicle {
  // TODO make a parameter
  private val idm = driver.idm
  val spatial: Spatial = vehicle.spatial

  def reactTo(obstacle: Spatial, speedLimit: Velocity): Acceleration = {
    idm.deltaVDimensionallySafe(
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
      vehicle = VehicleImpl(updatedSpatial.r, updatedSpatial.v, vehicle.accelerationAbility, vehicle.brakingAbility, vehicle.weight, vehicle.d )
    )
  }

  def createInfiniteVehicle: PilotedVehicle = {
    /*
        This is a clue that it's in the wrong spot!
        The infinite vehicle shouldn't be based on particular vehicles dimensions.

        TODO Get these passed in the right way. It will make much more sense
        when this exists in the [[Lane]] class, and dt/dP are not need at all.
      */
    val dt = Client.dt
    val dP = MetersPerSecondSquared(100000)
    val updatedSpatial: Spatial = Spatial.accelerateAlongCurrentDirection(spatial, dt, dP)
    this.copy(
      driver = driver.copy(spatial=updatedSpatial),
      vehicle = VehicleImpl.simpleCar(updatedSpatial.r, updatedSpatial.v )
    )
  }

  override def tooClose(pilotedVehicle: PilotedVehicle): Boolean = {
    val bumperToBumperOffset = this.spatial.dimensions.coordinates.head + pilotedVehicle.spatial.dimensions.coordinates.head
    (this.spatial.distanceTo(pilotedVehicle.spatial) - bumperToBumperOffset) < this.driver.minimumDistance
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
    val (dX, dY, dZ, dUnit: LengthUnit) = commuterDimensions


    val p: QuantityVector[Distance] = SVector(pX, pY, pZ) .map(pUnit(_))
    val v: QuantityVector[Velocity] = SVector(vX, vY, vZ).map(vUnit(_))
    val d: QuantityVector[Length] = SVector(dX, dY, dZ).map{x=>dUnit(x)}
    val spatial = Spatial.withVecs(p, v, d)
      new PilotedVehicleImpl( Driver.commuter(spatial, idm), VehicleImpl.simpleCar(p, v))
  }
  /* TODO: Beware of arbitrary spacial.
    It should be locked down on Commuter.
   */

  def commuter(
              spatial: Spatial,
                idm: IntelligentDriverModel
              ): PilotedVehicle = {
    new PilotedVehicleImpl( Driver.commuter(spatial, idm), VehicleImpl.simpleCar(spatial.r, spatial.v))
  }

}

object TypeClassUsage {
  val idm: IntelligentDriverModel = new IntelligentDriverModelImpl
  val drivenVehicle1: PilotedVehicle = PilotedVehicle.commuter( (0, 0, 0, Kilometers), (120, 0, 0, KilometersPerHour), idm)
  val drivenVehicle2: PilotedVehicle = PilotedVehicle.commuter( (0, 2, 0, Kilometers), (120, 0, 0, KilometersPerHour), idm)

  val res: Spatial = SpatialForDefaults.disect(drivenVehicle1)

}
