package com.billding

import cats.data.{NonEmptyList, Validated}
import squants.{Time, Velocity}
import squants.motion._

trait Scene {
  val lanes: List[Lane]
  val t: Time
  implicit val dt: Time
  val speedLimit: Velocity
  private val updateLane = (lane: Lane) => Lane.update(lane,speedLimit, t, dt)

  def update(speedLimit: Velocity, t: Time)(implicit dt: Time): Scene = {
    val nextT =  this.t + this.dt
    val res: List[Lane] = lanes map updateLane
    SceneImpl(res, nextT, this.dt, speedLimit)
  }
}

case class SceneImpl(
  lanes: List[Lane],
  t: Time,
  dt: Time,
  speedLimit: Velocity
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
