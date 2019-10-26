package fr.iscpif.client.svgRendering

import squants.motion.Distance

case class SpatialCanvasImpl(
    height: Distance,
    width: Distance,
    pixelHeight: Int,
    pixelWidth: Int
) extends {
  val heightDistancePerPixel: Distance = height / pixelHeight
  val widthDistancePerPixel: Distance = width / pixelWidth
}
