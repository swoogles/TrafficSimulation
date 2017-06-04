package com.billding.physics

import breeze.linalg.DenseVector
import com.billding.traffic.{PilotedVehicle, PilotedVehicleImpl}
import squants.motion._
import squants.space.{LengthUnit, Meters}
import squants.{Length, QuantityVector, SVector, Time, Velocity}

trait Spatial {
  val numberOfDimensions = 3
  val r: QuantityVector[Distance] //= SVector(Kilometers(-1.2), Kilometers(4.3), Kilometers(2.3))
  val v: QuantityVector[Velocity]
  val dimensions: QuantityVector[Distance]
  def relativeVelocity(obstacle: Spatial): QuantityVector[Velocity] = {
    (this.v - obstacle.v)
  }
  def relativeVelocityMag(obstacle: Spatial): Velocity = {
    val z= (relativeVelocity _) andThen (_.magnitude)
    z.apply(obstacle)
  }
  def vectorTo(obstacle: Spatial): QuantityVector[Distance] = (obstacle.r - this.r)
  def vectorToMag(vectorTo: QuantityVector[Distance]): Distance = vectorTo.magnitude
  def distanceTo(obstacle: Spatial): Distance = {
    val z= (vectorTo _) andThen (_.magnitude)
    z.apply(obstacle)
  }
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
}

object Spatial {
  val ZERO_VELOCITY: (Double, Double, Double, VelocityUnit) = (0, 0, 0, MetersPerSecond)

  val ZERO_VELOCITY_VECTOR: QuantityVector[Velocity] = {
    val (vX, vY, vZ, vUnit)= (0, 0, 0, MetersPerSecond)
    SVector(vX, vY, vZ).map(vUnit(_))
  }
  val ZERO_DIMENSIONS: (Double, Double, Double, LengthUnit) = (0, 2, 0, Meters)
  val ZERO_DIMENSIONS_VECTOR: QuantityVector[Length] = {
    val (dX, dY, dZ, dUnit)=ZERO_DIMENSIONS
    SVector(dX, dY, dZ).map(dUnit(_))
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
  TODO: Restrict this so nobody tries to reverse.
  */
  def accelerateAlongCurrentDirection(spatial: Spatial, dt: Time, dP: Acceleration): Spatial = {
    // TODO I think I've lost the unit safety with this current approach
    val vUnit = spatial.v.valueUnit
    val vUnitVec: QuantityVector[Velocity] = spatial.v.normalize
    val accelerationOppositeOfTravelDirection: QuantityVector[Acceleration] =
      (vUnitVec.to(vUnit)).map{ a: Double => dP * a}
    val accelerationWithTime = accelerationOppositeOfTravelDirection.map{accelerationComponent: Acceleration =>accelerationComponent*dt}
    val newV = spatial.v.plus(accelerationWithTime)
    var inflected = false
    val zerodOutVComponents: Seq[Velocity] = for ((oldVComponent, newVComponent) <- spatial.v.coordinates.zip(newV.coordinates)) yield {
      if ( (oldVComponent.value <= 0 && newVComponent.value > 0)
        || (oldVComponent.value >= 0 && newVComponent.value < 0)) {
        println("inflection point!!!!!")
        inflected = true
        MetersPerSecond(0)
      } else {
        newVComponent
      }
    }
    val zerodOutV =  QuantityVector(zerodOutVComponents:_*)
    val dPwithNewV = zerodOutV.map{ v: Velocity => v * dt }
    val betterMomentumFactor: QuantityVector[Distance] = accelerationOppositeOfTravelDirection.map{ p: Acceleration => .5 * p * dt.squared}
    val newP = spatial.r + dPwithNewV + betterMomentumFactor
    if (inflected) println("zerodOutV: " + zerodOutV)
    SpatialImpl(newP, zerodOutV, spatial.dimensions)
  }

  def apply(
             pIn: (Double, Double, Double, DistanceUnit),
             vIn: (Double, Double, Double, VelocityUnit),
             dIn:((Double, Double, Double, LengthUnit))
           ): Spatial = {
    val (pX, pY, pZ, pUnit) = pIn
    val (vX, vY, vZ, vUnit) = vIn
    val (dX, dY, dZ, dUnit) = dIn


    val p: QuantityVector[Distance] = SVector(pX, pY, pZ) .map(pUnit(_))
    val v: QuantityVector[Velocity] = SVector(vX, vY, vZ).map(vUnit(_))
    val d: QuantityVector[Length] = SVector(dX, dY, dZ).map(dUnit(_))
    new SpatialImpl(p, v, d)

  }
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

  def withVecs(
           p: QuantityVector[Distance],
            v: QuantityVector[Velocity],
            d: QuantityVector[Length]
           ): Spatial = {

    new SpatialImpl(p, v, d)
  }

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
