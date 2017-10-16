package com.billding.traffic

import cats.data.{NonEmptyList, Validated}
import squants.motion._
import squants.{Length, Time, Velocity}

trait Scene {
  val streets: List[StreetImpl]
  val t: Time
  implicit val dt: Time
  val speedLimit: Velocity
  private val updateLane: (LaneImpl) => LaneImpl = (lane: LaneImpl) => Lane.update(lane, t, dt)
  val canvasDimensions: (Length, Length)

  def update(speedLimit: Velocity)(implicit dt: Time): SceneImpl = {
    val nextT =  this.t + this.dt
    val res: List[StreetImpl] = {
      streets.map(street=>
        street.updateLanes(updateLane)
      )
    }
    SceneImpl(res, nextT, this.dt, speedLimit, this.canvasDimensions)
  }

  def updateAllStreets(func: LaneImpl => LaneImpl): SceneImpl
  // TODO Include Map[Idx, Vehicle]

  val allVehicles: List[PilotedVehicle]
}

case class SceneImpl(
                      streets: List[StreetImpl],
                      t: Time,
                      dt: Time,
                      speedLimit: Velocity,
                      canvasDimensions: (Length, Length)
) extends Scene {

  def updateAllStreets(func: LaneImpl => LaneImpl): SceneImpl = {
    val newStreets = streets.map { street: StreetImpl =>
      street.updateLanes(func)
    }
    this.copy(streets=newStreets)
  }

  val allVehicles: List[PilotedVehicle] = streets.flatMap(
    _.lanes.flatMap(_.vehicles)
  )
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
  //  def createScene(roads: Road): Scene
  // Get vehicles that haven't taken a recent action.
  def reactiveVehicles(scene: Scene): List[PilotedVehicle]
}
