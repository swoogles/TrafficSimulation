package com.billding.traffic

import cats.data.NonEmptyList
import squants.Time
import squants.motion.Acceleration

import com.billding.physics.Spatial
import squants.motion.Distance
import squants.space.{Length, Meters}
import squants.{QuantityVector, Velocity}

case class Lane(
                 vehicles: List[PilotedVehicle],
                 vehicleSource: VehicleSourceImpl,
                 beginning: Spatial,
                 end: Spatial,
                 speedLimit: Velocity
                   ) {

  val length: Length = beginning.distanceTo(end)

  val infinityPointForward: QuantityVector[Distance] =
    beginning.vectorTo(end).normalize.map(_ * 10000)
  val infinityPointBackwards: QuantityVector[Distance] =
    beginning.vectorTo(end).normalize.map(_ * -10000)

  val vehicleAtInfinityForward: PilotedVehicle = {
    val spatial = Spatial.withVecs(infinityPointForward)
    PilotedVehicle.commuter2(spatial, new IntelligentDriverModelImpl, spatial)
  }
  val vehicleAtInfinityBackwards: PilotedVehicle = {
    val spatial = Spatial.withVecs(infinityPointBackwards)
    PilotedVehicle.commuter2(spatial, new IntelligentDriverModelImpl, spatial)
  }
  val infinitySpatial: Spatial = vehicleAtInfinityForward.spatial

  /*
    Look at reusing this for finding leading/following cars in neighboring lane.
   */
  def addDisruptiveVehicle(pilotedVehicle: PilotedVehicle): Lane = {
    val disruptionPoint: QuantityVector[Distance] =
      end.vectorTo(beginning).times(.25)

    val betterVec: QuantityVector[Distance] =
      disruptionPoint.plus(end.r)

    val isPastDisruption =
      (v: PilotedVehicle) =>
        v.spatial.vectorTo(end).magnitude < disruptionPoint.magnitude

    val newPilotedVehicle = pilotedVehicle.move(betterVec)
    val (pastVehicles, approachingVehicles) =
      this.vehicles.partition(isPastDisruption)
    val vehicleList: List[PilotedVehicle] =
      (pastVehicles :+ newPilotedVehicle.target(end)) ::: approachingVehicles
    this.copy(vehicles = vehicleList)
  }

  /**
    * TODO: Also check lane start/end points OR that fraction is between 0 and 1. I think Option B.
    */
  def vehicleCanBePlaced(pilotedVehicle: PilotedVehicle,
                         fractionCompleted: Double): Boolean = {
    val disruptionPoint: QuantityVector[Distance] =
      beginning.vectorTo(end).times(fractionCompleted)

    val vehicleInLane = pilotedVehicle.move(disruptionPoint)
    // TODO Use actual vehicle sizes instead of set meters distance
    val interferes: Boolean =
      this.vehicles.exists(
        curVehicle => curVehicle.distanceTo(vehicleInLane) < Meters(3)
      )

    !interferes
  }

  def disruptVehicles(): Lane = {
    val (pastVehicles, approachingVehicles) =
      this.vehicles.splitAt(this.vehicles.length - 5)

    val (disruptedVehicle :: restOfApproachingVehicles) = approachingVehicles
    val vehicleList: List[PilotedVehicle] =
      (pastVehicles :+ disruptedVehicle.stop()) ::: restOfApproachingVehicles
    this.copy(vehicles = vehicleList)
  }

}

object Lane {

  val MAX_VEHICLES_PER_LANE = 60

  def apply(
             sourceTiming: Time,
             beginning: Spatial,
             end: Spatial,
             speedLimit: Velocity,
             vehicles: List[PilotedVehicle] = Nil
  ): Lane = {
    // TODO Get this speed updated via some nifty RX variables in the GUI
    val directionForSource: QuantityVector[Distance] = beginning.vectorTo(end)

    val startingV: QuantityVector[Velocity] =
      directionForSource.normalize.map { x: Distance =>
        x.value * speedLimit
      }

    val velocitySpatial =
      Spatial(beginning.r, startingV, beginning.dimensions)
    val source = VehicleSourceImpl(sourceTiming, beginning, velocitySpatial)
    Lane(vehicles, source, beginning, end, speedLimit)
  }

  private def responsesInOneLane(
                                  vehicles: NonEmptyList[PilotedVehicle],
                                  speedLimit: Velocity
  ): NonEmptyList[Acceleration] = {
    val target = vehicles.head
    vehicles.tail match {
      case Nil => throw new RuntimeException("shit!!")
      case follower :: Nil =>
        NonEmptyList(follower.reactTo(target, speedLimit), Nil)
      case follower :: rest => {
        follower.reactTo(target, speedLimit) :: responsesInOneLane(
          NonEmptyList(follower, rest),
          speedLimit)
      }
    }
  }

  def update(lane: Lane, t: Time, dt: Time): Lane = {
    val newVehicleOption: Option[PilotedVehicle] =
      lane.vehicleSource.produceVehicle(t, dt, lane.end)

    val newVehicleList: List[PilotedVehicle] =
      newVehicleOption match {
        // This could be tweaked so it's always reducing to MAX_VEHICLES_PER_LANE, rather than only dropping 1
        case Some(newVehicle) if (lane.vehicles.size > MAX_VEHICLES_PER_LANE) =>
          lane.vehicles.drop(lane.vehicles.size - MAX_VEHICLES_PER_LANE) :+ newVehicle
        case Some(newVehicle) => lane.vehicles :+ newVehicle
        case None             => lane.vehicles
      }

    val laneWithNewVehicle = lane.copy(vehicles = newVehicleList)
    val dMomentumList = responsesInOneLanePrep(laneWithNewVehicle)
    val newVehicles: List[PilotedVehicle] =
      newVehicleList.zip(dMomentumList) map {
        case (vehicle, dMomentum) =>
          vehicle.accelerateAlongCurrentDirection(dt, dMomentum)
      }
    val vehiclesThatHaveNotReachedDestination =
      newVehicles.filter(vehicle => vehicle.distanceTo(vehicle.destination) > Meters(40))

    laneWithNewVehicle.copy(vehicles = vehiclesThatHaveNotReachedDestination)
  }

  def responsesInOneLanePrep(lane: Lane): List[Acceleration] = {
    lane.vehicles match {
      case Nil => Nil
      case _ :: _ =>
        responsesInOneLane(
          NonEmptyList(lane.vehicleAtInfinityForward, lane.vehicles),
          lane.speedLimit).toList
    }
  }

  def attemptVehicleBeforeAndAfter(
                                    pilotedVehicle: PilotedVehicle,
                                    lane: Lane): Option[(PilotedVehicle, PilotedVehicle)] = {
    for (before <- getVehicleBefore(pilotedVehicle, lane);
         after <- getVehicleAfter(pilotedVehicle, lane)) yield {
      (before, after)
    }
  }

  def getVehicleIndex(pilotedVehicle: PilotedVehicle,
                      lane: Lane): Option[Integer] = {
    val index: Int = lane.vehicles.indexWhere(_.equals(pilotedVehicle))
    if (index == -1)
      Option.empty
    else
      Some(index)
  }

  def getVehicleBefore(pilotedVehicle: PilotedVehicle,
                       lane: Lane): Option[PilotedVehicle] = {
    for (index <- getVehicleIndex(pilotedVehicle, lane)) yield {
      lane.vehicles.lift(index - 1).getOrElse(lane.vehicleAtInfinityForward)
    }
  }
  def getVehicleAfter(pilotedVehicle: PilotedVehicle,
                      lane: Lane): Option[PilotedVehicle] = {
    for (index <- getVehicleIndex(pilotedVehicle, lane)) yield {
      lane.vehicles.lift(index + 1).getOrElse(lane.vehicleAtInfinityBackwards)
    }
  }

  /**
    * TODO : getFractionBetweenEndpoints(pilotedVehicle: PilotedVehicle, lane: Lane): Double
    *         getFractionalPoint(lane: Lane): Spatial
    */
  def fractionCompleted(pilotedVehicle: PilotedVehicle,
                        lane: Lane): Double = {
    val vehicleDistance: Distance = pilotedVehicle.distanceTo(lane.end)
    1.0 - vehicleDistance / lane.length
  }

  // TODO See if this actually works... Very important to MOBIL algorithm.
  def moveToNeighboringLane(pilotedVehicle: PilotedVehicle,
                            lane: Lane,
                            desiredLane: Lane): Lane = {
    val fractionComplete = fractionCompleted(pilotedVehicle, lane)
    val disruptionPoint: QuantityVector[Distance] =
      desiredLane.end.vectorTo(desiredLane.beginning).times(fractionComplete)
    val movedVehicle = pilotedVehicle.copy(
      vehicle = pilotedVehicle.vehicle.copy(
        spatial = Spatial.withVecs(disruptionPoint))
    )
    desiredLane.copy(vehicles = desiredLane.vehicles :+ movedVehicle)
  }
}
