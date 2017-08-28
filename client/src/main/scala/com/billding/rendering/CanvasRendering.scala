package com.billding.rendering

// This isn't giving the correct class...
//import org.scalajs.dom.html.Canvas
import com.billding.physics.Spatial
import org.scalajs.dom.raw.HTMLCanvasElement
import org.scalajs.dom.{CanvasRenderingContext2D, ImageData}

trait SpriteMap

trait Renderable {
  val sprites: ImageData
  val spatial: Spatial
}

object CanvasRendering {
  /*
    TODO This has NO concept of svg scaling, so the cars could very well be GINORMOUS.

    This is a great learning experience.
    When I was doing everything using only traits, progress soared in the beginning,
    but slowed considerably as time went on. I thought this meant it was time to switch
    over to implementations. Most excitedly, I could start rendering things in the
    browser. My first problem was a true physics problem. I was failing to calculate
    acceleration along the direction of motion. This might have been a mistake I would
    make in any case, but it feels like I caused this grief by not starting with pure
    vector equations/functions throughout. Maybe I would have avoided this problem
    completely if I had stuck to the APIs that the Squants types give me. They should
    have good reasons for their choices, based on how great this library is in all other
    aspects so far.

    I might also be completely off-base, and the problem doesn't lie in the rendering
    at all. It must though.
    Oh wow.
                                    Meters = pixels
    That's the problem. Of course the scaling is terrible.
      -Come up with appropriate pixel <=> Distance conversion.
        -6.inches.per.pixel
          -pixel == int. It's the End of the Line.
   */
  def render(canvas: HTMLCanvasElement, renderables: List[Renderable]): Unit = ???

  private def clear(canvas: HTMLCanvasElement) = {
    val ctx = canvas.getContext("2d") .asInstanceOf[CanvasRenderingContext2D]
//    def putImageData(imagedata: ImageData, dx: Double, dy: Double)
    ctx.clearRect(
      0, 0, canvas.width, canvas.height
    )
  }

  /**
    * The goal here is to get a much longer portion of a straight road rendered in a rectangular space.
    * I will be making a smoothed square wave, with the function given here:
    *   https://mathematica.stackexchange.com/questions/38293/make-a-differentiable-smooth-sawtooth-waveform/38295#38295
    *
    * - Use derivative of curve at any point to get proper orientation of the car.
    * @param x
    */
  def warpLongStraightLineToSmoothSquareWave(x: Double): Double = {
    import scala.math._
    val amplitude = 50.0
    val period = 200.0 // aka L, T
    val offset = 0.0
//    val blah: Double = 3.0 ^ 3.0

    def sgn(x1: Double) = {
      if (x1 < 0) -1
      else if (x1.abs <= 0.001) 0
      else 1
    }
    amplitude * sgn(x) * sin( (2 * Pi * (x - offset)) / period)

    val pieces
    = for (n <- Range(1, 51, 2)) yield {
//      = for (n <- Range(1, 121, 2)) yield {
      (4 / Pi) * (amplitude / n) * sin( (n * Pi * x ) / period )
    }
    pieces.sum

//    pow((amplitude * -1.0), floor(2.0 * (x - offset)/ period))

//    val epsilon = 0.01;
//    2 * atan(sin(2 * Pi * x) / epsilon) / Pi
//    δ = 0.01;
//    sqr[x_] := 2 ArcTan[Sin[2 π x]/δ]/π;
//    Plot[{SquareWave[x], sqr[x]}, {x, -2, 2}, PlotRange -> All, Exclusions -> None]

  }

}


