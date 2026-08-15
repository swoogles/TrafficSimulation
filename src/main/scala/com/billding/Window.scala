package com.billding

import com.billding.svgRendering.{Projection, RenderedVehicle}
import com.billding.traffic.Scene
import org.scalajs.dom
import org.scalajs.dom.svg.{G, SVG}
import scalatags.JsDom
import scalatags.JsDom.all._
import scalatags.JsDom.{svgAttrs, svgTags}

/*
 * TODO It might make more sense for this to accept a List[JsDom.TypedTag[G]]
 * and canvas dimensions to not muck around with anything specific to the scene.
 */
class Window(scene: Scene, canvasWidth: Int) {

  private val projection: Projection = scene.project(canvasWidth)

  val svgNode: JsDom.TypedTag[SVG] =
    svgTags
      .svg(
        attr("viewBox") := projection.viewBox,
        onwheel := { wheelEvent: dom.MouseEvent =>
          println("we want to zoom in/out here." + wheelEvent)
        }
      )(
        svgTags
          .g(
            createSvgReps(
              scene.renderables.map(createCarSvgRepresentation)
            )
          )
      )

  private def createSvgReps(
    drawables: Seq[JsDom.TypedTag[G]]
  ): JsDom.TypedTag[G] =
    svgTags.g(
      for {
        t <- drawables
      } yield {
        t
      }
    )

  // TODO This should go somewhere else, on its own.
  private def createCarSvgRepresentation(vehicle: RenderedVehicle): JsDom.TypedTag[G] = {
    val CIRCLE: String = "conceptG"

    val renderedWidth = projection.across(vehicle.width)
    val renderedHeight = projection.down(vehicle.height)

    // A car's position is its centre, so the sprite hangs half of itself back and up from
    // there, and turns about that same centre to face along the road.
    val x = projection.xOf(vehicle.position) - renderedWidth / 2
    val y = projection.yOf(vehicle.position) - renderedHeight / 2
    val turn =
      s"rotate(${vehicle.headingInDegrees}, ${renderedWidth / 2}, ${renderedHeight / 2})"

    svgTags.g(
      cls := CIRCLE
    )(
      svgAttrs.transform := s"translate($x, $y) $turn"
    )(
      svgTags.image(
        href := "images/sedan.svg",
        width := renderedWidth.px,
        height := renderedHeight.px,
        onclick := { _: dom.MouseEvent =>
          println(vehicle.uuid)
        }
      )
    )
  }
}
