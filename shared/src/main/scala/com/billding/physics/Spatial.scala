package com.billding.physics

import breeze.linalg.DenseVector
import com.billding.traffic.{PilotedVehicle, PilotedVehicleImpl}
import shared.Orientation
import squants.motion._
import squants.space.{LengthUnit, Meters}
import squants.{Length, QuantityVector, Time, UnitOfMeasure, Velocity}

trait Spatial {
  val numberOfDimensions = 3
  val r: QuantityVector[Distance]
  val v: QuantityVector[Velocity]
  val dimensions: QuantityVector[Distance]
  def move(orientation: Orientation, distance: Distance): Spatial
  def relativeVelocity(obstacle: Spatial): QuantityVector[Velocity]
  def relativeVelocityMag(obstacle: Spatial): Velocity
  def vectorTo(obstacle: Spatial): QuantityVector[Distance]

  def vectorToMag(vectorTo: QuantityVector[Distance]): Distance
  def distanceTo(obstacle: Spatial): Distance

  val x: Distance = r.coordinates.head
  val y: Distance = r.coordinates.tail.head
}

case class SpatialImpl (
                         r: QuantityVector[Distance],
                         v: QuantityVector[Velocity],
                         dimensions: QuantityVector[Distance]
) extends Spatial {
  val allAspects: List[QuantityVector[_]] = List(r, v, dimensions)
  for ( aspect <- allAspects ) {
    assert(aspect.coordinates.length == numberOfDimensions)
  }

  override def relativeVelocity(obstacle: Spatial): QuantityVector[Velocity] =
    (this.v - obstacle.v)

  override def relativeVelocityMag(obstacle: Spatial): Velocity = {
    (relativeVelocity _) andThen (_.magnitude) apply obstacle
  }

  override def vectorTo(obstacle: Spatial): QuantityVector[Distance] =
    (obstacle.r - this.r)

  override def vectorToMag(vectorTo: QuantityVector[Distance]): Distance =
    vectorTo.magnitude

  override def distanceTo(obstacle: Spatial): Distance =
    (vectorTo _) andThen (_.magnitude) apply obstacle

  def move(orientation: Orientation, distance: Distance) = {
    val displacement = orientation.vec.map{x:Double => distance * x}
    this.copy(r = r + displacement)
  }
}

object Spatial {
  val ZERO_VELOCITY: (Double, Double, Double, VelocityUnit) = (0, 0, 0, MetersPerSecond)
  val ZERO_VELOCITY_VECTOR: QuantityVector[Velocity] = convertToSVector(ZERO_VELOCITY)

  val ZERO_DIMENSIONS: (Double, Double, Double, LengthUnit) = (0, 2, 0, Meters)
  val ZERO_DIMENSIONS_VECTOR: QuantityVector[Length] = convertToSVector(ZERO_DIMENSIONS)

  def convertToSVector[T <: squants.Quantity[T]](
    input: (Double, Double, Double, UnitOfMeasure[T])
  ): QuantityVector[T] = {
    val (x, y, z, measurementUnit) = input
    QuantityVector[T](measurementUnit(x), measurementUnit(y), measurementUnit(z))
  }

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
  def accelerateAlongCurrentDirection(spatial: Spatial, dt: Time, dV: Acceleration, destination: Spatial): SpatialImpl = {
    val unitVec =
      spatial.vectorTo(destination)
        .map{ r: Distance => r.toMeters}
        .normalize

    val accelerationAlongDirectionOfTravel: QuantityVector[Acceleration] =
      unitVec.map{ unitVecComponent => dV * unitVecComponent}

    val changeInVelocity: QuantityVector[Velocity] =
      accelerationAlongDirectionOfTravel.map(_*dt)

    val newV: QuantityVector[Velocity] = spatial.v.plus(changeInVelocity)
    val newVNoReverse: QuantityVector[Velocity] =
      if (newV.normalize.dotProduct(unitVec).value == -1)
        ZERO_VELOCITY_VECTOR
      else
        newV

    val changeInPositionViaVelocity: QuantityVector[Length] =
      if (spatial.v.normalize.dotProduct(unitVec).value == -1 )
        ZERO_DIMENSIONS_VECTOR
      else
        spatial.v.map{ v: Velocity => v * dt }

    val changeInPositionViaAcceleration: QuantityVector[Distance] =
      accelerationAlongDirectionOfTravel.map{ p: Acceleration => .5 * p * dt.squared}

    val newP = spatial.r + changeInPositionViaVelocity // + changeInPositionViaAcceleration
    SpatialImpl(newP, newVNoReverse, spatial.dimensions)
  }

  def apply(
    pIn: (Double, Double, Double, DistanceUnit),
    vIn: (Double, Double, Double, VelocityUnit),
    dIn:((Double, Double, Double, LengthUnit))
  ): SpatialImpl =
    SpatialImpl(
      convertToSVector(pIn),
      convertToSVector(vIn),
      convertToSVector(dIn)
    )

  def apply(
    pIn: (Double, Double, Double, DistanceUnit),
    vIn: (Double, Double, Double, VelocityUnit)
  ): SpatialImpl = {
    apply(pIn, vIn, ZERO_DIMENSIONS)
  }

  def apply(
    pIn: (Double, Double, Double, DistanceUnit)
  ): SpatialImpl = {
    apply(pIn, ZERO_VELOCITY, ZERO_DIMENSIONS)
  }

  val BLANK = Spatial.apply((0, 0, 0, Meters))

  def withVecs(
    p: QuantityVector[Distance],
    v: QuantityVector[Velocity] = Spatial.ZERO_VELOCITY_VECTOR,
    d: QuantityVector[Length] = Spatial.ZERO_DIMENSIONS_VECTOR
  ): SpatialImpl =
    SpatialImpl(p, v, d)

}

/*
  TODO: This class will enable spatial behavior with a class.
  But maybe that's not needed... I dunno. It's late.
 */
trait SpatialFor[A] {
  def makeSpatial(a: A): Spatial
}

object SpatialForDefaults {
  /*
    def mostUsedWords[T : SpatialFor](t: T): List[(String, Int)] =
    LiteraryAnalyzer.mostUsedWords(makeSpatial(t))
   */
  def disect[T : SpatialFor](t: T): Spatial = implicitly[SpatialFor[T]].makeSpatial(t)

  implicit val spatialForPilotedVehicle = new SpatialFor[PilotedVehicle] {
    def makeSpatial(a: PilotedVehicle): Spatial = {
      a match {
        case vehicle: PilotedVehicleImpl => vehicle.spatial
      }
    }
  }

}

