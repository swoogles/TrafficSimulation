package fr.iscpif.client.svgRendering

import fr.iscpif.client.physics.Spatial
import fr.iscpif.client.traffic.{Lane, Vehicle}
import org.scalajs.dom.ImageData

trait Renderator[T] {
  def makeRenderable(t: T): Renderable
}
object TypeClasses {
  val vehicleRenderator = new Renderator[Vehicle] {
    override def makeRenderable(t: Vehicle): Renderable = new Renderable {
      override val sprites: ImageData = ???
      override val spatial: Spatial = ???
    }
  }
//  val roadRenderator = new Renderator[Road] {
//    override def makeRenderable(t: Road): Renderable = new Renderable {
//      override val sprites: ImageData = ???
//      override val spatial: Spatial = ???
//    }
//  }
  val laneRenderator = new Renderator[Lane] {
    override def makeRenderable(t: Lane): Renderable = new Renderable {
      override val sprites: ImageData = ???
      override val spatial: Spatial = ???
    }
  }

}
