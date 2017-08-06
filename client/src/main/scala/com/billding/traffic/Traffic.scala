package com.billding.traffic

import cats.data.{NonEmptyList, Validated}
import squants.motion._
import squants.{Length, Time, Velocity}

trait Scene {
  val lanes: List[LaneImpl]
  val t: Time
  implicit val dt: Time
  val speedLimit: Velocity
  private val updateLane: (LaneImpl) => LaneImpl = (lane: LaneImpl) => Lane.update(lane,speedLimit, t, dt)
  val canvasDimensions: (Length, Length)

  def update(speedLimit: Velocity)(implicit dt: Time): SceneImpl = {
    val nextT =  this.t + this.dt
    val res: List[LaneImpl] = lanes map updateLane
    SceneImpl(res, nextT, this.dt, speedLimit, this.canvasDimensions)
  }
}

case class SceneImpl(
  lanes: List[LaneImpl],
  t: Time,
  dt: Time,
  speedLimit: Velocity,
  canvasDimensions: (Length, Length)
) extends Scene


trait ErrorMsg {
  val description: String
}


trait Universe {
  // NOTE: Assumes vehicles travelling in same direction
  val speedLimit: Velocity
//  val idm: IntelligentDriverModel
  def calculateDriverResponse(vehicle: PilotedVehicle, scene: Scene): Acceleration
  // TODO Work on this after Lane processing functions.
  def getAllActions(scene: Scene): List[(PilotedVehicle, Acceleration)]
  def update(scene: Scene, dt: Time): Validated[NonEmptyList[ErrorMsg], Scene]
//  def createScene(roads: Road): Scene
  // Get vehicles that haven't taken a recent action.
  def reactiveVehicles(scene: Scene): List[PilotedVehicle]
}
