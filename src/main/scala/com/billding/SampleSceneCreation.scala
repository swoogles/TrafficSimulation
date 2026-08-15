package com.billding

import com.billding.physics.{RingPath, Spatial}
import com.billding.traffic._
import squants.Length
import squants.motion.{DistanceUnit, KilometersPerHour, Velocity, VelocityUnit}
import squants.space.{Kilometers, Meters}
import squants.time.{Milliseconds, Seconds, Time}

class SampleSceneCreation(endingSpatial: Spatial)(implicit val DT: Time) {

  private def simplerVehicle(xPos: Double, xV: Double) = {
    val lengthUnit: DistanceUnit = Meters
    val velocityUnit: VelocityUnit = KilometersPerHour
    PilotedVehicle((xPos, 0.0, 0.0, lengthUnit), endingSpatial, (xV, 0.0, 0.0, velocityUnit))
  }

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
      "group encountering a stopped vehicle",
      createWithVehicles(
        Seconds(3),
        List(
          simplerVehicle(90, 0.1),
          simplerVehicle(55, 100),
          simplerVehicle(40, 100),
          simplerVehicle(25, 100),
          simplerVehicle(10, 100)
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
        Seconds(4),
        List(
          simplerVehicle(125, 0),
          simplerVehicle(120, 0),
          simplerVehicle(115, 0),
          simplerVehicle(110, 0),
          simplerVehicle(105, 0),
          simplerVehicle(100, 0),
          simplerVehicle(95, 0),
          simplerVehicle(90, 0),
          simplerVehicle(60, 0),
          simplerVehicle(55, 0),
          simplerVehicle(50, 0),
          simplerVehicle(45, 0),
          simplerVehicle(40, 0),
          simplerVehicle(35, 0),
          simplerVehicle(30, 0)
        )
      )
    )

  val singleCarApproachingAStoppedCar =
    NamedScene(
      "single car encountering a stopped vehicle",
      createWithVehicles(
        Seconds(12),
        List(
          simplerVehicle(120, 0),
          simplerVehicle(60, 60)
        )
      )
    )

  private def createWithVehicles(
    sourceTiming: Time,
    vehicles: List[PilotedVehicle]
  ): StreetScene = {

    val speedLimit: Velocity = KilometersPerHour(45) // TODO Connect this to Car Speed Control.
    val originSpatial = Spatial((0, 0, 0, Kilometers))
    val endingSpatial = Spatial((0.5, 0, 0, Kilometers))
    val canvasDimensions: (Length, Length) = (Kilometers(.25), Kilometers(.5))

    val lane =
      Lane.apply(sourceTiming, originSpatial, endingSpatial, speedLimit, vehicles)
    val street = Street(List(lane), originSpatial, endingSpatial)
    StreetScene(
      List(street),
      Seconds(0.2),
      DT,
      speedLimit,
      canvasDimensions
    )
  }

  private val ringSpeedLimit: Velocity = KilometersPerHour(45)

  /**
    * A fixed population going round a closed loop, which is where traffic gets to misbehave
    * on its own: nothing enters, nothing leaves, so any jam you see the cars made themselves.
    */
  private def ring(carCount: Int, circumference: Length): RingScene =
    RingScene(
      TrackLane.evenlySpaced(
        RingPath.ofCircumference(circumference),
        carCount,
        ringSpeedLimit,
        ringSpeedLimit
      ),
      Seconds(0),
      DT
    )

  /*
  Car counts on a 400m loop, with 8m cars that want 6m of room at a standstill. Past about
  28 cars there is no room left to want, and the whole ring locks solid and stays that way.
  These three sit either side of the interesting part: free flowing, dense enough to be
  fragile, and crawling.
   */
  val quietRing =
    NamedScene("ring road, 8 cars", ring(8, Meters(400)))

  val busyRing =
    NamedScene("ring road, 16 cars", ring(16, Meters(400)))

  val jammedRing =
    NamedScene("ring road, 22 cars", ring(22, Meters(400)))
}
