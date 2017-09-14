package com.billding.traffic

import squants.motion.Distance
import squants.{Acceleration, Time, Velocity}

/*
  Resources:
    https://en.wikipedia.org/wiki/Intelligent_driver_model
 */
trait IntelligentDriverModel {
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
                             ): Acceleration

  val name = "IDM"

}

object DefaultDriverModel {
  val idm = new IntelligentDriverModelImpl
}
