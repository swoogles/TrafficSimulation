package com.billding
package com.billding

import org.scalatest._
import squants.Length
import squants.motion._
import squants.space.{Kilometers, LengthUnit, Meters}
import squants.time.Seconds
import scala.language.postfixOps
import squants.time.TimeConversions._

/* This is actually the *better* test since I'm only accelerating in one direction
* It directly tests whether you're slowing down or speeding up the object
* If it went through and ensured that *every* value was decreased, it would fail in
* many cases, for example 1 dimensional motion, where only 1 vector will decrease.
*/
class SpatialTest extends FlatSpec {
  it should "accelerate a spatial in the direction of travel." in {
    val startingSpacial = Spatial(
      (0, 0, 0, Kilometers),
      (120, 0, 0, KilometersPerHour)
    )
    val acceleration = MetersPerSecondSquared(1)
    val dt = Seconds(1)
    val endingSpatial = Spatial.update(startingSpacial, dt, acceleration)
    endingSpatial.p.magnitude > startingSpacial.p.magnitude
  }

  it should "decelerate a spatial in the direction of travel." in {
    val startingSpacial = Spatial(
      (0, 0, 0, Kilometers),
      (120, 0, 0, KilometersPerHour)
    )
    val acceleration = -MetersPerSecondSquared(1)
    val dt = 1 seconds
    val endingSpatial = Spatial.update(startingSpacial, dt, acceleration)
    endingSpatial.p.magnitude < startingSpacial.p.magnitude
  }
}
