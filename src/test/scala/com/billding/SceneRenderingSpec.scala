package com.billding

import com.billding.physics.RingPath
import com.billding.traffic.{RingScene, TrackLane}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import squants.motion.KilometersPerHour
import squants.space.Meters
import squants.time.{Milliseconds, Seconds}

class SceneRenderingSpec extends AnyFlatSpec with Matchers {

  private val CanvasWidth = 800
  private val speedLimit = KilometersPerHour(45)

  private val ringScene = RingScene(
    TrackLane
      .evenlySpaced(RingPath.ofCircumference(Meters(400)), 8, speedLimit, speedLimit),
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
