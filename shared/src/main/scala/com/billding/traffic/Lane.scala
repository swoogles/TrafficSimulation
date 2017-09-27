package com.billding.traffic

import cats.data.NonEmptyList
import com.billding.physics.{Spatial, SpatialImpl}
import com.billding.{traffic, _}
import squants.motion._
import squants.space.Meters
import squants.time.Seconds
import squants.{QuantityVector, Time, Velocity}

trait Segment {
  /*
    Figure out where this belongs in the Road/Lane/Scene interplay.
    I will need to consider arc lengths.
   */
}
trait Lane {
  val vehicles: List[PilotedVehicleImpl]
  val vehicleSource: VehicleSource
  val beginning: SpatialImpl
  val end: SpatialImpl
  val vehicleAtInfinityForward: PilotedVehicleImpl
  val infinitySpatial: SpatialImpl
  def vehicleCanBePlaced(pilotedVehicle: PilotedVehicleImpl, fractionCompleted: Double): Boolean
}

case class LaneImpl(vehicles: List[PilotedVehicleImpl], vehicleSource: VehicleSourceImpl, beginning: SpatialImpl, end: SpatialImpl) extends Lane {

  val infinityPointForward: QuantityVector[Distance] = beginning.vectorTo(end).normalize.map{ x: Distance => x * 10000}
  val infinityPointBackwards: QuantityVector[Distance] = beginning.vectorTo(end).normalize.map{ x: Distance => x * -10000}
  val vehicleAtInfinityForward: PilotedVehicleImpl = {
    val spatial =  Spatial.withVecs(infinityPointForward, Spatial.ZERO_VELOCITY_VECTOR, Spatial.ZERO_DIMENSIONS_VECTOR )
    PilotedVehicle.commuter(spatial, new IntelligentDriverModelImpl, spatial)
  }
  val vehicleAtInfinityBackwards: PilotedVehicleImpl = {
    val spatial =  Spatial.withVecs(infinityPointBackwards, Spatial.ZERO_VELOCITY_VECTOR, Spatial.ZERO_DIMENSIONS_VECTOR )
    PilotedVehicle.commuter(spatial, new IntelligentDriverModelImpl, spatial)
  }
  override val infinitySpatial: SpatialImpl = vehicleAtInfinityForward.spatial

  /*
    Look at reusing this for finding leading/following cars in neighboring lane.
   */
  def addDisruptiveVehicle(pilotedVehicle: PilotedVehicleImpl): LaneImpl = {
    val disruptionPoint: QuantityVector[Distance] = end.vectorTo(beginning).times(.25)

    val betterVec: QuantityVector[Distance] =
      disruptionPoint.plus(end.r)

    val isPastDisruption =
      (v: PilotedVehicle) =>
        v.spatial.vectorTo(end).magnitude < disruptionPoint.magnitude

    val newDriver = pilotedVehicle.driver.copy(spatial =
      pilotedVehicle.driver.spatial.copy(r = betterVec)
    )
    val newVehicle =
      pilotedVehicle.vehicle.copy(spatial =
        pilotedVehicle.vehicle.spatial.copy(r=betterVec)
      )

    val newPilotedVehicle = pilotedVehicle.copy(driver = newDriver, vehicle = newVehicle)
    val (pastVehicles, approachingVehicles) = this.vehicles.partition(isPastDisruption)
    val vehicleList: List[PilotedVehicleImpl] = (pastVehicles :+ newPilotedVehicle.copy(destination = end) ) ::: approachingVehicles
    this.copy(vehicles =
      vehicleList
    )

  }

  /**
    * TODO: Also check lane start/end points OR that fraction is between 0 and 1. I think Option B.
    */
  def vehicleCanBePlaced(pilotedVehicle: PilotedVehicleImpl, fractionCompleted: Double): Boolean = {
    val disruptionPoint: QuantityVector[Distance] = beginning.vectorTo(end).times(fractionCompleted)
//    pprint.pprintln("disruptionPoint:" )
//    pprint.pprintln(disruptionPoint)

    val vehiclePlacementInLane: SpatialImpl = pilotedVehicle.spatial.copy(r=disruptionPoint)
    // TODO Use actual vehicle sizes instead of set meters distance
    val interferes: Boolean =
      this.vehicles.exists(
        curVehicle => curVehicle.spatial.distanceTo(vehiclePlacementInLane) < Meters(3)
      )

    ! interferes
  }

  def disruptVehicles(): LaneImpl = {
    val (pastVehicles, approachingVehicles) = this.vehicles.splitAt(this.vehicles.length-5)

    val ( disruptedVehicle :: restOfApproachingVehicles) = approachingVehicles
    val newV= disruptedVehicle.vehicle.spatial.copy(v=Spatial.ZERO_VELOCITY_VECTOR)
    val newlyDisruptedVehicle = disruptedVehicle.copy(vehicle = disruptedVehicle.vehicle.copy(spatial = newV))
    val vehicleList: List[PilotedVehicleImpl] = (pastVehicles :+ newlyDisruptedVehicle ) ::: restOfApproachingVehicles
    this.copy(vehicles = vehicleList)
  }

}

object Lane extends LaneFunctions {

  def apply(sourceTiming: Time, beginning: SpatialImpl, end: SpatialImpl, speed: Velocity, vehicles: List[PilotedVehicleImpl] = Nil): LaneImpl = {
    // TODO Get this speed updated via some nifty RX variables in the GUI
    val directionForSource: QuantityVector[Distance] = beginning.vectorTo(end)
    val startingV: QuantityVector[Velocity] = directionForSource.normalize.map{ x: Distance => x.value * speed}

    val velocitySpatial = SpatialImpl(beginning.r, startingV, beginning.dimensions)
    val source = VehicleSourceImpl(sourceTiming, beginning, velocitySpatial)
    LaneImpl(vehicles, source, beginning, end)
  }

  private def responsesInOneLane(vehicles: NonEmptyList[PilotedVehicle], speedLimit: Velocity): NonEmptyList[Acceleration] = {
    val target = vehicles.head
    vehicles.tail match {
      case Nil => throw new RuntimeException("shit!!")
      case follower :: Nil => NonEmptyList(follower.reactTo(target, speedLimit), Nil) // :: responsesInOneLane(follower :: rest)
      case follower :: rest => {
        follower.reactTo(target, speedLimit) :: responsesInOneLane(NonEmptyList(follower, rest), speedLimit)
      }
    }
  }

  val MAX_VEHICLES_PER_LANE = 60

  def update(lane: LaneImpl, speedLimit: Velocity, t: Time, dt: Time): LaneImpl = {
    val newVehicleOption: Option[PilotedVehicleImpl] = lane.vehicleSource.produceVehicle(t, dt, lane.infinitySpatial)
    val newVehicleList: List[PilotedVehicleImpl] =
      newVehicleOption match {
          // This could be tweaked so it's always reducing to MAX_VEHICLES_PER_LANE, rather than only dropping 1
        case Some(newVehicle) if (lane.vehicles.size > MAX_VEHICLES_PER_LANE) => lane.vehicles.drop(lane.vehicles.size - MAX_VEHICLES_PER_LANE) :+ newVehicle
        case Some(newVehicle) => lane.vehicles :+ newVehicle
        case None => lane.vehicles
      }

    val laneWithNewVehicle = lane.copy(vehicles = newVehicleList)
    val dMomentumList = responsesInOneLanePrep(laneWithNewVehicle, speedLimit)
    val newVehicles: List[PilotedVehicleImpl] =
      newVehicleList.zip(dMomentumList) map {
        case (vehicle, dMomentum) => vehicle.accelerateAlongCurrentDirection(dt, dMomentum)
      }

    laneWithNewVehicle.copy(vehicles = newVehicles)
  }

  def responsesInOneLanePrep(lane: Lane, speedLimit: Velocity): List[Acceleration] = {
    lane.vehicles match {
      case Nil => Nil
      case head :: _ => responsesInOneLane(NonEmptyList(lane.vehicleAtInfinityForward, lane.vehicles), speedLimit).toList
    }
  }

  /*
    TODO: Make this safe
   */
  def getVehicleBeforeAndAfter(pilotedVehicleImpl: PilotedVehicleImpl, lane: LaneImpl): (PilotedVehicleImpl, PilotedVehicleImpl) = {
    val index: Int = lane.vehicles.indexWhere(_.equals(pilotedVehicleImpl))
    val before = lane.vehicles(index - 1)
    val after = lane.vehicles(index + 1)
    (before, after)
  }

  def attemptVehicleBeforeAndAfter(pilotedVehicleImpl: PilotedVehicleImpl, lane: LaneImpl): Option[(PilotedVehicleImpl, PilotedVehicleImpl)] = {
    for (
      before <- getVehicleBefore(pilotedVehicleImpl, lane);
      after <- getVehicleAfter(pilotedVehicleImpl, lane)
    ) yield {
      (before, after)
    }
  }


  def getVehicleIndex(pilotedVehicleImpl: PilotedVehicleImpl, lane: LaneImpl): Option[Integer] = {
    val index: Int = lane.vehicles.indexWhere(_.equals(pilotedVehicleImpl))
    if (index == -1) {
      Option.empty
    } else {
      Some(index)
    }
  }

  def getVehicleBefore(pilotedVehicleImpl: PilotedVehicleImpl, lane: LaneImpl): Option[PilotedVehicleImpl] = {
    for (
      index <- getVehicleIndex(pilotedVehicleImpl, lane)
    ) yield {
      lane.vehicles.lift(index - 1).getOrElse(lane.vehicleAtInfinityForward)
    }
  }
  def getVehicleAfter(pilotedVehicleImpl: PilotedVehicleImpl, lane: LaneImpl): Option[PilotedVehicleImpl] = {
    for (
      index <- getVehicleIndex(pilotedVehicleImpl, lane)
    ) yield {
      lane.vehicles.lift(index + 1).getOrElse(lane.vehicleAtInfinityBackwards)
    }
  }

  /**
    * TODO : getFractionBetweenEndpoints(pilotedVehicleImpl: PilotedVehicleImpl, lane: LaneImpl): Double
    *         getFractionalPoint(lane: LaneImpl): Spatial
    */
  def fractionCompleted(pilotedVehicleImpl: PilotedVehicleImpl, lane: LaneImpl): Double = {
//    val laneDistanceVector: QuantityVector[Distance] = lane.beginning.vectorTo(lane.end)
    val laneDistance = lane.beginning.distanceTo(lane.end)

//    println("laneDistance: " + laneDistance)
//    pprint.pprintln("pilotedVehicle spatial: " + pilotedVehicleImpl.spatial)
    val vehicleDistanceVector: QuantityVector[Distance] = pilotedVehicleImpl.spatial.vectorTo(lane.end)
//    pprint.pprintln("vehicleDistanceVector: " + vehicleDistanceVector)
    vehicleDistanceVector.coordinates.foreach(println)
    val vehicleDistance: Distance = pilotedVehicleImpl.spatial.distanceTo(lane.end)
//    println("vehicleDistance: " + vehicleDistance)
    (1.0 - vehicleDistance / laneDistance)
  }

  def moveToNeighboringLane(pilotedVehicleImpl: PilotedVehicleImpl, lane: LaneImpl, desiredLane: LaneImpl): LaneImpl = {
    val fractionComplete = fractionCompleted(pilotedVehicleImpl, lane)
    val disruptionPoint: QuantityVector[Distance] = desiredLane.end.vectorTo(desiredLane.beginning).times(fractionComplete)
    fractionComplete
    ???
  }
}

