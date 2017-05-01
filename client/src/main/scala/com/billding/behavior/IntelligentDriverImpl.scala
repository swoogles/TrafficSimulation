package com.billding.behavior

/**
  * Created by bfrasure on 4/29/17.
  */
class IntelligentDriverModel {
  /*
  For the Intelligent-Driver Model, any update time steps below 0.5 seconds will essentially
  lead to the same result, i.e., sufficiently approximate the true solution.

  Resources:
  http://www.traffic-simulation.de/info/IDM.html
  */

  /*
  The IDM has intuitive parameters:
    - , v0
    -, T
    -, a
    -, b
    -, s0
    - acceleration exponent, delta.
   */

  /**
    * This is one of the fundamental algorithms to this whole traffic project.
    * It is an established formula that produces realistic behavior in a single lane.
    * It continues to play this role when implementing a more complex, multi-lane MOBIL simulation.
    *
    * @param v0 desired speed when driving on a free road
    * @param T desired safety time headway when following other vehicles
    * @param a acceleration in everyday traffic
    * @param b "comfortable" braking deceleration in everyday traffic
    * @param s0 minimum bumper-to-bumper distance to the front vehicle
    * @return the acceleration to apply to the following vehicle.
    */
  def deltaV(v0: Float, T: Float, a: Float, b: Float, s0: Float): Float = ???

}
