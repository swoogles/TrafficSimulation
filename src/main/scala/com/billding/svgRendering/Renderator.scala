package com.billding.svgRendering

trait Renderator[T] {
  def makeRenderable(t: T): Renderable
}
