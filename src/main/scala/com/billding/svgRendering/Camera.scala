package com.billding.svgRendering

import com.billding.physics.PathExtent
import squants.motion.Distance
import squants.space.Meters
import squants.QuantityVector

/**
  * A viewport onto the world, kept beside a scene rather than inside it.
  *
  * [[com.billding.traffic.Scene.project]] refits its whole extent to the canvas every frame,
  * which is right for a scene with nothing to pan - a ring, a street - and wrong for a network
  * someone is looking around: refitting on every tick would recenter and rescale the view out
  * from under a person mid-pan, and a camera folded into the scene gets thrown away and rebuilt
  * every time the scene itself is replaced (a new tick, a loaded network). A `Camera` is state
  * that survives both, updated only by an explicit gesture - pan, zoom, follow, fit.
  *
  * Unlike [[Projection]], which lets a straight street stretch its two axes independently to
  * fill a letterboxed canvas, a camera never does: `metersPerPixel` is one number for both axes,
  * so a circular arc drawn under it is still a circle. That is what phase J's editor and any
  * touch interaction need - a single scale to convert a pinch gesture's screen distance into a
  * world distance, and a single inverse mapping (`worldAt`) to turn a tap into a world point.
  */
final case class Camera(center: QuantityVector[Distance], metersPerPixel: Double) {

  /** The world, laid onto a canvas of this size, `center` landing in the middle of it - the
    * same centering [[Projection.fitting]] does, but at a scale and a center that persist
    * across frames instead of being recomputed from whatever the scene currently contains.
    */
  def projection(pixelWidth: Int, pixelHeight: Int): Projection = {
    val origin = (
      pixelWidth / 2.0 - center.coordinates.head.toMeters / metersPerPixel,
      pixelHeight / 2.0 - center.coordinates(1).toMeters / metersPerPixel
    )
    Projection(pixelWidth, pixelHeight, metersPerPixel, metersPerPixel, origin)
  }

  /**
    * The world point under a screen position - the inverse of the `xOf`/`yOf` pair a
    * [[Projection]] exposes, which only go from world to screen. Every touch gesture (pan,
    * pinch, tap-to-place, tap-to-select) starts from a screen coordinate and needs to know what
    * in the world it landed on, which is exactly what neither `Projection` nor a scene's own
    * `project` can answer today.
    *
    * The plane a camera looks at is flat, so the returned point keeps this camera's own third
    * coordinate rather than inventing one - consistent with every other planar `QuantityVector`
    * in this codebase, which carries a zero third component rather than omitting it.
    */
  def worldAt(screenX: Double, screenY: Double, pixelWidth: Int, pixelHeight: Int): QuantityVector[Distance] = {
    val proj = projection(pixelWidth, pixelHeight)
    QuantityVector[Distance](
      Meters((screenX - proj.worldOriginInPixels._1) * proj.metersPerPixelAcross),
      Meters((screenY - proj.worldOriginInPixels._2) * proj.metersPerPixelDown),
      center.coordinates(2)
    )
  }
}

object Camera {

  /**
    * A camera that frames `extent` the way [[Projection.fitting]] does - same padding, same
    * "shorter of the two axes wins" scale - except the result is state that can be kept and
    * reused frame over frame rather than a `Projection` recomputed from the network every tick.
    *
    * Reusing `Projection.fitting`'s own scale (rather than recomputing the padding arithmetic
    * here) is what keeps the two in lockstep: change how fitting pads or scales, and a camera
    * built this way follows without a second place to update.
    */
  def fitting(extent: PathExtent, pixelWidth: Int, pixelHeight: Int, padding: Double): Camera = {
    val fitted = Projection.fitting(extent, pixelWidth, pixelHeight, padding)
    Camera(extent.center, fitted.metersPerPixelAcross)
  }

  /**
    * How close two fingers can be, in pixels, before their spacing stops being trustworthy for
    * a zoom ratio - closer than this (two fingers landing on nearly the same point, or a stray
    * event with duplicate coordinates) and dividing by that spacing would send the scale
    * towards infinity. Holding the scale steady instead is the safe, unsurprising thing to do
    * with a measurement that has stopped meaning anything.
    */
  private val MinGestureSpanPixels = 1.0

  /**
    * The camera two fingers drive, given where they were (`...Before`) and where they are now
    * (`...After`), under `before` - the camera as it stood a moment ago.
    *
    * Pan and pinch are the same arithmetic, not two gestures to distinguish: find the world
    * point each finger was over (via `before.worldAt`), then solve for the one center and one
    * scale that puts those same world points back under wherever the fingers are now. Two
    * fingers translating together (same distance, same direction) leaves the world span between
    * them unchanged, so the scale falls out unchanged and only the center moves; two fingers
    * changing how far apart they are changes the scale, growing or shrinking around the
    * gesture's own midpoint rather than the screen's center or either finger alone - which is
    * what keeps a pinch feeling anchored to the fingers doing it.
    *
    * Called once per touch-move with `before` and the "...Before" points taken from the last
    * event (not from however the gesture originally started) - since each call already anchors
    * the world points under the fingers exactly, chaining calls this way holds the gesture
    * steady for as long as the same two fingers are down, with nothing to keep between calls
    * beyond the camera and the touches' last positions. That is what lets a finger being added
    * or removed just restart the chain from the current touches, with nothing to jump from or
    * back to.
    *
    * Pure and DOM-free on purpose: a spec drives this directly with plain coordinate tuples, so
    * the only thing left for the DOM layer to get right is turning a `TouchEvent` into these
    * eight numbers.
    */
  def followingTouches(
    before: Camera,
    firstBefore: (Double, Double),
    secondBefore: (Double, Double),
    firstAfter: (Double, Double),
    secondAfter: (Double, Double),
    pixelWidth: Int,
    pixelHeight: Int
  ): Camera = {
    val worldFirst = before.worldAt(firstBefore._1, firstBefore._2, pixelWidth, pixelHeight)
    val worldSecond = before.worldAt(secondBefore._1, secondBefore._2, pixelWidth, pixelHeight)

    val worldFirstX = worldFirst.coordinates.head.toMeters
    val worldFirstY = worldFirst.coordinates(1).toMeters
    val worldSecondX = worldSecond.coordinates.head.toMeters
    val worldSecondY = worldSecond.coordinates(1).toMeters

    val worldSpan = math.hypot(worldFirstX - worldSecondX, worldFirstY - worldSecondY)
    val screenSpan = math.hypot(firstAfter._1 - secondAfter._1, firstAfter._2 - secondAfter._2)

    val metersPerPixel =
      if (screenSpan < MinGestureSpanPixels) before.metersPerPixel else worldSpan / screenSpan

    // Anchoring the gesture's own midpoint, not the screen's center - a pinch that keeps one
    // finger still and moves the other should visibly pivot around the two fingers, not slide
    // the whole world towards the middle of the canvas.
    val screenMidX = (firstAfter._1 + secondAfter._1) / 2.0
    val screenMidY = (firstAfter._2 + secondAfter._2) / 2.0
    val worldMidX = (worldFirstX + worldSecondX) / 2.0
    val worldMidY = (worldFirstY + worldSecondY) / 2.0

    val centerX = worldMidX - (screenMidX - pixelWidth / 2.0) * metersPerPixel
    val centerY = worldMidY - (screenMidY - pixelHeight / 2.0) * metersPerPixel

    Camera(
      QuantityVector[Distance](Meters(centerX), Meters(centerY), before.center.coordinates(2)),
      metersPerPixel
    )
  }
}
