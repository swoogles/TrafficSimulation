package com.billding.svgRendering

import squants.motion.{MetersPerSecond, MetersPerSecondSquared}
import squants.{Acceleration, Velocity}

/**
  * What a car is doing about its speed, reduced to the three cases worth drawing.
  *
  * This lives with the rendering rather than with the traffic because it is a question about
  * colour, not about physics: the simulation deals in a continuous acceleration, and the only
  * reason to sort it into buckets is that a person watching needs to see, at a glance, where
  * in the pack the braking is happening.
  */
sealed trait Motion

object Motion {

  case object Accelerating extends Motion
  case object Braking extends Motion
  case object Steady extends Motion

  /**
    * How much acceleration counts as holding steady.
    *
    * Traffic in equilibrium sits at exactly zero, so this exists for the approach to it: cars
    * settling after a disturbance trail off through ever smaller corrections, and without a
    * deadband the whole ring would twinkle red and green long after anything interesting
    * had stopped happening.
    */
  val Deadband: Acceleration = MetersPerSecondSquared(0.25)

  /** A car this slow is treated as stopped, whatever it is about to do next. */
  val Stopped: Velocity = MetersPerSecond(0.1)

  def of(speed: Velocity, acceleration: Acceleration): Motion =
    if (speed < Stopped) Steady
    else if (acceleration > Deadband) Accelerating
    else if (acceleration < -Deadband) Braking
    else Steady
}
