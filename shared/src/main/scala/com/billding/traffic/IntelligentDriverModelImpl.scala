package com.billding.traffic

import squants.Time
import squants.motion.{Acceleration, Distance, Velocity}
import squants.space.Meters

import scala.language.postfixOps
import scala.math.{max, pow}

case class IntelligentDriverModelImpl(name: String = "simpleIdm") extends IntelligentDriverModel {
  // Acceleration Exponent. Don't really understand the significance of this.
  // It's basically a magic value via research done by others.
  val aExp: Int = 4

  /**
    * This is one of the fundamental algorithms to this whole traffic project.
    * It is an established formula that produces realistic behavior in a single lane.
    * It continues to play this role when implementing a more complex, multi-lane MOBIL simulation.
    *
    * @param v Speed of the following vehicle
    * @param v0 desired speed when driving on a free road
    *           as in "the v we are trying to return to. Our resting state"
    * @param dV Difference in speed between the 2 vehicles
    * @param T  Desired safety time headway when following other vehicles
    * @param a  Acceleration in everyday traffic
    * @param b  "Comfortable" braking deceleration in everyday traffic
    * @param s Actual gap between vehicles
    * @param s0 minimum bumper-to-bumper distance to the front vehicle
    * @return the acceleration to apply to the following vehicle.
    */
  def deltaVDimensionallySafe(
                               v: Velocity,
                               v0: Velocity,
                               dV: Velocity,
                               T: Time,
                               a: Acceleration,
                               b: Acceleration,
                               s: Distance,
                               s0: Distance
                             ): Acceleration = {

    val desiredDistance = sStar( v, dV, T, a, b, s0 )
    val accelerationTerm = pow(v/v0, aExp)
    val brakingTerm = pow(desiredDistance / s, 2)

    a * (1 - accelerationTerm - brakingTerm)
  }



  private def sStar(
                     v: Velocity,
                     dV: Velocity,
                     T: Time,
                     a: Acceleration,
                     b: Acceleration,
                     s0: Distance

                   ): Distance = {
    /*
    If the desired distance is negative, that means:
      - We've gotten too close to the car in front of us
        ?Is this a Warning as opposed to a Failure?
      -We've hit the car in front of us
     */
    val desiredDistance =
      (v*T).toMeters
    + (v.toMetersPerSecond * dV.toMetersPerSecond) /
      2 * Math.sqrt(a.toMetersPerSecondSquared*b.toMetersPerSecondSquared)
    Meters(s0.toMeters + max(0.0, desiredDistance)) // This prevents reversing.
  }

}
