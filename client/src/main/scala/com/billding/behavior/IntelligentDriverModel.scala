package com.billding.behavior

import com.billding.{PilotedVehicle, Spatial, WeightedManeuver}
import squants.{Acceleration, Velocity}

/*
  Resources:
    https://en.wikipedia.org/wiki/Intelligent_driver_model
 */
trait IntelligentDriverModel {
  def reactTo(decider: PilotedVehicle, obstacle: Spatial, speedLimit: Velocity): Acceleration
}

