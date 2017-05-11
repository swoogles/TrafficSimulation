package com.billding

import cats.data.NonEmptyList
import squants.{QuantityVector, Time, Velocity}
import squants.motion._
import squants.space.Kilometers

trait Segment {
  /*
    Figure out where this belongs in the Road/Lane/Scene interplay.
    I will need to consider arc lengths.
   */
}
trait Lane {
  val vehicles: List[PilotedVehicle]
  val vehicleSource: VehicleSource
  def beginning: Spatial
  def end: Spatial


  /**
    * TODO Lane should be responsible for creating the vehicles at infinity, not the driver/vehicle.
    */
  private val infinityPoint: QuantityVector[Distance] = beginning.vectorTo(end).normalize.map{ x: Distance => x * 10000}
  val vehicleAtInfinity: PilotedVehicle = {
    val spatial =  Spatial.withVecs(infinityPoint, Spatial.ZERO_VELOCITY, Spatial.ZERO_DIMENSIONS_VECTOR )
    PilotedVehicle.commuter(spatial, new IntelligentDriverModelImpl)
  }

  // TODO put these in appropriate pattern matching? Not sure they mean much hanging on their own.
  private val leadingVehicle: Option[PilotedVehicle] = vehicles.headOption
  private val followingVehicle: Option[PilotedVehicle] = vehicles.tail.headOption

  /** vehicles.headOption.flatMap( vehicle.follower )
      Or should it be based on the car that you're following?
      I really need to hammer out the infinity vehicles here.
      They should exist extended a certain distance beyond the
      Lane! Not based on the vehicles velocity!
      I think I'm going to need a [[Segment]] class beneath Lane.

    val followingVehicle: PilotedVehicle = vehicles.headOption.flatMap(_.follower)
    println("Distance between: " + leadingVehicle.map(_.spatial.distanceTo(followingVehicle.spatial)))
  */
}

case class LaneImpl(vehicles: List[PilotedVehicle], vehicleSource: VehicleSource, beginning: Spatial, end: Spatial) extends Lane

object Lane {

  // TODO: Test new vehicles from source
  def update(lane: Lane, speedLimit: Velocity, t: Time, dt: Time): Lane = {
    val newVehicleOption: Option[PilotedVehicle] = lane.vehicleSource.produceVehicle(t)
    val newVehicleList: List[PilotedVehicle] =
      if ( newVehicleOption.isDefined ) lane.vehicles :+ newVehicleOption.get
      else lane.vehicles

    val dMomentumList = responsesInOneLanePrep(newVehicleList, lane, speedLimit)
    val vehiclesAndUpdates = newVehicleList.zip(dMomentumList)
    val newVehicles = vehiclesAndUpdates map {
      case (vehicle, dMomentum) => vehicle.accelerateAlongCurrentDirection(dt, dMomentum)
    }
    new LaneImpl(newVehicles, lane.vehicleSource, lane.beginning, lane.end)
  }

  def responsesInOneLanePrep(vehicles: List[PilotedVehicle], lane: Lane, speedLimit: Velocity): List[Acceleration] = {
    vehicles match {
      case Nil => Nil
      case head :: _ => responsesInOneLane(NonEmptyList(lane.vehicleAtInfinity, vehicles), speedLimit).toList
    }
  }

  private def responsesInOneLane(vehicles: NonEmptyList[PilotedVehicle], speedLimit: Velocity): NonEmptyList[Acceleration] = {
    val target = vehicles.head
    vehicles.tail match {
      case follower :: Nil => NonEmptyList(follower.reactTo(target, speedLimit), Nil) // :: responsesInOneLane(follower :: rest)
      case follower :: rest =>
        follower.reactTo(target, speedLimit)  :: responsesInOneLane(NonEmptyList(follower,rest), speedLimit)
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
          [[com.billding.Lane]]? I think that's it.
    */

}

