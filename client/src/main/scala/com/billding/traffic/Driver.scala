package com.billding.traffic

import com.billding.physics.Spatial
import squants.motion.Distance
import squants.space.LengthConversions._
import squants.time.TimeConversions._
import squants.{Time, Velocity}

trait Driver {
  val spatial: Spatial
  val reactionTime: Time
  val preferredDynamicSpacing: Time
  val minimumDistance: Distance
  val desiredSpeed: Velocity
  val idm: IntelligentDriverModel
}

case class Commuter(
                     spatial: Spatial,
                     idm: IntelligentDriverModel,
                     reactionTime: Time = (0.5 seconds),
                     preferredDynamicSpacing: Time = (1 seconds),

                     /** TODO This is what I'm using to ensure a stop right now.
                       *
                       * Should be improved through other means discussed here:
                       * [[com.billding.rendering.CanvasRendering]]
                       */

                     minimumDistance: Distance = (2 meters),
                     desiredSpeed: Velocity = (120.kilometers.per(hour))
                   ) extends Driver

