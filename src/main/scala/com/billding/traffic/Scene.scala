package com.billding.traffic

import squants.{Length, Time, Velocity}

case class Scene(
                  streets: List[Street],
                  t: Time,
                  dt: Time,
                  speedLimit: Velocity,
                  canvasDimensions: (Length, Length) // TODO This probably deserves to be inside a more specific Canvas class
) {

  private val updateLane: (Lane) => Lane =
    (lane: Lane) => Lane.update(lane, t, dt)

  def updateWithSpeedLimit(speedLimit: Velocity)(implicit dt: Time): Scene = {
    val nextT = this.t + this.dt
    val res: List[Street] = {
      streets.map(street => street.updateLanes(updateLane))
    }
    Scene(res, nextT, this.dt, speedLimit, this.canvasDimensions)
  }

  def updateAllStreets(func: Lane => Lane): Scene = {
    val newStreets = streets.map { street: Street =>
      street.updateLanes(func)
    }
    this.copy(streets = newStreets)
  }

  private val allVehicles: List[PilotedVehicle] = streets.flatMap(
    _.lanes.flatMap(_.vehicles)
  )

  def applyToAllVehicles[T](f: PilotedVehicle => T): List[T] =
    for (vehicle <- allVehicles) yield {
      f(vehicle)
    }

}
