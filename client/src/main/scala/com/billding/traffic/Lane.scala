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
    PilotedVehicle.commuter(spatial, new IntelligentDriverModelImpl)
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
    def update(lane: Lane, speedLimit: Velocity, t: Time, dt: Time): Lane = {
      val newVehicleOption: Option[PilotedVehicle] = lane.vehicleSource.produceVehicle(t, dt)
      println("starting vehicle list size: " + lane.vehicles.size)
      val newVehicleList: List[PilotedVehicle] =
        if (newVehicleOption.isDefined) lane.vehicles :+ newVehicleOption.get
        else lane.vehicles
      println("updated vehicle list size: " + newVehicleList.size)

      val laneWithNewVehicle = LaneImpl(newVehicleList, lane.vehicleSource, lane.beginning, lane.end)
      val dMomentumList = responsesInOneLanePrep(laneWithNewVehicle, speedLimit)
      val vehiclesAndUpdates = newVehicleList.zip(dMomentumList)
      val newVehicles: List[PilotedVehicle] = vehiclesAndUpdates map {
        case (vehicle, dMomentum) => vehicle.accelerateAlongCurrentDirection(dt, dMomentum)
      }
      newVehicles.sliding(2).map{case (leader :: follower :: Nil) => {
        val tooClose = leader.tooClose(follower)
        if (tooClose) {
          println("TOO CLOSE!! STOP!!")
        }
        tooClose
      }}
      println("newVehicles list size: " + newVehicles.size)

      val result = LaneImpl(newVehicles, lane.vehicleSource, lane.beginning, lane.end)
      println("result vehicles size: " + result.vehicles.size)
      result
    }

  def responsesInOneLanePrep(lane: Lane, speedLimit: Velocity): List[Acceleration] = {
    lane.vehicles match {
      case Nil => Nil
      case head :: _ => responsesInOneLane(NonEmptyList(lane.vehicleAtInfinity, lane.vehicles), speedLimit).toList
    }
  }

  /** TODO lane.leader.follower
          How cool would that be?
          The syntax is friendly at first glance. It would need to accompany the optional behavior of:
            -lane.leader
            -vehicle.follower

          It would look more like:
            val leader: Option[PilotedVehicle] =
              lane.leader
            val follower: Option[PilotedVehicle] =
              leader
              .flatMap(leader=>leader.follower)

          I'm really starting to think this is too internal-fiddly to be exposed
          outside of the class. I want this behavior, but hidden inside...
          [[traffic.Lane]]? I think that's it.
    */

}

