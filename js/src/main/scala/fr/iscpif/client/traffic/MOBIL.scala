package fr.iscpif.client.traffic

import squants.motion.MetersPerSecondSquared
import squants.{Acceleration, Velocity}

/**
  MOBIL: Minimizing Overall Braking Induced by Lane Change
  This is what I'm really working towards, since it will enable triggering traffic
  waves with a single errant lane change.

  Resources:
  http://akesting.de/download/MOBIL_TRR_2007.pdf
  http://www.traffic-simulation.de/info/MOBIL.html
  */
class MOBIL {
  val safeBreaking = MetersPerSecondSquared(3) // aka b_save
  /* TODO Is this doing/going-to-do anything?
  def approachingVehicleInNeighboringLane(
      self: PilotedVehicleImpl,
      desiredLane: LaneImpl
  ): VehicleImpl = ???
   */

  def safetyCriterion(
      self: PilotedVehicleImpl,
      approachingVehicle: PilotedVehicleImpl, // aka B'
      speedLimit: Velocity // Does this belong here?
  ): Boolean = {
    /*
      - Transpose self in front of approaching vehicle.
        -What's a good way to do this irrespective of street orientation?
        -This *might* be avoidable, if I just use the dead reckoning distance.
          At high speeds, it would be a safer approximation than in slow, tightly packed traffic.
      -
     */
    val shiftedSelf = self

    val potentialDeceleration: Acceleration = // aka  acc' (B')
      approachingVehicle.reactTo(shiftedSelf, speedLimit)

    potentialDeceleration > -safeBreaking
  }

  /*
    In direct mathy terms:
    acc' (M') - acc (M) > p [ acc (B) + acc (B') - acc' (B) - acc' (B') ] + athr
   */
  def incentiveCriterion(
      self: PilotedVehicleImpl, // aka M
      curLeadingVehicle: PilotedVehicleImpl, // Not in formula, but needed for acc(M)
      newLeadingVehicle: PilotedVehicleImpl, // Not in formula, but needed for acc'(M')
      curFollowingVehicle: PilotedVehicleImpl, // aka B
      newFollowingVehicle: PilotedVehicleImpl, // aka B'
      athr: Acceleration,
      p: Double,
      speedLimit: Velocity // Does this belong here?
  ): Boolean = {
    val shiftedSelf = self
    val currentAcceleration = self.reactTo(curLeadingVehicle, speedLimit)
    val potentialAcceleration =
      shiftedSelf.reactTo(newLeadingVehicle, speedLimit)
    val impactOnCurFollowerNoChange =
      curFollowingVehicle.reactTo(self, speedLimit) // acc(B)
    val impactOnNewFollowerNoChange =
      newFollowingVehicle.reactTo(shiftedSelf, speedLimit) // acc(B')
    val impactOnCurFollowerWithChange =
      curFollowingVehicle.reactTo(curLeadingVehicle, speedLimit) // acc'(B)
    val impactOnNewFollowerWithChange =
      newFollowingVehicle.reactTo(newLeadingVehicle, speedLimit) // acc'(B')
    (potentialAcceleration - currentAcceleration) >
      p * (impactOnCurFollowerNoChange + impactOnNewFollowerNoChange - impactOnCurFollowerWithChange - impactOnNewFollowerWithChange) + athr
  }

}
