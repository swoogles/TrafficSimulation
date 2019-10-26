package fr.iscpif.client.previouslySharedCode.physics

import scala.language.postfixOps

import squants.{Acceleration, Length, QuantityVector, SVector}
import squants.space.Kilometers
import squants.space.LengthConversions._
import squants.time.Time
import squants.time.TimeConversions._

trait Forces {
  val pos: QuantityVector[Length] =
    SVector(Kilometers(0.0), Kilometers(0.0), Kilometers(0.0))

  val vDt: Time = 1 seconds
  val v = SVector(10.meters.per(vDt), 5.meters.per(vDt), 0.meters.per(vDt))

  val pDt: Time = 1 minutes
  val makeMomentumDimension: Double => Acceleration = (x: Double) =>
    x.meters.per(pDt.squared)

  val wind = SVector(-1.meters.per(pDt.squared),
                     0.meters.per(pDt.squared),
                     0.meters.per(pDt.squared))

  val gasAcceleration: QuantityVector[Acceleration] =
    SVector(5, 0, 0)
      .map(makeMomentumDimension)

  val gasAccelerationB: QuantityVector[Acceleration] =
    SVector(5, 0, 0)
      .map((x: Double) => x.meters.per(pDt.squared))

  val dt: Time = 1.hours
  pos + v.map(_ * 1.hours) + wind.map(_ * 1.hours.squared)
  pos + v.map(_ * dt) + wind.map(_ * dt.squared)
}
