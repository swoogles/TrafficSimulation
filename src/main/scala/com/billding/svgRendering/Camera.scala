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
}
