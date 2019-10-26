package fr.iscpif.client.traffic

import fr.iscpif.client.physics.SpatialImpl
import squants.motion.Distance
import squants.{QuantityVector, Time, Velocity}

case class DriverImpl(
    spatial: SpatialImpl,
    idm: IntelligentDriverModelImpl,
    reactionTime: Time,
    preferredDynamicSpacing: Time,
    minimumDistance: Distance,
    desiredSpeed: Velocity
) extends Driver {

  def move(betterVec: QuantityVector[Distance]): DriverImpl = {
    copy(spatial = spatial.copy(r = betterVec))
  }

  override def updateSpatial(spatial: SpatialImpl) =
    this.copy(spatial = spatial)

}
