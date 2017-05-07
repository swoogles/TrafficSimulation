package com.billding

import cats.data.{NonEmptyList, Validated}
import squants.{Time, Velocity}
import squants.motion._

trait Scene {
  def vehicles(): List[PilotedVehicle]
  def roads(): List[Road]
  val t: Time
  val dt: Time
}


trait Road {
  def lanes: List[Lane] // Maybe should be private impl detail?
  def produceVehicles(t: Time)
  def beginning: Spatial
  def end: Spatial
}

object Road {
  def apply(beginning: Spatial, end: Spatial, sourceEnd: Integer, carRate: Integer): Road = {
    ???
  }
}

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
  def createScene(roads: Road): Scene
  // Get vehicles that haven't taken a recent action.
  def reactiveVehicles(scene: Scene): List[PilotedVehicle]
}
