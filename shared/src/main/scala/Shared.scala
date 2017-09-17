package shared

import breeze.linalg.DenseVector
import squants.motion._
import squants.space.{Length, LengthUnit, Meters}
import squants.{DoubleVector, Length, QuantityVector, SVector, Time, UnitOfMeasure, Velocity}

trait Api {
  def uuid(): String = java.util.UUID.randomUUID.toString
}


sealed trait Orientation {
  val vec: DoubleVector
}
object Orientation {

  case object North extends Orientation {
    val vec = DoubleVector(0.0, -1.0, 0.0)
  }

  case object South extends Orientation {
    val vec = DoubleVector(0.0, 1.0, 0.0)
  }

  case object East extends Orientation {
    val vec = DoubleVector(1.0, 0.0, 0.0)
  }

  case object West extends Orientation {
    val vec = DoubleVector(-1.0, 0.0, 0.0)
  }

}

trait SharedSpatial {
  val numberOfDimensions = 3
  val r: QuantityVector[Distance]
  val v: QuantityVector[Velocity]
  val dimensions: QuantityVector[Distance]
  def relativeVelocity(obstacle: SharedSpatial): QuantityVector[Velocity] = {
    (this.v - obstacle.v)
  }
  def relativeVelocityMag(obstacle: SharedSpatial): Velocity = {
    val z= (relativeVelocity _) andThen (_.magnitude)
    z.apply(obstacle)
  }
  def vectorTo(obstacle: SharedSpatial): QuantityVector[Distance] = (obstacle.r - this.r)
  def vectorToMag(vectorTo: QuantityVector[Distance]): Distance = vectorTo.magnitude
  def distanceTo(obstacle: SharedSpatial): Distance = {
    val z= (vectorTo _) andThen (_.magnitude)
    z.apply(obstacle)
  }
  def move(orientation: Orientation, distance: Distance): SharedSpatial
}
