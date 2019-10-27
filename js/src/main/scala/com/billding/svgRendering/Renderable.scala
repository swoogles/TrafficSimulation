package com.billding.svgRendering

import com.billding.physics.Spatial
import org.scalajs.dom.ImageData

trait Renderable {
  val sprites: ImageData
  val spatial: Spatial
}
