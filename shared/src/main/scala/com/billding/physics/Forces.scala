package com.billding.physics

import squants.{QuantityVector, SVector}

trait Forces {
  import squants.Length
  import squants.space.Kilometers
  import squants.space.LengthConversions._
  import squants.time.TimeConversions._

  val pos: QuantityVector[Length] =
    SVector(Kilometers(0.0), Kilometers(0.0), Kilometers(0.0))

  val vDt = 1 seconds
  val v = SVector(
    10.meters.per(vDt),
    5.meters.per(vDt),
    0.meters.per(vDt))

  val pDt = 1 minutes
  val makeMomentumDimension = (x: Double) =>x.meters.per(pDt.squared)

  val wind = SVector(
    -1.meters.per(pDt.squared),
    0.meters.per(pDt.squared),
    0.meters.per(pDt.squared))

  val gasAcceleration =
    SVector( 5, 0, 0 )
      .map(makeMomentumDimension)

  val gasAccelerationB =
    SVector( 5, 0, 0)
      .map((x: Double) =>x.meters.per(pDt.squared)  )


  val dt = 1.hours
  pos + v.map(_ * 1.hours) + ( wind.map( _ * 1.hours.squared))
  pos + v.map(_ * dt) + ( wind.map( _ * dt.squared))
}
