package fr.iscpif.client.svgRendering

import fr.iscpif.client.physics.Spatial
import org.scalajs.dom.ImageData

trait Renderable {
  val sprites: ImageData
  val spatial: Spatial
}
