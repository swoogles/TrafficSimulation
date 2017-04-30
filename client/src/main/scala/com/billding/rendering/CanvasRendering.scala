package com.billding.rendering

trait Canvas

// This isn't giving the correct class...
//import org.scalajs.dom.html.Canvas
import com.billding.Spatial
import org.scalajs.dom.raw.HTMLCanvasElement
import org.scalajs.dom.{CanvasRenderingContext2D, ImageData}

trait SpriteMap

trait Renderable {
  val sprites: ImageData
  val spatial: Spatial
}

object CanvasRendering {
  def render(canvas: HTMLCanvasElement, renderables: List[Renderable]): Unit = ???

  private def clear(canvas: HTMLCanvasElement) = {
    val ctx = canvas.getContext("2d") .asInstanceOf[CanvasRenderingContext2D]
//    def putImageData(imagedata: ImageData, dx: Double, dy: Double)
    ctx.clearRect(
      0, 0, canvas.width, canvas.height
    )
  }

}


