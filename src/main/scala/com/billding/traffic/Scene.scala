package com.billding.traffic

import com.billding.network.{Boundary, NetworkIndex, NetworkTick, NetworkTraffic, RoadNetwork, Source}
import com.billding.physics.{PathExtent, RingPath}
import com.billding.svgRendering.{
  CountingLine,
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
import squants.time.Seconds
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

  /**
    * Cars per kilometre of lane, for scenes whose population is yours to set.
    *
    * A street's population is whatever its sources have put on it and its exits have not yet
    * taken off, so there is nothing to set - the control for that road is how often cars
    * arrive. A ring holds exactly the cars you give it, which makes this the one dial that
    * decides what the traffic is physically able to do.
    */
  def density: Option[Double]

  /**
    * How many cars have finished the course, counting from when the scene was laid out.
    *
    * What finishing means is the one thing the two shapes of road genuinely disagree about. A
    * street has an end, so a car finishes by driving off it and is gone. A ring has no end, so
    * a lap is the nearest thing to finishing there is, and the same car finishes over and over.
    * Both are the same question asked of the road you are looking at - how much traffic has
    * this road got through - which is why they are one number rather than two.
    */
  def completed: Int
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

  val density: Option[Double] = None

  def completed: Int = streets.flatMap(_.lanes).map(_.completed).sum
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
    * One band of tarmac across all the lanes, with a dashed line on each interior boundary and
    * the counting line painted across the lot.
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
      tarmac :: (dividers :+ countingLine(rings.head, rings.last))
    }
  }

  /**
    * The counting line, from the outer kerb to the inner one.
    *
    * Every lane counts at the same arc length, and on a ring an arc length is an angle - the
    * same angle whatever the radius - so the several lines the lanes each keep are one line
    * across the road, and are worth drawing as one. The ends are found by asking a ring of the
    * right radius where that arc length falls, which is also the only statement of why this
    * works: change where a lane counts and both the count and the paint move together.
    */
  private def countingLine(outer: RingPath, inner: RingPath): CountingLine = {
    val half = road.laneWidth / 2.0
    def edgeAt(radius: Length) =
      RingPath(outer.center, radius).pointAt(TrackLane.CountingLine)

    CountingLine(
      edgeAt(outer.radius + half),
      edgeAt(inner.radius - half),
      RoadShape.CountingLineWidth
    )
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

  val density: Option[Double] = Some(road.density)

  def completed: Int = road.completed

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

/**
  * A lane graph with traffic on it - a street's cars entering and leaving, but over a
  * network of sections and movements rather than a single straight road.
  *
  * `index` is carried alongside `network` rather than rebuilt every tick: [[NetworkIndex]]
  * groups every movement by its endpoints once, and [[com.billding.network.NetworkTick.advance]]
  * (via [[com.billding.network.Lookahead]]) leans on that grouping once per vehicle per tick.
  *
  * `completed` is a running total kept here rather than asked of the traffic, because
  * [[com.billding.network.NetworkTick.advance]] only ever reports the departures of the one
  * tick it just ran - the same way `Lane.completed` accumulates a count `TrackLane.update`
  * has no memory of itself.
  *
  * `sources` defaults to empty, the way a network scene behaved before card E1's [[Source]]
  * existed at all: nothing arrives, the seven cars a demo seeds it with eventually drive off
  * the far end, and the road sits empty - correct, and exactly the "unwatchable" scene E1
  * closes the gap on for any scene that opts in by naming at least one section to arrive at.
  */
case class NetworkScene(
  network: RoadNetwork,
  index: NetworkIndex,
  traffic: NetworkTraffic,
  t: Time,
  dt: Time,
  speedLimit: Velocity,
  sources: List[Source] = Nil,
  completed: Int = 0
) extends Scene {

  /**
    * Advance every source by one tick before the network's own physics runs, the same order
    * [[Boundary]]'s own spec drives a source in - admit or queue this tick's arrival first,
    * then let [[com.billding.network.NetworkTick.advance]] move everybody (newly admitted
    * cars included) so an admitted vehicle is never left sitting untouched at the boundary
    * for a full extra tick.
    *
    * Each `Source`'s returned state is threaded through the fold and kept in the copy below -
    * the whole point of a `Source` carrying its own `seed` is that it is a value carried
    * forward tick over tick, not a fresh generator started from scratch each time.
    */
  def updateWithSpeedLimit(speedLimit: Velocity): NetworkScene = {
    val (nextSources, trafficAfterArrivals) =
      sources.foldLeft((List.empty[Source], traffic)) {
        case ((admittedSoFar, currentTraffic), source) =>
          val (nextSource, nextTraffic, _) = Boundary.tick(source, currentTraffic, index, dt)
          (admittedSoFar :+ nextSource, nextTraffic)
      }
    val result = NetworkTick.advance(trafficAfterArrivals, index, dt)
    copy(
      sources = nextSources,
      traffic = result.traffic,
      t = t + dt,
      speedLimit = speedLimit,
      completed = completed + result.departures.size
    )
  }

  /**
    * Every vehicle placed on its section's path, the same `pointAt`/`normalAt` composition
    * [[TrackVehicle.placedOn]] uses to turn an along-the-road position into a point in space.
    *
    * A vehicle whose section the network no longer knows about (it shouldn't happen, but
    * `section` is a lookup rather than a reference) is simply left off the canvas rather than
    * crashing the render.
    */
  def renderables: List[RenderedVehicle] =
    traffic.all.flatMap { vehicle =>
      network.section(vehicle.section).map { section =>
        val path = section.path
        val position = path.pointAt(vehicle.s) + path.normalAt(vehicle.s).map { component: Double =>
          vehicle.lateral * component
        }
        RenderedVehicle(
          position,
          path.headingAt(vehicle.s),
          vehicle.piloted.width,
          vehicle.piloted.height,
          vehicle.piloted.uuid,
          Motion.of(vehicle.speed, vehicle.acceleration)
        )
      }
    }

  /** One shape per section - `RoadShape.of` already knows straight, arc and ring paths. */
  def roadShapes: List[RoadShape] =
    network.sections.values.map(section => RoadShape.of(section.path, section.width)).toList

  /**
    * Fit every section's path into the box the page has room for, at one scale for both
    * axes - never the stretched-axis treatment `StreetScene` allows itself, because an arc
    * drawn with a single radius is only correct under an even scale.
    *
    * Letterboxed the same way [[RingScene.project]] is: a network only ever takes as much
    * height as its own proportions can use, so the controls stay tucked up underneath rather
    * than pushed down past a band of whitespace a tall, narrow network would otherwise leave.
    */
  def project(pixelWidth: Int, availableHeight: Int): Projection =
    PathExtent.covering(network.sections.values.map(_.path)) match {
      case Some(shape) =>
        val tallestWorthHaving = pixelWidth * (shape.height / shape.width)
        Projection.fitting(
          shape,
          pixelWidth,
          math.max(1, math.min(availableHeight, tallestWorthHaving.toInt)),
          NetworkScene.Padding
        )
      case None =>
        // No sections at all - nothing to fit, so fall back to a plain 1:1 canvas rather
        // than dividing by an extent that doesn't exist.
        Projection(pixelWidth, math.max(1, availableHeight), 1.0, 1.0, (pixelWidth / 2.0, availableHeight / 2.0))
    }

  /**
    * The mean time between arrivals at the first source that has one, in the same "spacing in
    * time" units [[StreetScene.sourceTiming]] reports - the reading the speed-control panel's
    * `carTiming` slider already expects, whichever shape of road it is looking at.
    *
    * A [[Source]] is a Poisson process, so there is no single fixed spacing the way a street's
    * `VehicleSourceImpl` has one - `1 / meanRate` is the mean of that process, the honest
    * single number to show for a rate that is actually randomised tick to tick.
    *
    * `None` for a source-free scene (the default), which is exactly the old behaviour: a
    * network with nothing arriving has no timing to report, same as before E1.
    */
  val sourceTiming: Option[Time] =
    sources.headOption.map(source => Seconds(1.0 / source.meanRate.toHertz))

  // Sink-set density isn't modelled yet - a network scene's population is whatever its
  // sources (if any) have put on it, the same reasoning `Scene.density`'s doc gives for why a
  // street reports none either.
  val density: Option[Double] = None
}

object NetworkScene {

  /** Breathing room around the network, so cars aren't clipped by the edge of the canvas. */
  val Padding: Double = 1.12
}
