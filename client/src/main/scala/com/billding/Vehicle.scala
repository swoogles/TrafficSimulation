package com.billding

import squants.mass.Kilograms
import squants.motion.{Acceleration, Distance, KilogramForce}
import squants.{Mass, Time}
import squants.space.LengthConversions._
import squants.time.TimeConversions._

sealed trait Vehicle {
  val spatial: Spatial
  val weight: Mass
  val accelerationAbility: Acceleration
  val brakingAbility: Acceleration
}

case class Car(
                spatial: Spatial,
                weight: Mass = Kilograms(800),
                accelerationAbility: Acceleration = (0.3.meters.per((1 seconds).squared)),
                brakingAbility: Acceleration = (3.0.meters.per((1 seconds).squared))
              ) extends Vehicle

case class Truck(
                  spatial: Spatial,
                  weight: Mass = Kilograms(3000),
                  accelerationAbility: Acceleration = (0.3.meters.per((1 seconds).squared)),
                  brakingAbility: Acceleration = (2.0.meters.per((1 seconds).squared))
                ) extends Vehicle


trait PilotedVehicle extends Vehicle with Driver

class PilotedVehicleImpl(driver: Driver, vehicle: Vehicle) extends PilotedVehicle with Spatial {
  val reactionTime: Time = driver.reactionTime
  val weight = vehicle.weight
  val spatial = vehicle.spatial
  val currentManeuver = Coast
  val maneuverTakenAt: Time = 1 seconds
  val accelerationAbility = vehicle.accelerationAbility
  val brakingAbility = vehicle.brakingAbility
  val preferredDynamicSpacing = driver.preferredDynamicSpacing
  val minimumDistance = driver.minimumDistance
  val p = spatial.p
  val v = spatial.v
  val dimensions = spatial.dimensions
}
