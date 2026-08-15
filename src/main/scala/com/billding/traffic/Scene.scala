package com.billding.traffic

import com.billding.svgRendering.{Projection, RenderedVehicle, RoadShape, RoadStrip}
import squants.motion.MetersPerSecond
import squants.{Length, Time, Velocity}

/**
  * A running simulation, whatever shape of road it happens to run on.
  *
  * The two implementations differ in more than geometry: a street has cars entering and
  * leaving, while a ring holds a fixed population and lets you watch what it does to itself.
  */
sealed trait Scene {

  def t: Time
  def dt: Time
  def speedLimit: Velocity

  /** Advance one tick. */
  def updateWithSpeedLimit(speedLimit: Velocity): Scene

  /** The cars, as the canvas wants them. */
  def renderables: List[RenderedVehicle]

  /** The road they are driving on, laid down before them. */
  def roadShapes: List[RoadShape]

  /** How this scene wants to be laid out on a canvas of the given pixel width. */
  def project(pixelWidth: Int): Projection

  /** How often new cars arrive, for scenes that have anywhere for them to arrive from. */
  def sourceTiming: Option[Time]
}

case class StreetScene(
  streets: List[Street],
  t: Time,
  dt: Time,
  speedLimit: Velocity,
  canvasDimensions: (Length, Length) // TODO This probably deserves to be inside a more specific Canvas class
) extends Scene {

  def updateWithSpeedLimit(speedLimit: Velocity): StreetScene = {
    val nextT = this.t + this.dt
    val res: List[Street] = {
      streets.map(
        street =>
          street.updateLanes(
            (lane: Lane) => Lane.update(lane, t, dt)
          )
      )
    }
    StreetScene(res, nextT, this.dt, speedLimit, this.canvasDimensions)
  }

  def updateAllStreets(func: Lane => Lane): StreetScene = {
    val newStreets = streets.map { street: Street =>
      street.updateLanes(func)
    }
    this.copy(streets = newStreets)
  }

  def renderables: List[RenderedVehicle] =
    for {
      street  <- streets
      lane    <- street.lanes
      vehicle <- lane.vehicles
    } yield RenderedVehicle(
      vehicle.spatial.r,
      lane.direction,
      vehicle.width,
      vehicle.height,
      vehicle.uuid
    )

  def roadShapes: List[RoadShape] =
    for {
      street <- streets
      lane   <- street.lanes
    } yield RoadStrip(lane.beginning.r, lane.end.r, RoadShape.LaneWidth)

  /**
    * The original SpatialCanvas arithmetic, fudge factors and all, so the straight road
    * keeps its familiar scale.
    *
    * The road itself sits at y = 0, and a car is drawn centred on its position, so the
    * strip needs half a car of room above the road or the top half gets clipped away.
    */
  def project(pixelWidth: Int): Projection = {
    val pixelHeight = pixelWidth / 8
    Projection(
      pixelWidth,
      pixelHeight,
      canvasDimensions._2.toMeters / (pixelWidth * 3),
      canvasDimensions._1.toMeters / (pixelHeight * 5),
      (0.0, pixelHeight / 2.0)
    )
  }

  val sourceTiming: Option[Time] =
    streets.flatMap(street => street.lanes.map(lane => lane.vehicleSource.spacingInTime)).headOption
}

/**
  * A fixed population of cars going round and round. Nothing enters, nothing leaves, so
  * whatever you see is something the traffic did to itself.
  */
case class RingScene(
  lane: TrackLane,
  t: Time,
  dt: Time
) extends Scene {

  val speedLimit: Velocity = lane.speedLimit

  def updateWithSpeedLimit(speedLimit: Velocity): RingScene =
    copy(lane = TrackLane.update(lane.copy(speedLimit = speedLimit), dt), t = t + dt)

  def renderables: List[RenderedVehicle] =
    lane.vehicles.map { vehicle =>
      RenderedVehicle(
        vehicle.piloted.spatial.r,
        lane.headingOf(vehicle),
        vehicle.piloted.width,
        vehicle.piloted.height,
        vehicle.piloted.uuid
      )
    }

  def roadShapes: List[RoadShape] = List(RoadShape.of(lane.path))

  def project(pixelWidth: Int): Projection =
    Projection.fitting(
      lane.path.extent,
      pixelWidth,
      (pixelWidth * RingScene.CanvasAspect).toInt,
      RingScene.Padding
    )

  val sourceTiming: Option[Time] = None

  /** Brake one car in the middle of the pack, so there are cars either side to watch. */
  def brakeOneCar(): RingScene =
    if (lane.vehicles.isEmpty) this
    else copy(lane = lane.withVehicleSlowedTo(lane.vehicles.size / 2, MetersPerSecond(0)))
}

object RingScene {

  /** How tall the ring's canvas is relative to its width. */
  val CanvasAspect: Double = 0.62

  /** Breathing room around the ring, so cars aren't clipped by the edge of the canvas. */
  val Padding: Double = 1.12
}
