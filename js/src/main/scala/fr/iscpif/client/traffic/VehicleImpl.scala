package fr.iscpif.client.traffic

import fr.iscpif.client.physics.Spatial
import squants.motion.{Acceleration, Distance, DistanceUnit, VelocityUnit}
import squants.{Length, Mass, QuantityVector, Velocity}

sealed trait Vehicle {
  val spatial: Spatial
  val weight: Mass
  val accelerationAbility: Acceleration
  val brakingAbility: Acceleration
  def move(betterVec: QuantityVector[Distance]): VehicleImpl
  def updateSpatial(spatial: Spatial): VehicleImpl
  val width: Distance
  val height: Distance
  def updateVelocity(newV: QuantityVector[Velocity]): VehicleImpl
}

case class VehicleImpl(
                        spatial: Spatial,
                        accelerationAbility: Acceleration,
                        brakingAbility: Acceleration,
                        weight: Mass
) extends Vehicle {
  def move(betterVec: QuantityVector[Distance]): VehicleImpl = {
    copy(spatial = spatial.copy(r = betterVec))
  }

  val width: Distance = spatial.dimensions.coordinates(0)
  val height: Distance = spatial.dimensions.coordinates(1)

  override def updateSpatial(spatial: Spatial): VehicleImpl =
    this.copy(spatial = spatial)

  def updateVelocity(newV: QuantityVector[Velocity]): VehicleImpl =
    this.copy(spatial = this.spatial.updateVelocity(newV))
}

object VehicleImpl {

  def apply(p: QuantityVector[Distance],
            v: QuantityVector[Velocity]): VehicleImpl = {
    val d: QuantityVector[Length] =
      Spatial.convertToSVector(VehicleStats.Commuter.dimensions)
    val spatial = Spatial.withVecs(p, v, d)
    VehicleImpl(spatial,
                VehicleStats.Commuter.acceleration,
                VehicleStats.Commuter.deceleration,
                VehicleStats.Commuter.weight)
  }

  def apply(pIn: (Double, Double, Double, DistanceUnit),
            vIn: (Double, Double, Double, VelocityUnit)): VehicleImpl = {
    val p = Spatial.convertToSVector(pIn)
    val v = Spatial.convertToSVector(vIn)
    apply(p, v)
  }

}
