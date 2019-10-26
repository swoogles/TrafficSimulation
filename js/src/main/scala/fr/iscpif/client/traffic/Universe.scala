package fr.iscpif.client.traffic

import cats.data.{NonEmptyList, Validated}
import squants.{Acceleration, Time, Velocity}

trait Universe {
  val speedLimit: Velocity
  def calculateDriverResponse(vehicle: PilotedVehicle,
                              scene: Scene): Acceleration
  // TODO Work on this after Lane processing functions.
  def getAllActions(scene: Scene): List[(PilotedVehicle, Acceleration)]
  def update(scene: Scene, dt: Time): Validated[NonEmptyList[ErrorMsg], Scene]
  def reactiveVehicles(scene: Scene): List[PilotedVehicle]
}
