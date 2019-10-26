package fr.iscpif.client.svgRendering

trait Renderator[T] {
  def makeRenderable(t: T): Renderable
}
