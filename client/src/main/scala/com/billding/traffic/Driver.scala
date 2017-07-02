package com.billding.traffic

import com.billding.physics.{Spatial, SpatialImpl}
import squants.motion.Distance
import squants.space.LengthConversions._
import squants.time.TimeConversions._
import squants.{Time, Velocity}

trait Driver {
  val spatial: SpatialImpl
  val reactionTime: Time
  val preferredDynamicSpacing: Time
  val minimumDistance: Distance
  val desiredSpeed: Velocity
  val idm: IntelligentDriverModel
}

object Driver {
  def commuter(
                spatial: SpatialImpl,
                idm: IntelligentDriverModel) = {
    val reactionTime: Time = (0.5 seconds)
    val preferredDynamicSpacing: Time = (1 seconds)
    val minimumDistance: Distance = (2 meters)
    val desiredSpeed: Velocity = (120.kilometers.per(hour))
    DriverImpl(spatial, idm, reactionTime, preferredDynamicSpacing, minimumDistance, desiredSpeed)
  }

  def aggressive(
                spatial: SpatialImpl,
                idm: IntelligentDriverModel) = {
    val reactionTime: Time = (0.5 seconds)
    val preferredDynamicSpacing: Time = (0.5 seconds)
    val minimumDistance: Distance = (1 meters)
    val desiredSpeed: Velocity = (150.kilometers.per(hour))
    DriverImpl(spatial, idm, reactionTime, preferredDynamicSpacing, minimumDistance, desiredSpeed)
  }

}

case class DriverImpl(
                     spatial: SpatialImpl,
                     idm: IntelligentDriverModel,
                     reactionTime: Time,
                     preferredDynamicSpacing: Time,
                     minimumDistance: Distance,
                     desiredSpeed: Velocity
                   ) extends Driver
