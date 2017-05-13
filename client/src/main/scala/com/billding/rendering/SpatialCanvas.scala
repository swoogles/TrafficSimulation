package com.billding.rendering

import squants.motion._

trait SpatialCanvas {
  val height: Distance
  val width: Distance

  val pixelHeight: Int
  val pixelWidth: Int
  val heightDistancePerPixel: Distance = height / pixelHeight
  val widthDistancePerPixel: Distance = width / pixelWidth
}
