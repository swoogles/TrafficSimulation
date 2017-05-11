package com.billding

import squants.{Time, Velocity}
import squants.motion.Distance
import squants.time.TimeConversions._
import squants.space.LengthConversions._

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
                     preferredDynamicSpacing: Time = (2 seconds),

                     /** TODO This is what I'm using to ensure a stop right now.
                       *
                       * Should be improved through other means discussed here:
                       * [[com.billding.rendering.CanvasRendering]]
                       */

                     minimumDistance: Distance = (400 meters),
                     desiredSpeed: Velocity = (120.kilometers.per(hour))
                   ) extends Driver

