package com.billding

import com.billding.physics.RingPath
import com.billding.svgRendering.{RoadRing, RoadShape, RoadStrip}
import com.billding.traffic.{RingScene, StreetScene, TrackLane, TrackRoad}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import squants.motion.KilometersPerHour
import squants.space.Meters
import squants.time.{Milliseconds, Seconds}

class SceneRenderingSpec extends AnyFlatSpec with Matchers {

  private val CanvasWidth = 800
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

  private val projection = ringScene.project(CanvasWidth)

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
    val road = ringScene.roadShapes match {
      case List(ring: RoadRing) => ring
      case other                => fail(s"expected a single ring of road, got $other")
    }

    road.center shouldBe RingPath.ORIGIN
    road.radius.toMeters shouldBe (400 / (2 * math.Pi)) +- 1e-9
    road.width.toMeters shouldBe 6.0 // wider than the 4m cars

    val roadRadiusInPixels = projection.across(road.radius)
    pixelPositions.map(pixelRadiusOf).foreach { carRadius =>
      carRadius shouldBe roadRadiusInPixels +- 1e-9
    }
  }

  it should "keep the road inside the canvas, edge lines and all" in {
    val road = ringScene.roadShapes.head.asInstanceOf[RoadRing]
    val outerEdge = projection.across(road.radius + road.width / 2.0)

    // Measured from the centre of the canvas, which is where the ring's centre lands.
    outerEdge should be < math.min(projection.pixelWidth, projection.pixelHeight) / 2.0
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
    val streetProjection = scenes.scene1.scene.project(CanvasWidth)

    streetProjection.pixelHeight shouldBe CanvasWidth / 8
    streetProjection.metersPerPixelAcross shouldBe 500.0 / (800 * 3) +- 1e-12
    streetProjection.metersPerPixelDown shouldBe 250.0 / (100 * 5) +- 1e-12
    streetProjection.worldOriginInPixels shouldBe (0.0, CanvasWidth / 8 / 2.0)
  }
}
