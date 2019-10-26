package fr.iscpif.client.traffic

import fr.iscpif.client.physics.Spatial
import scala.language.postfixOps

import squants.space.LengthConversions._
import squants.time.TimeConversions._
import squants.motion.Distance
import squants.{QuantityVector, Time, Velocity}

trait Driver {
  val spatial: Spatial
  val reactionTime: Time
  val preferredDynamicSpacing: Time
  val minimumDistance: Distance
  val desiredSpeed: Velocity
  val idm: IntelligentDriverModelImpl
  def move(betterVec: QuantityVector[Distance]): DriverImpl
  def updateSpatial(spatial: Spatial): DriverImpl
}

object Driver {
  def commuter(spatial: Spatial, idm: IntelligentDriverModelImpl) = {
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

  def aggressive(spatial: Spatial, idm: IntelligentDriverModelImpl) = {
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


