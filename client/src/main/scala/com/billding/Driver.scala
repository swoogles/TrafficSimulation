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
}

case class Commuter(
                     spatial: Spatial,
                     reactionTime: Time = (0.5 seconds),
                     preferredDynamicSpacing: Time = (1 seconds),
                     minimumDistance: Distance = (1 meters),
                     desiredSpeed: Velocity = (120.kilometers.per(hour))
                   ) extends Driver

sealed trait Maneuver
/*
  Decide how much of a spectrum Braking and Accelerating can use..
  Version 1 will probably only have 1 hard setting for each.
 */
case object Brake extends Maneuver
case object Accelerate extends Maneuver
case object Maintain extends Maneuver // Should this also be the move when driver is "cooling down" ?
/*
  This will be a slight decrease in speed. To be more accurate, it would be a larger decrease when travelling
  at a higher speed with increased wind resistance.
  Probably going to save this for a later version, as a simple simulation can run without this.
 */
case object Coast extends Maneuver

trait WeightedManeuver {
  val maneuver: Maneuver
  val urgency: Float
}

