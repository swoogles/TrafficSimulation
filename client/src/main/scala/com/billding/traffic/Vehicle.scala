package com.billding.traffic

import com.billding.physics.SpatialForDefaults.spatialForPilotedVehicle
import com.billding.physics.{Spatial, SpatialForDefaults}
import squants.mass.Kilograms
import squants.motion._
import squants.space.LengthConversions._
import squants.space.{LengthUnit, Meters}
import squants.time.TimeConversions._
import squants.{Length, Mass, QuantityVector, Time, Velocity}

sealed trait Vehicle {
  val spatial: Spatial
  val weight: Mass
  val accelerationAbility: Acceleration
  val brakingAbility: Acceleration
}

case class VehicleImpl(
                        spatial: Spatial,
                        accelerationAbility: Acceleration,
                        brakingAbility: Acceleration,
                        weight: Mass
                      ) extends Vehicle

object VehicleStats {
  val trucker: (Double, Double, Double, LengthUnit) = (12, 2, 0, Meters)

  object Commuter {
    val minimumGap = 2.meters
    val acceleration = 1.meters.per((1 seconds).squared)
    val deceleration = 3.0.meters.per((1 seconds).squared)
    val dimensions: (Double, Double, Double, LengthUnit) = (4, 2, 0, Meters)
  }

  object Truck {
    val minimumGap = 2.meters
    val acceleration = 1.meters.per((1 seconds).squared)
    val deceleration = 2.0.meters.per((1 seconds).squared)
    val dimensions: (Double, Double, Double, LengthUnit) = (12, 2, 0, Meters)
  }
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

  def simpleCar(p: QuantityVector[Distance],
                v: QuantityVector[Velocity]): VehicleImpl = {
    val d: QuantityVector[Length] = Spatial.convertToSVector(VehicleStats.Commuter.dimensions)
    val spatial = Spatial.withVecs(p, v, d)
    VehicleImpl(spatial,
      (1.meters.per((1 seconds).squared)),
      (3.0.meters.per((1 seconds).squared)),
      Kilograms(800)
    )
  }

  def simpleCar(pIn: (Double, Double, Double, DistanceUnit),
                vIn: (Double, Double, Double, VelocityUnit)): VehicleImpl = {
    val p = Spatial.convertToSVector(pIn)
    val v = Spatial.convertToSVector(vIn)
    simpleCar(p, v)
  }

}

sealed trait PilotedVehicle {
  def reactTo(obstacle: Spatial, speedLimit: Velocity): Acceleration
  def reactTo(obstacle: PilotedVehicle, speedLimit: Velocity): Acceleration
  def accelerateAlongCurrentDirection(dt: Time, dP: Acceleration): PilotedVehicle
  val spatial: Spatial
  def tooClose(pilotedVehicle: PilotedVehicle): Boolean
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


