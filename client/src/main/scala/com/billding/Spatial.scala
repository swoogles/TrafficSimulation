package com.billding

import breeze.linalg.DenseVector
import breeze.linalg.normalize
import squants.{DoubleVector, Length, QuantityVector, SVector, Time, Velocity, motion}
import squants.motion._
import squants.space.LengthConversions._
import squants.space.{LengthUnit, Meters}
import squants.time.TimeConversions._

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
case class SpatialImpl (
  p: QuantityVector[Distance],
  v: QuantityVector[Velocity] = SVector(0.meters.per(1 seconds), 0.meters.per(1 seconds), 0.meters.per(1 seconds)),
  dimensions: QuantityVector[Distance] = SVector(0.meters, 0.meters, 0.meters)
) extends Spatial {
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

  /*
  new speed:	v(t+Δt) = v(t) + (dv/dt) Δt,
  new position:   	x(t+Δt) = x(t) + v(t)Δt + 1/2 (dv/dt) (Δt)2,
  new gap:	s(t+Δt) = xl(t+Δt) − x(t+Δt)− Ll.
  */
  def update(spatial: Spatial, dt: Time, dP: Acceleration): Spatial = {
    val vUnit = spatial.v.valueUnit
    val accelerationOppositeOfTravelDirection: QuantityVector[Acceleration] = (spatial.v.normalize.to(vUnit)).map{ a: Double => dP * a}
    val newV = spatial.v.map{ x: Velocity =>x + dP * dt}
    val dPwithNewV = newV.map{ v: Velocity => v * dt }
    val betterMomentumFactor: QuantityVector[Length] = accelerationOppositeOfTravelDirection.map{ p: Acceleration => .5 * p * dt.squared}
    val newP = spatial.p + dPwithNewV + betterMomentumFactor
    new SpatialImpl(newP, newV, spatial.dimensions)
  }

  def apply(
             pIn: (Double, Double, Double, LengthUnit),
             vIn: (Double, Double, Double, VelocityUnit) = (0, 0, 0, MetersPerSecond)
           ): Spatial = {
    val (pX, pY, pZ, pUnit) = pIn
    val (vX, vY, vZ, vUnit) = vIn

    val p: QuantityVector[Distance] = SVector(pX, pY, pZ) .map(pUnit(_))
    val v: QuantityVector[Velocity] = SVector(vX, vY, vZ).map(vUnit(_))
    new SpatialImpl(p, v)
  }

}



