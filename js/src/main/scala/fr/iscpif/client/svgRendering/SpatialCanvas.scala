package fr.iscpif.client.svgRendering

import squants.motion.Distance

case class SpatialCanvas(
    height: Distance,
    width: Distance,
    pixelHeight: Int,
    pixelWidth: Int
) {
  val heightDistancePerPixel: Distance = height / pixelHeight
  val widthDistancePerPixel: Distance = width / pixelWidth
}
