package com.billding

import breeze.linalg.DenseVector
import breeze.linalg.normalize
import squants.{DoubleVector, QuantityVector, SVector, Velocity, motion}
import squants.motion.{Distance, MetersPerSecondSquared}

class PositiveVector(values: DenseVector[Float]) {
  require(values forall (_ >= 0), "List contains negative numbers")
}

trait Spatial {
  // Consider Breeze version
  //  val p: DenseVector[Distance] // Must enforce length 3....
  val p: QuantityVector[Distance] //= SVector(Kilometers(-1.2), Kilometers(4.3), Kilometers(2.3))
  val v: QuantityVector[Velocity]
  val dimensions: QuantityVector[Distance]
}
object Spatial {
  def vecBetween(observer: Spatial, target: Spatial): DenseVector[Distance] = ???
  def distanceBetween(observer: Spatial, target: Spatial): Distance = ???
  def relativeVelocity(observer: Spatial, target: Spatial) = ???
  def isTouching(observer: Spatial, target: Spatial): Boolean = ???
  def onCollisionCourse(observer: Spatial, target: Spatial): Boolean = {
    /*
     Consider approach described here:
     https://math.stackexchange.com/questions/1438002/determine-if-objects-are-moving-towards-each-other?newreg=900c99882b6b4753a3a89d6109f6b83c
      val relativeV = target.v - observer.v
      breeze.linalg.normalize(target.p + relativeV) == normalize(observer.p - relativeV)
     */
    // Alternate solution: Determe in observer.p lines on the line defined by target.p + target.v
    ???
  }
}


trait Forces {
  import squants.energy.Energy
  import squants.energy.EnergyConversions._
  import squants.mass.{Density, Mass}
  import squants.mass.MassConversions._
  import squants.motion.{Acceleration, Velocity, VolumeFlow}
  import squants.motion.AccelerationConversions._
  import squants.space.LengthConversions._
  import squants.space.VolumeConversions._
  import squants.time.Time
  import squants.time.TimeConversions._
  import squants.Length
  import squants.space.Kilometers

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
