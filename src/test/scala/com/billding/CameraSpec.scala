package com.billding

import com.billding.physics.PathExtent
import com.billding.svgRendering.{Camera, Projection}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import squants.motion.Distance
import squants.space.Meters
import squants.QuantityVector

class CameraSpec extends AnyFlatSpec with Matchers {

  private val PixelWidth = 360
  private val PixelHeight = 640

  private val center: QuantityVector[Distance] =
    QuantityVector[Distance](Meters(120), Meters(-40), Meters(0))

  private def metersOf(vector: QuantityVector[Distance]): (Double, Double) =
    (vector.coordinates.head.toMeters, vector.coordinates(1).toMeters)

  // Zoomed in (fractions of a meter per pixel), a working-default street scale, and zoomed
  // far out - an overview map per W7/W9 - so the round trip is checked close, medium and wide.
  private val zoomLevels = Seq(0.05, 1.0, 25.0)

  zoomLevels.foreach { metersPerPixel =>
    s"A camera at $metersPerPixel meters per pixel" should
    "recover a world point exactly when the screen coordinate isn't rounded" in {
      val camera = Camera(center, metersPerPixel)
      val worldPoint =
        QuantityVector[Distance](Meters(center.coordinates.head.toMeters + 37.0), Meters(center.coordinates(1).toMeters - 12.0), Meters(0))

      val projection = camera.projection(PixelWidth, PixelHeight)
      val screenX = projection.xOf(worldPoint)
      val screenY = projection.yOf(worldPoint)

      val recovered = camera.worldAt(screenX, screenY, PixelWidth, PixelHeight)
      val (rx, ry) = metersOf(recovered)
      val (wx, wy) = metersOf(worldPoint)

      rx shouldBe wx +- 1e-9
      ry shouldBe wy +- 1e-9
    }

    it should "recover a world point within one pixel once the screen coordinate is rounded to a real pixel" in {
      val camera = Camera(center, metersPerPixel)
      val worldPoint =
        QuantityVector[Distance](Meters(center.coordinates.head.toMeters - 8.0), Meters(center.coordinates(1).toMeters + 55.0), Meters(0))

      val projection = camera.projection(PixelWidth, PixelHeight)
      val roundedScreenX = math.round(projection.xOf(worldPoint)).toDouble
      val roundedScreenY = math.round(projection.yOf(worldPoint)).toDouble

      val recovered = camera.worldAt(roundedScreenX, roundedScreenY, PixelWidth, PixelHeight)
      val (rx, ry) = metersOf(recovered)
      val (wx, wy) = metersOf(worldPoint)

      // Half a pixel's worth of rounding on the way to the screen, undone by an exact inverse
      // on the way back - the whole round trip stays within one pixel's width in world units.
      rx shouldBe wx +- metersPerPixel
      ry shouldBe wy +- metersPerPixel
    }

    it should "keep the camera's own third coordinate on the recovered point" in {
      val camera = Camera(center, metersPerPixel)
      val recovered = camera.worldAt(PixelWidth / 2.0, PixelHeight / 2.0, PixelWidth, PixelHeight)
      recovered.coordinates(2) shouldBe center.coordinates(2)
    }
  }

  "A camera's projection" should "scale both axes equally, unlike a StreetScene's" in {
    val camera = Camera(center, 2.0)
    val projection = camera.projection(PixelWidth, PixelHeight)
    projection.metersPerPixelAcross shouldBe projection.metersPerPixelDown
  }

  it should "center the camera's own position in the middle of the canvas" in {
    val camera = Camera(center, 3.0)
    val projection = camera.projection(PixelWidth, PixelHeight)
    projection.xOf(center) shouldBe PixelWidth / 2.0 +- 1e-9
    projection.yOf(center) shouldBe PixelHeight / 2.0 +- 1e-9
  }

  "Camera.fitting" should "produce a camera whose projection matches Projection.fitting" in {
    val extent = PathExtent(
      QuantityVector[Distance](Meters(500), Meters(-200), Meters(0)),
      Meters(300),
      Meters(150)
    )
    val padding = 1.12

    val expected = Projection.fitting(extent, PixelWidth, PixelHeight, padding)
    val camera = Camera.fitting(extent, PixelWidth, PixelHeight, padding)
    val actual = camera.projection(PixelWidth, PixelHeight)

    actual.pixelWidth shouldBe expected.pixelWidth
    actual.pixelHeight shouldBe expected.pixelHeight
    actual.metersPerPixelAcross shouldBe expected.metersPerPixelAcross +- 1e-9
    actual.metersPerPixelDown shouldBe expected.metersPerPixelDown +- 1e-9
    actual.worldOriginInPixels._1 shouldBe expected.worldOriginInPixels._1 +- 1e-9
    actual.worldOriginInPixels._2 shouldBe expected.worldOriginInPixels._2 +- 1e-9
  }

  it should "match Projection.fitting for a taller-than-wide extent too" in {
    val extent = PathExtent(
      QuantityVector[Distance](Meters(-40), Meters(900), Meters(0)),
      Meters(50),
      Meters(400)
    )
    val padding = 1.2

    val expected = Projection.fitting(extent, PixelWidth, PixelHeight, padding)
    val camera = Camera.fitting(extent, PixelWidth, PixelHeight, padding)
    val actual = camera.projection(PixelWidth, PixelHeight)

    actual.metersPerPixelAcross shouldBe expected.metersPerPixelAcross +- 1e-9
    actual.worldOriginInPixels._1 shouldBe expected.worldOriginInPixels._1 +- 1e-9
    actual.worldOriginInPixels._2 shouldBe expected.worldOriginInPixels._2 +- 1e-9
  }
}
