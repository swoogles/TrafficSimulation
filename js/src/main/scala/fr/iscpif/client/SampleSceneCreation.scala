package fr.iscpif.client

import com.billding.physics.{Spatial, SpatialImpl}
import com.billding.traffic._
import squants.Length
import squants.motion.{KilometersPerHour, Velocity, VelocityUnit}
import squants.space.{Kilometers, LengthUnit, Meters}
import squants.time.{Milliseconds, Seconds, Time}

class SampleSceneCreation(endingSpatial: SpatialImpl) {
  import PilotedVehicle.createVehicle
  implicit val DT: Time = Milliseconds(20)

  def simpleVehicle
  (
      pIn1: (Double, Double, Double, LengthUnit),
      vIn1: (Double, Double, Double, VelocityUnit) =
        (0, 0, 0, KilometersPerHour)
      ) = {
    createVehicle(pIn1, vIn1, endingSpatial)
  }

  val scene1 = createWithVehicles(
    List(
      simpleVehicle((100, 0, 0, Meters), (0.1, 0, 0, KilometersPerHour)),
      simpleVehicle((80, 0, 0, Meters), (50, 0, 0, KilometersPerHour)),
      simpleVehicle((60, 0, 0, Meters), (100, 0, 0, KilometersPerHour))
    )
  )

  val scene2 = createWithVehicles(
    List(
      simpleVehicle((100, 0, 0, Meters), (0, 0, 0, KilometersPerHour)),
      simpleVehicle((95, 0, 0, Meters), (0, 0, 0, KilometersPerHour)),
      simpleVehicle((90, 0, 0, Meters), (0, 0, 0, KilometersPerHour))
    )
  )

  def createWithVehicles(vehicles: List[PilotedVehicleImpl]): SceneImpl = {

    val speedLimit: Velocity = KilometersPerHour(65)
    val originSpatial = Spatial((0, 0, 0, Kilometers))
    val endingSpatial = Spatial((0.5, 0, 0, Kilometers))
    val canvasDimensions: (Length, Length) = (Kilometers(.25), Kilometers(.5))

    val lane =
    Lane.apply(Seconds(2), originSpatial, endingSpatial, speedLimit, vehicles)
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
