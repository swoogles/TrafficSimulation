package com.billding.traffic

import squants.Acceleration
import squants.mass.Kilograms
import squants.space.LengthConversions._
import squants.space.{Length, LengthUnit, Meters}
import squants.time.TimeConversions._

/*
Parameter	        Value Car	  Value Truck	Remarks
Desired speed v0	120 km/h	  80 km/h	    For city traffic, one would adapt the desired speed while the other parameters essentially can be left unchanged.
Time headway T	  1.5 s	      1.7 s	      Recommendation in German driving schools: 1.8 s; realistic values vary between 2 s and 0.8 s and even below.
Minimum gap s0	  2.0 m	      2.0 m	      Kept at complete standstill, also in queues that are caused by red traffic lights.
Acceleration a	  0.3 m/s2	  0.3 m/s2	  Very low values to enhance the formation of stop-and go traffic. Realistic values are 1-2 m/s2
Deceleration b	  3.0 m/s2	  2.0 m/s2	  Very high values to enhance the formation of stop-and go traffic. Realistic values are 1-2 m/s2
 */
object VehicleStats {
  object Commuter {
    val minimumGap: Length = 2 meters
    val acceleration: Acceleration = 2.meters.per((1 seconds).squared)
    val deceleration: Acceleration = 3.0.meters.per((1 seconds).squared)
    val dimensions: (Double, Double, Double, LengthUnit) = (4, 2, 0, Meters)
    val weight = Kilograms(800)
  }

  object Truck {
    val minimumGap: Length = 2 meters
    val acceleration: Acceleration = 1.meters.per((1 seconds).squared)
    val deceleration: Acceleration = 2.0.meters.per((1 seconds).squared)
    val dimensions: (Double, Double, Double, LengthUnit) = (12, 2, 0, Meters)
    val weight = Kilograms(4000)
  }
}
