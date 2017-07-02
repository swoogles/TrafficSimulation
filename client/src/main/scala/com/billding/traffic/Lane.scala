package com.billding.traffic

import cats.data.NonEmptyList
import com.billding.physics.Spatial
import com.billding.{traffic, _}
import squants.motion._
import squants.{QuantityVector, Time, Velocity}

trait Segment {
  /*
    Figure out where this belongs in the Road/Lane/Scene interplay.
    I will need to consider arc lengths.
   */
}
trait Lane {
  val vehicles: List[PilotedVehicle]
  val vehicleSource: VehicleSource
  val beginning: Spatial
  val end: Spatial
  val vehicleAtInfinity: PilotedVehicle
}

case class LaneImpl(vehicles: List[PilotedVehicle], vehicleSource: VehicleSource, beginning: Spatial, end: Spatial) extends Lane {

  private val infinityPoint: QuantityVector[Distance] = beginning.vectorTo(end).normalize.map{ x: Distance => x * 10000}
  val vehicleAtInfinity: PilotedVehicle = {
    val spatial =  Spatial.withVecs(infinityPoint, Spatial.ZERO_VELOCITY_VECTOR, Spatial.ZERO_DIMENSIONS_VECTOR )
    PilotedVehicle.commuter(spatial, new IntelligentDriverModelImpl, spatial)
  }
}

object Lane extends LaneFunctions {

  private def responsesInOneLane(vehicles: NonEmptyList[PilotedVehicle], speedLimit: Velocity): NonEmptyList[Acceleration] = {
    val target = vehicles.head
    vehicles.tail match {
      case follower :: Nil => NonEmptyList(follower.reactTo(target, speedLimit), Nil) // :: responsesInOneLane(follower :: rest)
      case follower :: rest => {
        follower.reactTo(target, speedLimit) :: responsesInOneLane(NonEmptyList(follower, rest), speedLimit)
      }
    }
  }

    // TODO: Test new vehicles from source
    def update(lane: LaneImpl, speedLimit: Velocity, t: Time, dt: Time): LaneImpl = {
      val newVehicleOption: Option[PilotedVehicle] = lane.vehicleSource.produceVehicle(t, dt, lane.vehicleAtInfinity.spatial)
      val newVehicleList: List[PilotedVehicle] =
      newVehicleOption match {
        case Some(newVehicle) if (lane.vehicles.size > 30) => lane.vehicles.tail :+ newVehicle
        case Some(newVehicle) => lane.vehicles :+ newVehicle
        case None => lane.vehicles
      }

      val laneWithNewVehicle = lane.copy(vehicles = newVehicleList)
      val dMomentumList = responsesInOneLanePrep(laneWithNewVehicle, speedLimit)
      val vehiclesAndUpdates = newVehicleList.zip(dMomentumList)
      val newVehicles: List[PilotedVehicle] = vehiclesAndUpdates map {
        case (vehicle, dMomentum) => vehicle.accelerateAlongCurrentDirection(dt, dMomentum)
      }
      // TODO Decide if this can be removed/moved
      newVehicles.sliding(2).map{case (leader :: follower :: Nil) => {
        val tooClose = leader.tooClose(follower)
        if (tooClose) {
          println("TOO CLOSE!! STOP!!")
        }
        tooClose
      }}

      laneWithNewVehicle.copy(vehicles = newVehicles)
    }

  def responsesInOneLanePrep(lane: Lane, speedLimit: Velocity): List[Acceleration] = {
    lane.vehicles match {
      case Nil => Nil
      case head :: _ => responsesInOneLane(NonEmptyList(lane.vehicleAtInfinity, lane.vehicles), speedLimit).toList
    }
  }

}

