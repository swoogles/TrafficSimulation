package fr.iscpif.client.previouslySharedCode.traffic

import fr.iscpif.client.previouslySharedCode.physics.SpatialImpl
import fr.iscpif.client.previouslySharedCode.serialization.BillSquants

import play.api.libs.json.{Format, Json}

import scala.language.postfixOps

import squants.space.LengthConversions._
import squants.time.TimeConversions._
import squants.motion.Distance
import squants.{QuantityVector, Time, Velocity}

trait Driver {
  val spatial: SpatialImpl
  val reactionTime: Time
  val preferredDynamicSpacing: Time
  val minimumDistance: Distance
  val desiredSpeed: Velocity
  val idm: IntelligentDriverModelImpl
  def move(betterVec: QuantityVector[Distance]): DriverImpl
  def updateSpatial(spatial: SpatialImpl): DriverImpl
}

object Driver {
  def commuter(spatial: SpatialImpl, idm: IntelligentDriverModelImpl) = {
    val reactionTime: Time = 0.5 seconds
    val preferredDynamicSpacing: Time = 1 seconds
    val minimumDistance: Distance = 2 meters
    val desiredSpeed: Velocity = 120.kilometers.per(hour)
    DriverImpl(spatial,
               idm,
               reactionTime,
               preferredDynamicSpacing,
               minimumDistance,
               desiredSpeed)
  }

  def aggressive(spatial: SpatialImpl, idm: IntelligentDriverModelImpl) = {
    val reactionTime: Time = 0.5 seconds
    val preferredDynamicSpacing: Time = 0.5 seconds
    val minimumDistance: Distance = 1 meters
    val desiredSpeed: Velocity = 150.kilometers.per(hour)
    DriverImpl(spatial,
               idm,
               reactionTime,
               preferredDynamicSpacing,
               minimumDistance,
               desiredSpeed)
  }

}

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

object DriverImpl {
}
