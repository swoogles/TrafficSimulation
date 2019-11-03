package com.billding.physics

import com.billding.Orientation
import squants.{Acceleration, DoubleVector, Length, QuantityVector, Time, UnitOfMeasure, Velocity, motion}
import squants.motion.{Distance, DistanceUnit, MetersPerSecond, MetersPerSecondSquared, VelocityUnit}
import squants.space.{LengthUnit, Meters}

import scala.language.postfixOps

case class Spatial(
    r: QuantityVector[Distance],
    v: QuantityVector[Velocity],
    dimensions: QuantityVector[Distance]
) {
  val numberOfDimensions = 3
  val x: Distance = r.coordinates.head
  val y: Distance = r.coordinates.tail.head

  val allAspects: List[QuantityVector[_]] = List(r, v, dimensions)
  for (aspect <- allAspects) {
    assert(aspect.coordinates.length == numberOfDimensions)
  }

  def relativeVelocity(obstacle: Spatial): QuantityVector[Velocity] =
    this.v - obstacle.v

  def relativeVelocityMag(obstacle: Spatial): Velocity = {
    relativeVelocity _ andThen (_.magnitude) apply obstacle
  }

  def vectorTo(obstacle: Spatial): QuantityVector[Distance] =
    obstacle.r - this.r

  def vectorToMag(vectorTo: QuantityVector[Distance]): Distance =
    vectorTo.magnitude

  def distanceTo(obstacle: Spatial): Distance =
    vectorTo _ andThen (_.magnitude) apply obstacle

  def move(orientation: Orientation, distance: Distance) = {
    val displacement = orientation.vec.map { x: Double =>
      distance * x
    }
    this.copy(r = r + displacement)
  }

  def updateVelocity(newV: QuantityVector[Velocity]): Spatial =
    this.copy(v = newV)
}

object Spatial {
  val ZERO_VELOCITY: (Double, Double, Double, VelocityUnit) =
    (0, 0, 0, MetersPerSecond)
  val ZERO_VELOCITY_VECTOR: QuantityVector[Velocity] = convertToSVector(
    ZERO_VELOCITY)

  val ZERO_DIMENSIONS: (Double, Double, Double, LengthUnit) = (0, 2, 0, Meters)
  val ZERO_DIMENSIONS_VECTOR: QuantityVector[Length] = convertToSVector(
    ZERO_DIMENSIONS)

  def convertToSVector[T <: squants.Quantity[T]](
      input: (Double, Double, Double, UnitOfMeasure[T])
  ): QuantityVector[T] = {
    val (x, y, z, measurementUnit) = input
    QuantityVector[T](measurementUnit(x),
                      measurementUnit(y),
                      measurementUnit(z))
  }

  def vectorsAreInOppositeDirections(vec1: QuantityVector[Velocity], vec2: DoubleVector): Boolean = {
    val difference = (vec1.normalize.to(MetersPerSecond).normalize + vec2.normalize).magnitude
    difference < 0.1 && difference > -0.1
    //    println("dotProduct: " + vec1.dotProduct(vec2).value)
    //    vec1.dotProduct(vec2).value <= -0.9 && vec1.dotProduct(vec2).value > -1.1
  }


  /*
  A bunch of future posibilities:
  def vecBetween(observer: Spatial, target: Spatial): DenseVector[Distance] = ???
  def distanceBetween(observer: Spatial, target: Spatial): Distance = ???
  def relativeVelocity(observer: Spatial, target: Spatial): QuantityVector[Velocity] = ???
  def isTouching(observer: Spatial, target: Spatial): Boolean = ???
  def onCollisionCourse(observer: Spatial, target: Spatial): Boolean = {
     Consider approach described here:
     https://math.stackexchange.com/questions/1438002/determine-if-objects-are-moving-towards-each-other?newreg=900c99882b6b4753a3a89d6109f6b83c
      val relativeV = target.v - observer.v
      breeze.linalg.normalize(target.p + relativeV) == normalize(observer.p - relativeV)
    // Alternate solution: Determe in observer.p lines on the line defined by target.p + target.v
    ???
  }
   */
  def unitVecTo(spatial: Spatial, destination: Spatial): DoubleVector =
    spatial
      .vectorTo(destination)
      .map { r: Distance =>
        r.toMeters
      }
      .normalize

  def accelerationAlongDirectionOfTravelWithoutPreventingBackwardsTravel(spatial: Spatial, dV: Acceleration, destination: Spatial): QuantityVector[Acceleration] = {
    unitVecTo(spatial, destination).map { unitVecComponent =>
      dV * unitVecComponent
    }
  }

  def newVelocityAfterAcceleration(spatial: Spatial, accelerationAlongDirectionOfTravel: QuantityVector[Acceleration], dt: Time): QuantityVector[Velocity] = {
    val changeInVelocity: QuantityVector[Velocity] =
      accelerationAlongDirectionOfTravel.map(_ * dt)

    spatial.v.plus(changeInVelocity)

  }

  /*
  new speed:	v(t+Δt) = v(t) + (dv/dt) Δt,
  new position:   	x(t+Δt) = x(t) + v(t)Δt + 1/2 (dv/dt) (Δt)2,
  new gap:	s(t+Δt) = xl(t+Δt) − x(t+Δt)− Ll.
   */
  def accelerateAlongCurrentDirection(spatial: Spatial,
                                      dt: Time,
                                      dV: Acceleration,
                                      destination: Spatial): Spatial = {
    //    if (spatial.v.magnitude == MetersPerSecond(0)) throw new IllegalArgumentException("spatial needs to be moving")

    val accelerationAlongDirectionOfTravel: QuantityVector[Acceleration] =
      accelerationAlongDirectionOfTravelWithoutPreventingBackwardsTravel(spatial, dV, destination)

    val newV: QuantityVector[Velocity] = newVelocityAfterAcceleration(spatial, accelerationAlongDirectionOfTravel, dt)

    if (vectorsAreInOppositeDirections(newV.normalize, unitVecTo(spatial, destination)))
      spatial.copy(v = ZERO_VELOCITY_VECTOR) // No reversing allowed. Return a stopped car.
    else {
      val changeInPositionViaVelocity: QuantityVector[Length] =
        newV
          .map { v: Velocity => v * dt }

      val changeInPositionViaAcceleration: QuantityVector[Distance] =
        accelerationAlongDirectionOfTravel.map { p: Acceleration =>
          .5 * p * dt.squared
        }

      val newP = spatial.r + changeInPositionViaVelocity + changeInPositionViaAcceleration
      spatial.copy(r = newP, v = newV)
      }

  }

  def apply(
      pIn: (Double, Double, Double, DistanceUnit),
      vIn: (Double, Double, Double, VelocityUnit),
      dIn: (Double, Double, Double, LengthUnit)
  ): Spatial =
    Spatial(
      convertToSVector(pIn),
      convertToSVector(vIn),
      convertToSVector(dIn)
    )

  def apply(
      pIn: (Double, Double, Double, DistanceUnit),
      vIn: (Double, Double, Double, VelocityUnit)
  ): Spatial = {
    apply(pIn, vIn, ZERO_DIMENSIONS)
  }

  def apply(
      pIn: (Double, Double, Double, DistanceUnit)
  ): Spatial = {
    apply(pIn, ZERO_VELOCITY, ZERO_DIMENSIONS)
  }

  val BLANK = Spatial.apply((0, 0, 0, Meters))

  def withVecs(
      p: QuantityVector[Distance],
      v: QuantityVector[Velocity] = Spatial.ZERO_VELOCITY_VECTOR,
      d: QuantityVector[Length] = Spatial.ZERO_DIMENSIONS_VECTOR
  ): Spatial =
    Spatial(p, v, d)

}
