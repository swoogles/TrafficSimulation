package com.billding

import com.billding.physics.SpatialFor
import com.billding.svgRendering.SpatialCanvas
import com.billding.traffic.{PilotedVehicle, Scene}
import org.scalajs.dom
import org.scalajs.dom.raw.SVGElement
import org.scalajs.dom.svg.{G, SVG}
import rx.{Ctx, Rx}
import scaladget.stylesheet.all.ms
import scaladget.tools.JsRxTags._
import scalatags.JsDom
import scalatags.JsDom.all._
import scalatags.JsDom.{svgAttrs, svgTags}

/*
 * TODO It might make more sense for this to accept a List[JsDom.TypedTag[G]]
 * and canvas dimensions to not muck around with anything specific to the scene.
 */
class Window(scene: Scene, canvasHeight: Int, canvasWidth: Int)(
  implicit ctx: Ctx.Owner,
  implicit val spatialForPilotedVehicle: SpatialFor[PilotedVehicle]
) {

  // TODO ooooooooo, I think these could be made into Rxs/Vars for responsive rendering on screen resizing.
  val spatialCanvas =
    SpatialCanvas(scene.canvasDimensions._1, scene.canvasDimensions._2, canvasHeight, canvasWidth)

  val svgNode: JsDom.TypedTag[SVG] =
    svgTags
      .svg(
        attr("viewBox") := "0 0 500 500",
        //        width := canvasWidth,
        //        height := canvasHeight,
        onclick := { (e: dom.MouseEvent) =>
          println(e)
        },
        onwheel := { wheelEvent: dom.MouseEvent => println("we want to zoom in/out here." + wheelEvent) }
      )(
        svgTags
          .g(
            createSvgReps(
              scene.applyToAllVehicles(createCarSvgRepresentation)
            )
          )
      )

  private def createSvgReps(
                             drawables: Seq[JsDom.TypedTag[SVGElement]]
                           ): JsDom.TypedTag[SVGElement] =
    svgTags.g(
      for {
        t <- drawables
      } yield {
        t
      }
    )

  // TODO This should go somewhere else, on its own.
  private def createCarSvgRepresentation(vehicle: PilotedVehicle): JsDom.TypedTag[G] = {
    val CIRCLE: String = "conceptG"

    val spatial = SpatialFor.disect(vehicle)
    val x = spatial.x / spatialCanvas.widthDistancePerPixel
    val y = spatial.y / spatialCanvas.heightDistancePerPixel
    val renderedWidth = vehicle.width / spatialCanvas.widthDistancePerPixel
    val renderedHeight = vehicle.height / spatialCanvas.heightDistancePerPixel

    val element: SVGElement =
      Rx {
        svgTags.g(
          ms(CIRCLE)
        )(
          svgAttrs.transform := s"translate($x, $y)"
        )(
          svgTags.image(
            href := "images/sedan.svg",
            width := renderedWidth.px,
            height := renderedHeight.px,
            onclick := { (e: dom.MouseEvent) =>
              println(vehicle.uuid)
            }
          )
        )
      }

    svgTags.g(element)
  }
}
