package com.billding.traffic

import scala.language.postfixOps

import squants.space.LengthConversions._
import squants.time.TimeConversions._

import com.billding.physics.Spatial
import squants.motion.Distance
import squants.{QuantityVector, Time, Velocity}

case class Driver(
                   spatial: Spatial,
                   idm: IntelligentDriverModelImpl,
                   reactionTime: Time,
                   preferredDynamicSpacing: Time,
                   minimumDistance: Distance,
                   desiredSpeed: Velocity
                 ) {

  def move(betterVec: QuantityVector[Distance]): Driver =
    copy(spatial = spatial.copy(r = betterVec))

  def updateSpatial(spatial: Spatial) =
    this.copy(spatial = spatial)

}

object Driver {

  def commuter(spatial: Spatial, idm: IntelligentDriverModelImpl) = {
    val reactionTime: Time = 0.5 seconds
    val preferredDynamicSpacing: Time = 1 seconds
    val minimumDistance: Distance = 2 meters
    val desiredSpeed: Velocity = 120.kilometers.per(hour)
    Driver(spatial, idm, reactionTime, preferredDynamicSpacing, minimumDistance, desiredSpeed)
  }

  def aggressive(spatial: Spatial, idm: IntelligentDriverModelImpl) = {
    val reactionTime: Time = 0.5 seconds
    val preferredDynamicSpacing: Time = 0.5 seconds
    val minimumDistance: Distance = 1 meters
    val desiredSpeed: Velocity = 150.kilometers.per(hour)
    Driver(spatial, idm, reactionTime, preferredDynamicSpacing, minimumDistance, desiredSpeed)
  }

}
