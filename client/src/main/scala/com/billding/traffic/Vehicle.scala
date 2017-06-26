package com.billding.traffic

import com.billding.physics.Spatial
import squants.motion.{Acceleration, Distance, DistanceUnit, VelocityUnit}
import squants.{Length, Mass, QuantityVector, Velocity}

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

object VehicleImpl {

  def simpleCar(p: QuantityVector[Distance],
                v: QuantityVector[Velocity]): VehicleImpl = {
    val d: QuantityVector[Length] = Spatial.convertToSVector(VehicleStats.Commuter.dimensions)
    val spatial = Spatial.withVecs(p, v, d)
    VehicleImpl(spatial,
      VehicleStats.Commuter.acceleration,
      VehicleStats.Commuter.deceleration,
      VehicleStats.Commuter.weight
    )
  }

  def simpleCar(pIn: (Double, Double, Double, DistanceUnit),
                vIn: (Double, Double, Double, VelocityUnit)): VehicleImpl = {
    val p = Spatial.convertToSVector(pIn)
    val v = Spatial.convertToSVector(vIn)
    simpleCar(p, v)
  }

}




