package com.billding

import com.billding.physics.RingPath
import com.billding.svgRendering.{CountingLine, RoadRing, RoadShape, RoadStrip}
import com.billding.traffic.{RingScene, StreetScene, TrackLane, TrackRoad}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import squants.motion.KilometersPerHour
import squants.space.Meters
import squants.time.{Milliseconds, Seconds}

class SceneRenderingSpec extends AnyFlatSpec with Matchers {

  private val CanvasWidth = 800

  /** A window with plenty of room, so these tests are about the scene and not about the page. */
  private val CanvasHeight = 800

  private val speedLimit = KilometersPerHour(45)

  private val ringScene = RingScene(
    TrackRoad(
      List(
        TrackLane
          .evenlySpaced(RingPath.ofCircumference(Meters(400)), 8, speedLimit, speedLimit)
      )
    ),
    Seconds(0),
    Milliseconds(100)
  )

  private val projection = ringScene.project(CanvasWidth, CanvasHeight)

  private def pixelRadiusOf(position: (Double, Double)): Double = {
    val (x, y) = position
    val centerX = projection.pixelWidth / 2.0
    val centerY = projection.pixelHeight / 2.0
    math.hypot(x - centerX, y - centerY)
  }

  private val pixelPositions: List[(Double, Double)] =
    ringScene.renderables.map(car => (projection.xOf(car.position), projection.yOf(car.position)))

  /**
    * The scale has to be the same on both axes or the ring draws as an ellipse, which is
    * what the straight road's separate width and height divisors would have done to it.
    */
  "A ring scene" should "draw round rather than oval" in {
    projection.metersPerPixelAcross shouldBe projection.metersPerPixelDown

    val radii = pixelPositions.map(pixelRadiusOf)
    radii.foreach { radius =>
      radius shouldBe radii.head +- 1e-9
    }
  }

  it should "fit the whole loop on the canvas" in
  pixelPositions.foreach {
    case (x, y) =>
      x should ((be >= 0.0).and(be <= projection.pixelWidth.toDouble))
      y should ((be >= 0.0).and(be <= projection.pixelHeight.toDouble))
  }

  it should "point each car along the road rather than all one way" in {
    val headings = ringScene.renderables.map(_.headingInDegrees)

    // Arc length 0 sits at 3 o'clock heading round the loop, which is down the screen.
    headings.head shouldBe 90.0 +- 1e-9
    headings.distinct.size shouldBe headings.size
  }

  it should "size cars by the same scale as the road" in {
    val car = ringScene.renderables.head
    val carLengthInPixels = projection.across(car.width)

    // 8m car against a 400m loop, so it should cover about 2% of the circumference.
    val loopInPixels = projection.across(Meters(400))
    (carLengthInPixels / loopInPixels) shouldBe 0.02 +- 0.001
  }

  /**
    * The cars should sit on the tarmac rather than beside it: they drive along the middle
    * of the road, so their distance from the centre has to be the road's own radius.
    */
  it should "lay a road down under the traffic" in {
    // The tarmac comes first and the counting line is painted on top of it.
    val road = ringScene.roadShapes match {
      case List(ring: RoadRing, _: CountingLine) => ring
      case other                                 => fail(s"expected a ring of road, got $other")
    }

    road.center shouldBe RingPath.ORIGIN
    road.radius.toMeters shouldBe (400 / (2 * math.Pi)) +- 1e-9
    road.width.toMeters shouldBe 6.0 // wider than the 4m cars

    val roadRadiusInPixels = projection.across(road.radius)
    pixelPositions.map(pixelRadiusOf).foreach { carRadius =>
      carRadius shouldBe roadRadiusInPixels +- 1e-9
    }
  }

  /**
    * The counting line has to cross the road the cars are counted at, which is the one thing
    * about it that can be silently wrong: a stripe painted at the wrong angle is a perfectly
    * convincing marker for a place nothing is happening at.
    */
  it should "paint the counting line across the road, where the lane counts" in {
    val line = ringScene.roadShapes.collect { case line: CountingLine => line } match {
      case List(only) => only
      case other      => fail(s"expected one counting line, got $other")
    }

    val road = ringScene.roadShapes.head.asInstanceOf[RoadRing]

    // Arc length nought is 3 o'clock, so both ends sit level with the centre, out to one side.
    line.from.coordinates(1).toMeters shouldBe 0.0 +- 1e-9
    line.to.coordinates(1).toMeters shouldBe 0.0 +- 1e-9

    // From one kerb to the other, so it spans the tarmac rather than a slice of it.
    line.from.coordinates.head shouldBe (road.radius + road.width / 2.0)
    line.to.coordinates.head shouldBe (road.radius - road.width / 2.0)
  }

  it should "keep the road inside the canvas, edge lines and all" in {
    val road = ringScene.roadShapes.head.asInstanceOf[RoadRing]
    val outerEdge = projection.across(road.radius + road.width / 2.0)

    // Measured from the centre of the canvas, which is where the ring's centre lands.
    outerEdge should be < math.min(projection.pixelWidth, projection.pixelHeight) / 2.0
  }

  /**
    * The canvas used to be a fixed fraction of its own width, which is a letterbox whatever
    * shape the screen is. On anything wide that made the drawing taller than the window and
    * cut the bottom off the ring, which is the bug these two are really about: how tall the
    * drawing is has to be settled against how tall the page is.
    */
  "A ring scene on a short, wide page" should "fit the room it was given rather than overflow it" in {
    val wideAndShort = ringScene.project(1400, 500)

    wideAndShort.pixelHeight shouldBe 500

    // The whole road, edge lines and all, inside the box top to bottom.
    val road = ringScene.roadShapes.head.asInstanceOf[RoadRing]
    val outerEdge = wideAndShort.across(road.radius + road.width / 2.0)
    outerEdge should be < wideAndShort.pixelHeight / 2.0
  }

  /**
    * The other half of the same idea. A round road in a tall box can only ever be as big as
    * the box is wide, so taking the rest of the height would be claiming a band of whitespace
    * and pushing the controls down past it. A road that could use the height - an oval stood
    * on its end - would get it, because this is asked of the road's own proportions.
    */
  "A ring scene on a phone held upright" should "take only the height a round road can fill" in {
    val tallAndNarrow = ringScene.project(390, 700)

    tallAndNarrow.pixelWidth shouldBe 390
    tallAndNarrow.pixelHeight shouldBe 390
  }

  it should "still be bigger than the old letterbox made it" in {
    val phone = ringScene.project(390, 700)
    // The canvas was its own width times 0.62, whatever the page had room for.
    val asTheLetterboxHadIt = ringScene.project(390, (390 * 0.62).toInt)

    val roadIn = (projection: com.billding.svgRendering.Projection) =>
      projection.across(ringScene.roadShapes.head.asInstanceOf[RoadRing].radius)

    roadIn(phone) should be > roadIn(asTheLetterboxHadIt)
  }

  "A street scene" should "give every lane its own strip of road" in {
    val scenes = new SampleSceneCreation(
      com.billding.physics.Spatial((0.5, 0, 0, squants.space.Kilometers))
    )(Milliseconds(100))
    val street = scenes.scene1.scene.asInstanceOf[StreetScene]
    val lanes = street.streets.flatMap(_.lanes)

    street.roadShapes.size shouldBe lanes.size

    street.roadShapes.zip(lanes).foreach {
      case (strip: RoadStrip, lane) =>
        strip.from shouldBe lane.beginning.r
        strip.to shouldBe lane.end.r
        strip.width shouldBe RoadShape.LaneWidth
      case (other, _) => fail(s"a straight lane should be a strip, not $other")
    }
  }

  /**
    * The straight road's projection is the old SpatialCanvas arithmetic moved, not changed.
    * These are the numbers it produced before, so the refactor can't quietly rescale it.
    * The one deliberate difference is vertical: cars are drawn centred on their position
    * now, so the road drops half a strip down to keep them on the canvas.
    */
  "A street scene" should "keep its old scale, with room above the road" in {
    val scenes = new SampleSceneCreation(
      com.billding.physics.Spatial((0.5, 0, 0, squants.space.Kilometers))
    )(Milliseconds(100))
    val streetProjection = scenes.scene1.scene.project(CanvasWidth, CanvasHeight)

    streetProjection.pixelHeight shouldBe CanvasWidth / 8
    streetProjection.metersPerPixelAcross shouldBe 500.0 / (800 * 3) +- 1e-12
    streetProjection.metersPerPixelDown shouldBe 250.0 / (100 * 5) +- 1e-12
    streetProjection.worldOriginInPixels shouldBe (0.0, CanvasWidth / 8 / 2.0)
  }
}
