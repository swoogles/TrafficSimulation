package com.billding.traffic

import com.billding.physics.RingPath
import com.billding.svgRendering.{
  DividerRing,
  LaneChangeSignal,
  Motion,
  Projection,
  RenderedVehicle,
  RoadRing,
  RoadShape,
  RoadStrip
}
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

  /**
    * How this scene wants to be laid out in the box the page can give it.
    *
    * The height is what the page has room for, not what the scene must take: a scene is free
    * to use less of it, and says so by returning a projection shorter than it was offered.
    */
  def project(pixelWidth: Int, pixelHeight: Int): Projection

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

  /**
    * The straight road recomputes what each driver is doing rather than remembering it, since
    * a Lane keeps its accelerations only long enough to apply them. It is the same function
    * the update uses, so the colours agree with the motion they are describing.
    */
  def renderables: List[RenderedVehicle] =
    for {
      street                  <- streets
      lane                    <- street.lanes
      (vehicle, acceleration) <- lane.vehicles.zip(Lane.responsesInOneLanePrep(lane))
    } yield RenderedVehicle(
      vehicle.spatial.r,
      lane.direction,
      vehicle.width,
      vehicle.height,
      vehicle.uuid,
      Motion.of(vehicle.spatial.v.magnitude, acceleration)
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
    *
    * Whatever height the page offers is ignored: a straight road is a letterbox strip, and
    * stretching it down the screen would only put more empty tarmac either side of it.
    */
  def project(pixelWidth: Int, availableHeight: Int): Projection = {
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
  road: TrackRoad,
  t: Time,
  dt: Time
) extends Scene {

  val speedLimit: Velocity = road.speedLimit

  def updateWithSpeedLimit(speedLimit: Velocity): RingScene =
    copy(road = TrackRoad.update(road.withSpeedLimit(speedLimit), dt), t = t + dt)

  def renderables: List[RenderedVehicle] =
    for {
      (lane, index) <- road.lanes.zipWithIndex
      vehicle       <- lane.vehicles
    } yield RenderedVehicle(
      vehicle.piloted.spatial.r,
      lane.headingOf(vehicle),
      vehicle.piloted.width,
      vehicle.piloted.height,
      vehicle.piloted.uuid,
      Motion.of(vehicle.speed, vehicle.acceleration),
      // Lanes are numbered from the outside in, and the inside is the driver's left.
      vehicle.intent.map(intent => LaneChangeSignal(intent.to > index, intent.progress))
    )

  /**
    * One band of tarmac across all the lanes, with a dashed line on each interior boundary.
    *
    * Drawing a separate strip per lane would paint an edge line down every boundary, which
    * reads as two roads that happen to touch rather than as one road you may change lanes on.
    */
  def roadShapes: List[RoadShape] = {
    val rings = road.lanes.map(_.path).collect { case ring: RingPath => ring }
    if (rings.size != road.lanes.size) road.lanes.map(lane => RoadShape.of(lane.path))
    else {
      // The band spans every lane, so it is centred halfway between the outer and inner ones.
      val tarmac = RoadRing(
        rings.head.center,
        (rings.head.radius + rings.last.radius) / 2.0,
        road.laneWidth * road.lanes.size.toDouble
      )
      val dividers = rings.tail.map { ring =>
        DividerRing(ring.center, ring.radius + road.laneWidth / 2.0, road.laneWidth)
      }
      tarmac :: dividers
    }
  }

  /**
    * Fit the ring into the box the page has room for, and take no more of it than the road
    * can fill.
    *
    * The canvas used to be a fixed fraction of its own width, which is a letterbox whatever
    * the screen is: on a phone held upright it left the ring small with the screen empty
    * underneath, and on a wide desktop it made the canvas taller than the window, so the
    * bottom of the ring was cut off. Both of those are the same mistake - deciding how tall
    * the drawing is without reference to how tall the page is.
    *
    * Asking for only as much height as the road's own proportions can use keeps the controls
    * tucked up underneath rather than pushed down past a band of whitespace. A round ring
    * can't use a tall box, so on a phone it settles for a square one - but a road that is
    * taller than it is wide, an oval stood on its end, would take the height as soon as
    * there were one to take.
    */
  def project(pixelWidth: Int, availableHeight: Int): Projection = {
    val shape = road.extent
    val tallestWorthHaving = pixelWidth * (shape.height / shape.width)

    Projection.fitting(
      shape,
      pixelWidth,
      math.max(1, math.min(availableHeight, tallestWorthHaving.toInt)),
      RingScene.Padding
    )
  }

  val sourceTiming: Option[Time] = None

  /** Brake one car in the middle of the pack, so there are cars either side to watch. */
  def brakeOneCar(): RingScene = {
    val busiest = road.lanes.zipWithIndex.maxBy(_._1.vehicles.size)
    val (lane, index) = busiest
    if (lane.vehicles.isEmpty) this
    else
      copy(
        road = road.copy(
          lanes = road.lanes
            .updated(index, lane.withVehicleSlowedTo(lane.vehicles.size / 2, MetersPerSecond(0)))
        )
      )
  }

  /** The single errant lane change, on demand. */
  def forceLaneChange(): RingScene =
    copy(road = TrackRoad.forceLaneChange(road))
}

object RingScene {

  /** Breathing room around the ring, so cars aren't clipped by the edge of the canvas. */
  val Padding: Double = 1.12
}
