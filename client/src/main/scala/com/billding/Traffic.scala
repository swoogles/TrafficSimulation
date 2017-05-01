package com.billding

import cats.data.{NonEmptyList, Validated}
import squants.mass.MassUnit
import squants.space.LengthUnit
import squants.time.TimeUnit

sealed trait Maneuver
/*
  Decide how much of a spectrum Braking and Accelerating can use..
  Version 1 will probably only have 1 hard setting for each.
 */
case object Brake extends Maneuver
case object Accelerate extends Maneuver
case object Maintain extends Maneuver // Should this also be the move when driver is "cooling down" ?
/*
  This will be a slight decrease in speed. To be more accurate, it would be a larger decrease when travelling
  at a higher speed with increased wind resistance.
  Probably going to save this for a later version, as a simple simulation can run without this.
 */
case object Coast extends Maneuver

trait WeightedManeuver {
  val maneuver: Maneuver
  val urgency: Float
}

trait Driver {
  val reactionTime: TimeUnit
  val spatial: Spatial
}

trait Vehicle {
  val spatial: Spatial
  val weight: MassUnit
  val brakingAbility: Float
}

trait PilotedVehicle {
  val driver: Driver
  val vehicle: Vehicle
  val currentManeuver: Maneuver
  val maneuverTakenAt: TimeUnit
}

trait Scene {
  def vehicles(): List[PilotedVehicle]
  val t: TimeUnit
  val dt: TimeUnit
}

trait VehicleSource {
  def vehicles(): Stream[PilotedVehicle]
  def produceVehicle(t: TimeUnit): Option[PilotedVehicle]
  // Figure out how to accommodate both behaviors
  val spacingInDistance: LengthUnit
  val spacingInTime: TimeUnit
}

object VehicleSource {
  def withTimeSpacing(averageDt: TimeUnit): VehicleSource = ???
  def withDistanceSpacing(averageDpos: LengthUnit): VehicleSource = ???
}

trait Lane {
  def vehicles(): List[PilotedVehicle]
  val vehicleSource: VehicleSource
}

trait Road {
  def lanes: List[Lane]
  def produceVehicles(t: TimeUnit)
  def beginning: Spatial
  def end: Spatial
}

trait Universe {
  def calculateDriverResponse(vehicle: PilotedVehicle, scene: Scene): Maneuver
  def getAllActions(scene: Scene): List[(PilotedVehicle, Maneuver)]
  /*
    Consider this as the first use case for Validated.
   */
  def update(scene: Scene, dt: TimeUnit): Validated[NonEmptyList[String], Scene]
  def reactTo(decider: PilotedVehicle, obstacle: Spatial): WeightedManeuver
  def createScene(roads: Road): Scene
  // Get vehicles that haven't taken a recent action.
  def reactiveVehicles(scene: Scene): List[PilotedVehicle]
}

