package com.billding.svgRendering

import squants.motion.Distance

/**
* Trying to understand this class...
  *
  * @param height Distance displayed in the canvas
  * @param width Distance displayed in the canvas
  * @param pixelHeight How tall the canvas is on the page
  * @param pixelWidth How wide the canvas is on the page
  */
case class SpatialCanvas(
                          height: Distance,
                          width: Distance,
                          pixelHeight: Int,
                          pixelWidth: Int
) {
  // Still not thrilled about this arbitrary multiplication
  val heightDistancePerPixel: Distance = height / (pixelHeight * 3)
  val widthDistancePerPixel: Distance = width / (pixelWidth * 3)
}
