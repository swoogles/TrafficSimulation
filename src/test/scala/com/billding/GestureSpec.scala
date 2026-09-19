package com.billding

import com.billding.svgRendering.Camera
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import squants.motion.Distance
import squants.space.Meters
import squants.QuantityVector

/**
  * [[Camera.followingTouches]] is the whole of I3's touch arithmetic: given two fingers'
  * screen positions a moment ago and now, and the camera they were driving, what camera do
  * they drive to next. It is pure and DOM-free on purpose - a `TouchEvent` never appears here,
  * only plain coordinate tuples - so the gesture itself is checked without a browser, and the
  * DOM listeners in `Client`/`Model` are left with nothing to get wrong except turning a real
  * touch event into these eight numbers.
  */
class GestureSpec extends AnyFlatSpec with Matchers {

  private val PixelWidth = 360
  private val PixelHeight = 640

  private val startCenter: QuantityVector[Distance] =
    QuantityVector[Distance](Meters(100), Meters(-50), Meters(0))

  private def metersOf(vector: QuantityVector[Distance]): (Double, Double) =
    (vector.coordinates.head.toMeters, vector.coordinates(1).toMeters)

  "followingTouches" should
  "leave the scale unchanged and move the center by the drag when both fingers move the same way" in {
    val before = Camera(startCenter, 2.0)
    val firstBefore = (100.0, 300.0)
    val secondBefore = (260.0, 300.0)
    val dx = 30.0
    val dy = -15.0
    val firstAfter = (firstBefore._1 + dx, firstBefore._2 + dy)
    val secondAfter = (secondBefore._1 + dx, secondBefore._2 + dy)

    val after = Camera.followingTouches(
      before,
      firstBefore,
      secondBefore,
      firstAfter,
      secondAfter,
      PixelWidth,
      PixelHeight
    )

    after.metersPerPixel shouldBe before.metersPerPixel +- 1e-9

    // A drag the same on both fingers is a pure pan: the camera moves the opposite way in
    // world units, scaled by how many metres a pixel covers - true of the drag alone, not of
    // where on screen the fingers happened to be.
    val (cx, cy) = metersOf(after.center)
    val (bx, by) = metersOf(before.center)
    cx shouldBe (bx - dx * before.metersPerPixel) +- 1e-9
    cy shouldBe (by - dy * before.metersPerPixel) +- 1e-9
  }

  it should "double the scale when the fingers spread to twice their starting distance apart" in {
    val before = Camera(startCenter, 1.0)
    val midX = 180.0
    val midY = 320.0
    val halfSpanBefore = 40.0

    val firstBefore = (midX - halfSpanBefore, midY)
    val secondBefore = (midX + halfSpanBefore, midY)
    val firstAfter = (midX - halfSpanBefore * 2, midY)
    val secondAfter = (midX + halfSpanBefore * 2, midY)

    val after = Camera.followingTouches(
      before,
      firstBefore,
      secondBefore,
      firstAfter,
      secondAfter,
      PixelWidth,
      PixelHeight
    )

    // Twice the screen distance for the same world distance is half as many metres per pixel.
    after.metersPerPixel shouldBe before.metersPerPixel / 2.0 +- 1e-9
  }

  it should
  "keep the world point under each finger exactly under that finger afterwards, at several zoom levels" in {
    Seq(0.05, 1.0, 12.0).foreach { metersPerPixel =>
      val before = Camera(startCenter, metersPerPixel)
      val firstBefore = (90.0, 500.0)
      val secondBefore = (300.0, 120.0)
      val worldFirst = before.worldAt(firstBefore._1, firstBefore._2, PixelWidth, PixelHeight)
      val worldSecond = before.worldAt(secondBefore._1, secondBefore._2, PixelWidth, PixelHeight)

      // A two-finger move this camera can undo exactly is a scale-and-translate of the touch
      // pair with no change of direction between the two fingers - a camera with one scale and
      // no rotation (by design; see Camera's own doc) has no way to also undo a pair that
      // rotates, so the "after" pair here is deliberately kept along the same direction as the
      // "before" one, just at a different distance, angle held fixed and only the span and
      // position changing.
      val direction = (secondBefore._1 - firstBefore._1, secondBefore._2 - firstBefore._2)
      val scale = 0.75
      val firstAfter = (60.0, 400.0)
      val secondAfter = (firstAfter._1 + direction._1 * scale, firstAfter._2 + direction._2 * scale)

      val after = Camera.followingTouches(
        before,
        firstBefore,
        secondBefore,
        firstAfter,
        secondAfter,
        PixelWidth,
        PixelHeight
      )

      val projection = after.projection(PixelWidth, PixelHeight)
      projection.xOf(worldFirst) shouldBe firstAfter._1 +- 1e-6
      projection.yOf(worldFirst) shouldBe firstAfter._2 +- 1e-6
      projection.xOf(worldSecond) shouldBe secondAfter._1 +- 1e-6
      projection.yOf(worldSecond) shouldBe secondAfter._2 +- 1e-6
    }
  }

  it should "hold the scale steady rather than let it diverge when the fingers land on (almost) the same point" in {
    val before = Camera(startCenter, 3.5)
    val firstBefore = (100.0, 100.0)
    val secondBefore = (200.0, 100.0)
    val firstAfter = (150.0, 150.0)
    val secondAfter = (150.0000001, 150.0) // collapsed to (almost) the same screen point

    val after = Camera.followingTouches(
      before,
      firstBefore,
      secondBefore,
      firstAfter,
      secondAfter,
      PixelWidth,
      PixelHeight
    )

    after.metersPerPixel shouldBe before.metersPerPixel
    after.metersPerPixel.isNaN shouldBe false
    after.metersPerPixel.isInfinite shouldBe false
  }

  it should
  "not jump when a gesture is picked back up from wherever the fingers currently are after a third finger came and went" in {
    // I3's ownership rule is touch count alone: while a third finger is down the camera holds
    // still (Model never calls this function for that event), so by the time it comes back to
    // two fingers, "before" is unchanged and the two touch positions are simply wherever those
    // fingers now are - not anything remembered from before the interruption.
    val cameraAtResume = Camera(startCenter, 2.0)
    val resumedFirst = (95.0, 210.0)
    val resumedSecond = (270.0, 195.0)

    // Kept along the same direction as the resumed pair, per the previous test's note - only
    // the span and position differ.
    val direction = (resumedSecond._1 - resumedFirst._1, resumedSecond._2 - resumedFirst._2)
    val scale = 1.15
    val firstAfter = (110.0, 205.0)
    val secondAfter = (firstAfter._1 + direction._1 * scale, firstAfter._2 + direction._2 * scale)

    val after = Camera.followingTouches(
      cameraAtResume,
      resumedFirst,
      resumedSecond,
      firstAfter,
      secondAfter,
      PixelWidth,
      PixelHeight
    )

    // The fingers' own world points at the moment the gesture resumed land exactly back under
    // them - nothing about the interruption shows up as a jump.
    val worldFirst = cameraAtResume.worldAt(resumedFirst._1, resumedFirst._2, PixelWidth, PixelHeight)
    val worldSecond = cameraAtResume.worldAt(resumedSecond._1, resumedSecond._2, PixelWidth, PixelHeight)
    val projection = after.projection(PixelWidth, PixelHeight)
    projection.xOf(worldFirst) shouldBe firstAfter._1 +- 1e-6
    projection.yOf(worldFirst) shouldBe firstAfter._2 +- 1e-6
    projection.xOf(worldSecond) shouldBe secondAfter._1 +- 1e-6
    projection.yOf(worldSecond) shouldBe secondAfter._2 +- 1e-6
  }

  it should
  "chain across several small moves the way Model.touchMoved actually calls it, keeping a still finger fixed throughout" in {
    val before = Camera(startCenter, 1.5)
    val anchor = (200.0, 300.0) // one finger stays put the whole gesture
    val worldAnchor = before.worldAt(anchor._1, anchor._2, PixelWidth, PixelHeight)

    // The moving finger walks along one fixed ray from the anchor: each step is still a pure
    // scale-and-translate of the pair (same direction throughout, only the distance from the
    // anchor changing), which is the class of gesture this rotation-free camera can track
    // exactly across any number of steps - see the direction note two tests up.
    val direction = (-1.0, 1.2)
    def along(r: Double): (Double, Double) = (anchor._1 + direction._1 * r, anchor._2 + direction._2 * r)

    var camera = before
    var moving = along(140.0)
    Seq(120.0, 100.0, 90.0).foreach { r =>
      val next = along(r)
      camera = Camera.followingTouches(camera, anchor, moving, anchor, next, PixelWidth, PixelHeight)
      moving = next
    }

    val projection = camera.projection(PixelWidth, PixelHeight)
    projection.xOf(worldAnchor) shouldBe anchor._1 +- 1e-6
    projection.yOf(worldAnchor) shouldBe anchor._2 +- 1e-6
  }
}
