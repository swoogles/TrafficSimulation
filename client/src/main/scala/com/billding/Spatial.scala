package com.billding

import breeze.linalg.DenseVector
import breeze.linalg.normalize

class PositiveVector(values: DenseVector[Float]) {
  require(values forall (_ >= 0), "List contains negative numbers")
}

import squants.motion.VelocityUnit
import squants.space.LengthUnit

trait Spatial {
  val p = DenseVector.zeros[LengthUnit](3)
  val v = DenseVector.zeros[VelocityUnit](3)
  val dimensions: PositiveVector
}
object Spatial {
  def vecBetween(observer: Spatial, target: Spatial): DenseVector[LengthUnit] = ???
  def distanceBetween(observer: Spatial, target: Spatial): LengthUnit = ???
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


