package com.billding

import breeze.linalg.DenseVector
import breeze.linalg.normalize
import squants.{DoubleVector, QuantityVector, SVector, Velocity, motion}
import squants.motion._
import squants.space.LengthConversions._
import squants.space.LengthUnit
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

  def apply(
             pIn: (Double, Double, Double),
             pUnit: LengthUnit,
             vIn: (Double, Double, Double) = (0, 0, 0),
              vUnits: VelocityUnit

           ): Spatial = {

    val p: QuantityVector[Distance] = SVector(pIn._1, pIn._2, pIn._3 ) .map(pUnit(_))
    val v: QuantityVector[Velocity] = SVector(vIn._1, vIn._2, vIn._3 ).map(vUnits(_))
    new SpatialImpl(p, v)
  }

}


