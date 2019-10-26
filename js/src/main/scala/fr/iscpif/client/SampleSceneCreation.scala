package fr.iscpif.client

import fr.iscpif.client.previouslySharedCode.physics.{Spatial, SpatialImpl}
import fr.iscpif.client.previouslySharedCode.traffic._
import squants.Length
import squants.motion.{KilometersPerHour, Velocity, VelocityUnit}
import squants.space.{Kilometers, LengthUnit, Meters}
import squants.time.{Milliseconds, Seconds, Time}

case class NamedScene(name: String, scene: SceneImpl)

class SampleSceneCreation(endingSpatial: SpatialImpl) {
  import PilotedVehicle.createVehicle
  implicit val DT: Time = Milliseconds(20)

  private def simpleVehicle(
      pIn1: (Double, Double, Double, LengthUnit),
      vIn1: (Double, Double, Double, VelocityUnit)
  ) = {
    createVehicle(pIn1, vIn1, endingSpatial)
  }

  private def simplerVehicle(xPos: Double, xV: Double) =
    simpleVehicle((xPos, 0, 0, Meters), (xV, 0, 0, KilometersPerHour))

  val emptyScene =
    NamedScene(
      "Empty Scene",
      createWithVehicles(
        Seconds(2),
        List(
          simplerVehicle(15, 100)
        )
      )
    )

  val scene1 =
    NamedScene(
      "group encountering a stopped vehilce",
      createWithVehicles(
        Seconds(300),
        List(
          simplerVehicle(120, 0.1),
          simplerVehicle(60, 100),
          simplerVehicle(45, 100),
          simplerVehicle(30, 100),
          simplerVehicle(15, 100)
        )
      )
    )

  val scene2 =
    NamedScene(
      "stopped group getting back up to speed",
      createWithVehicles(
        Seconds(100),
        List(
          simplerVehicle(125, 0),
          simplerVehicle(120, 0),
          simplerVehicle(115, 0),
          simplerVehicle(110, 0),
          simplerVehicle(105, 0),
          simplerVehicle(100, 0),
          simplerVehicle(95, 0),
          simplerVehicle(90, 0)
        )
      )
    )

  val multipleStoppedGroups =
    NamedScene(
      "multiple stopped groups getting back up to speed",
      createWithVehicles(
        Seconds(100),
        List(
          simplerVehicle(180, 0),
          simplerVehicle(175, 0),
          simplerVehicle(170, 0),
          simplerVehicle(165, 0),
          simplerVehicle(160, 0),
          simplerVehicle(155, 0),
          simplerVehicle(150, 0),
          simplerVehicle(125, 0),
          simplerVehicle(120, 0),
          simplerVehicle(115, 0),
          simplerVehicle(110, 0),
          simplerVehicle(105, 0),
          simplerVehicle(100, 0),
          simplerVehicle(95, 0),
          simplerVehicle(90, 0)
        )
      )
    )

  val startingScene = scene1

  def createWithVehicles(sourceTiming: Time,
                         vehicles: List[PilotedVehicleImpl]): SceneImpl = {

    val speedLimit: Velocity = KilometersPerHour(65)
    val originSpatial = Spatial((0, 0, 0, Kilometers))
    val endingSpatial = Spatial((0.5, 0, 0, Kilometers))
    val canvasDimensions: (Length, Length) = (Kilometers(.25), Kilometers(.5))

    val lane =
      Lane.apply(sourceTiming,
                 originSpatial,
                 endingSpatial,
                 speedLimit,
                 vehicles)
    val street = StreetImpl(List(lane), originSpatial, endingSpatial)
    SceneImpl(
      List(street),
      Seconds(0.2),
      DT,
      speedLimit,
      canvasDimensions
    )
  }
}
