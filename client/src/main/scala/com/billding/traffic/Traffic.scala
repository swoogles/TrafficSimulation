package com.billding.traffic

import cats.data.{NonEmptyList, Validated}
import squants.motion._
import squants.{Length, Time, Velocity}

trait Scene {
  val lanes: List[Lane]
  val t: Time
  implicit val dt: Time
  val speedLimit: Velocity
  private def updateLane(lane: Lane): Lane  = {
    Lane.update(lane,speedLimit, t+dt, dt)
  }
  val dimensions: Dimensions

  def update(speedLimit: Velocity): Scene = {
//    println("updating scene")
    val newLanes = lanes map updateLane
    if (newLanes.flatten(_.vehicles).length > 0){
//      println("vehicle in scene")
    }
//    println(SceneImpl(lanes map updateLane, this.t + this.dt, this.dt, speedLimit, this.dimensions))
    SceneImpl(lanes map updateLane, this.t + this.dt, this.dt, speedLimit, this.dimensions)
  }
}

case class Dimensions( height: Length, width: Length )

case class SceneImpl(lanes: List[Lane], t: Time, dt: Time, speedLimit: Velocity, dimensions: Dimensions) extends Scene


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
