package com.billding.svgRendering

import com.billding.physics.PathExtent
import squants.motion.Distance
import squants.QuantityVector

/**
  * How a scene lays its world out on the canvas.
  *
  * Scenes build their own, because they don't all want the same treatment: a straight road
  * is a letterbox strip that can afford to stretch its two axes differently, while a ring
  * has to be scaled evenly in both or it draws an ellipse.
  */
case class Projection(
  pixelWidth: Int,
  pixelHeight: Int,
  metersPerPixelAcross: Double,
  metersPerPixelDown: Double,
  worldOriginInPixels: (Double, Double)
) {

  def xOf(position: QuantityVector[Distance]): Double =
    worldOriginInPixels._1 + position.coordinates.head.toMeters / metersPerPixelAcross

  def yOf(position: QuantityVector[Distance]): Double =
    worldOriginInPixels._2 + position.coordinates(1).toMeters / metersPerPixelDown

  def across(distance: Distance): Double = distance.toMeters / metersPerPixelAcross

  def down(distance: Distance): Double = distance.toMeters / metersPerPixelDown

  val viewBox: String = s"0 0 $pixelWidth $pixelHeight"
}

object Projection {

  /**
    * Fit a patch of world into a square-ish canvas at one scale for both axes, so circles
    * come out round. The world point at the middle of the extent lands at the middle of
    * the canvas.
    */
  def fitting(extent: PathExtent,
              pixelWidth: Int,
              pixelHeight: Int,
              padding: Double): Projection = {
    val metersPerPixel = math.max(
      extent.width.toMeters * padding / pixelWidth,
      extent.height.toMeters * padding / pixelHeight
    )
    val origin = (
      pixelWidth / 2.0 - extent.center.coordinates.head.toMeters / metersPerPixel,
      pixelHeight / 2.0 - extent.center.coordinates(1).toMeters / metersPerPixel
    )
    Projection(pixelWidth, pixelHeight, metersPerPixel, metersPerPixel, origin)
  }
}
