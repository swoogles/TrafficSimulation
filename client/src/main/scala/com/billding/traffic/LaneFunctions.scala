package com.billding.traffic

import squants.motion.Acceleration
import squants.{Time, Velocity}

trait LaneFunctions {
  // TODO: Test new vehicles from source
  def update(lane: LaneImpl, speedLimit: Velocity, t: Time, dt: Time): Lane
  def responsesInOneLanePrep(lane: Lane, speedLimit: Velocity): List[Acceleration]
}
