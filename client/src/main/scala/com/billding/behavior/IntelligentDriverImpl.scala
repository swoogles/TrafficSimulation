package com.billding.behavior

import com.billding.{PilotedVehicle, Spatial, WeightedManeuver}
import squants.{Length, Time}
import squants.motion.Distance
import squants.motion.MetersPerSecond
import squants.motion.MetersPerSecondSquared
import squants.motion.Velocity
import squants.motion.Acceleration
import squants.space.Meters

import scala.language.postfixOps
import squants.time.Seconds

import scala.math.pow
import scala.math.max

class IntelligentDriverImpl extends IntelligentDriverModel {
  // Acceleration Exponent. Don't really understand the significance of this.
  val aExp: Int = 4

  def reactTo(decider: PilotedVehicle, obstacle: Spatial, speedLimit: Velocity): Acceleration = {
    deltaVDimensionallySafe(
      decider.spatial.v.magnitude,
      speedLimit,
      (decider.spatial.v - obstacle.v).magnitude,
      decider.preferredDynamicSpacing,
      decider.accelerationAbility,
      decider.brakingAbility,
      (decider.spatial.p - obstacle.p).magnitude,
      decider.minimumDistance
    )
  }

  /**
    * This is one of the fundamental algorithms to this whole traffic project.
    * It is an established formula that produces realistic behavior in a single lane.
    * It continues to play this role when implementing a more complex, multi-lane MOBIL simulation.
    *
    * @param v Speed of the following vehicle
    * @param v0 desired speed when driving on a free road
    * @param dV Difference in speed between the 2 vehicles
    * @param T  Desired safety time headway when following other vehicles
    * @param a  Acceleration in everyday traffic
    * @param b  "Comfortable" braking deceleration in everyday traffic
    * @param s Actual gap between vehicles
    * @param s0 minimum bumper-to-bumper distance to the front vehicle
    * @return the acceleration to apply to the following vehicle.
    */
  private def deltaVDimensionallySafe(
                               v: Velocity,
                               v0: Velocity,
                               dV: Velocity,
                               T: Time,
                               a: Acceleration,
                               b: Acceleration,
                               s: Distance,
                               s0: Distance
                             ): Acceleration = {

    // sStar: desired dynamical distance
    def sStar(): Distance = {
      val v$ = v to MetersPerSecond
      val dV$ = dV to MetersPerSecond
      val T$ = T to Seconds
      val a$ = a to MetersPerSecondSquared
      val b$ = b to MetersPerSecondSquared
      val s0$ = s0 to Meters
      val inner = ((v$*T$) + ( (v$*dV$)/ (2 * Math.sqrt(a$*b$))))
      Meters(s0$ + max(0.0, inner))
    }

    val accelerationTerm = pow(v/v0, aExp)
    val brakingTerm = pow(sStar() / s, 2)

    a * (1 - accelerationTerm - brakingTerm)
  }

}
