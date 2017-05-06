package com.billding

import cats.data.{NonEmptyList, Validated}
import squants.{Mass, Time, Velocity}
import squants.motion.{Acceleration, Distance, MetersPerSecond}
import squants.space.Meters

trait Scene {
  def vehicles(): List[PilotedVehicle]
  def roads(): List[Road]
  val t: Time
  val dt: Time
}

trait VehicleSource {
  def produceVehicle(t: Time): Option[PilotedVehicle]
  // Figure out how to accommodate both behaviors
  val spacingInDistance: Distance
  val spacingInTime: Time
}

object VehicleSource {
  def withTimeSpacing(averageDt: Time): VehicleSource = ???
  def withDistanceSpacing(averageDpos: Distance): VehicleSource = ???
}

trait Lane {
  def vehicles(): List[PilotedVehicle]
  val vehicleSource: VehicleSource
  def beginning: Spatial
  def end: Spatial
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
  def getAllActions(scene: Scene): List[(PilotedVehicle, Acceleration)]
  def update(scene: Scene, dt: Time): Validated[NonEmptyList[ErrorMsg], Scene]
  def createScene(roads: Road): Scene
  // Get vehicles that haven't taken a recent action.
  def reactiveVehicles(scene: Scene): List[PilotedVehicle]
}
