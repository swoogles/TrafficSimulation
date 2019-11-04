package com.billding.traffic

import com.billding.physics.Spatial
import squants.motion.{Acceleration, Distance, DistanceUnit, VelocityUnit}
import squants.{Length, Mass, QuantityVector, Velocity}

case class Vehicle(
                    spatial: Spatial,
                    accelerationAbility: Acceleration,
                    brakingAbility: Acceleration,
                    weight: Mass
                  ) {

  def move(betterVec: QuantityVector[Distance]): Vehicle =
    copy(spatial = spatial.copy(r = betterVec))

  val width: Distance = spatial.dimensions.coordinates(0)
  val height: Distance = spatial.dimensions.coordinates(1)

  def updateSpatial(spatial: Spatial): Vehicle =
    this.copy(spatial = spatial)

  def updateVelocity(newV: QuantityVector[Velocity]): Vehicle =
    this.copy(spatial = this.spatial.updateVelocity(newV))
}

object Vehicle {

  def apply(p: QuantityVector[Distance], v: QuantityVector[Velocity]): Vehicle = {
    val d: QuantityVector[Length] =
      Spatial.convertToSVector(VehicleStats.Commuter.dimensions) // TODO This needs to be higher up and obvious.
    val spatial = Spatial.withVecs(p, v, d)
    Vehicle(
      spatial,
      VehicleStats.Commuter.acceleration,
      VehicleStats.Commuter.deceleration,
      VehicleStats.Commuter.weight
    )
  }

  def apply(
             pIn: (Double, Double, Double, DistanceUnit),
             vIn: (Double, Double, Double, VelocityUnit)
           ): Vehicle = {
    val p = Spatial.convertToSVector(pIn)
    val v = Spatial.convertToSVector(vIn)
    apply(p, v)
  }

}
