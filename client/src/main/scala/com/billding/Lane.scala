package com.billding

import cats.data.{NonEmptyList}
import squants.{Time, Velocity}
import squants.motion._
trait Lane {
  val vehicles: List[PilotedVehicle]
  val vehicleSource: VehicleSource
  def beginning: Spatial
  def end: Spatial
}

private case class LaneImpl(vehicles: List[PilotedVehicle], vehicleSource: VehicleSource, beginning: Spatial, end: Spatial) extends Lane

object Lane {

  def update(lane: Lane, speedLimit: Velocity, t: Time, dt: Time): Lane = {
    val dMomentumList = responsesInOneLanePrep(lane.vehicles, speedLimit)
    val vehiclesAndUpdates = lane.vehicles.zip(dMomentumList)
    val newVehicles = vehiclesAndUpdates map {
      case (vehicle, dMomentum) => vehicle.accelerateAlongCurrentDirection(dt, dMomentum)
    }
    new LaneImpl(newVehicles, lane.vehicleSource, lane.beginning, lane.end)
  }

  def responsesInOneLanePrep(vehicles: List[PilotedVehicle], speedLimit: Velocity): List[Acceleration] = {
    vehicles match {
      case Nil => Nil
      case head :: _ => responsesInOneLane(NonEmptyList(head.createInfiniteVehicle, vehicles), speedLimit).toList
    }
  }

  private def responsesInOneLane(vehicles: NonEmptyList[PilotedVehicle], speedLimit: Velocity): NonEmptyList[Acceleration] = {
    val target = vehicles.head
    vehicles.tail match {
      case follower :: Nil => NonEmptyList(follower.reactTo(target, speedLimit), Nil) // :: responsesInOneLane(follower :: rest)
      case follower :: rest => {
        follower.reactTo(target, speedLimit)  :: responsesInOneLane(NonEmptyList(follower,rest), speedLimit)
      }
    }
  }

}

