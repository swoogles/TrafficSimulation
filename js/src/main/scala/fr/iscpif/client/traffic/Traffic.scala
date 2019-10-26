package fr.iscpif.client.traffic

import cats.data.{NonEmptyList, Validated}
import squants.{Acceleration, Length, Time, Velocity}

trait Scene {
  val canvasDimensions: (Length, Length) // This probably deserves to be inside a more specific Canvas class

  def updateAllStreets(func: LaneImpl => LaneImpl): SceneImpl

  def updateSpeedLimit(speedLimit: Velocity)(implicit dt: Time): SceneImpl
  def applyToAllVehicles[T](f: PilotedVehicle => T): List[T]
}

case class SceneImpl(
    streets: List[StreetImpl],
    t: Time,
    dt: Time,
    speedLimit: Velocity,
    canvasDimensions: (Length, Length)
) extends Scene {

  private val updateLane: (LaneImpl) => LaneImpl = (lane: LaneImpl) =>
    Lane.update(lane, t, dt)

  def updateSpeedLimit(speedLimit: Velocity)(implicit dt: Time): SceneImpl = {
    val nextT = this.t + this.dt
    val res: List[StreetImpl] = {
      streets.map(street => street.updateLanes(updateLane))
    }
    SceneImpl(res, nextT, this.dt, speedLimit, this.canvasDimensions)
  }

  def updateAllStreets(func: LaneImpl => LaneImpl): SceneImpl = {
    val newStreets = streets.map { street: StreetImpl =>
      street.updateLanes(func)
    }
    this.copy(streets = newStreets)
  }

  private val allVehicles: List[PilotedVehicle] = streets.flatMap(
    _.lanes.flatMap(_.vehicles)
  )

  def applyToAllVehicles[T](f: PilotedVehicle => T): List[T] = {
    for (vehicle <- allVehicles) yield {
      f(vehicle)
    }
  }

}

trait ErrorMsg {
  val description: String
}

trait Universe {
  val speedLimit: Velocity
  def calculateDriverResponse(vehicle: PilotedVehicle,
                              scene: Scene): Acceleration
  // TODO Work on this after Lane processing functions.
  def getAllActions(scene: Scene): List[(PilotedVehicle, Acceleration)]
  def update(scene: Scene, dt: Time): Validated[NonEmptyList[ErrorMsg], Scene]
  def reactiveVehicles(scene: Scene): List[PilotedVehicle]
}