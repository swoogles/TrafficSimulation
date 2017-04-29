package com.billding

import breeze.linalg.DenseVector
import breeze.linalg.normalize

trait Spatial {
  val p = DenseVector.zeros[Float](3)
  val v = DenseVector.zeros[Float](3)
//  def blah = breeze.linalg.cross(pos, v);
}
object Spatial {
  def vecBetween(observer: Spatial, target: Spatial): DenseVector[Float] = ???
  def distanceBetween(observer: Spatial, target: Spatial): Float = ???
  def relativeVelocity(observer: Spatial, target: Spatial) = ???
  def onCollisionCourse(observer: Spatial, target: Spatial): Boolean = {
    /*
     Consider approach described here:
     https://math.stackexchange.com/questions/1438002/determine-if-objects-are-moving-towards-each-other?newreg=900c99882b6b4753a3a89d6109f6b83c
     */
    // Alternate solution: Determe in observer.p lines on the line defined by target.p + target.v
    val relativeV = target.v - observer.v
    breeze.linalg.normalize(target.p + relativeV) == normalize(observer.p - relativeV)
  }
}


