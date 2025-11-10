package com.billding

import com.billding.physics.SpatialFor
import com.billding.svgRendering.SpatialCanvas
import com.billding.traffic.{PilotedVehicle, Scene}
import org.scalajs.dom
import org.scalajs.dom.svg.{G, SVG}
import scalatags.JsDom
import scalatags.JsDom.all._
import scalatags.JsDom.{svgAttrs, svgTags}

/*
 * TODO It might make more sense for this to accept a List[JsDom.TypedTag[G]]
 * and canvas dimensions to not muck around with anything specific to the scene.
 */
class Window(scene: Scene, canvasHeight: Int, canvasWidth: Int)(
  implicit val spatialForPilotedVehicle: SpatialFor[PilotedVehicle]
) {

  // TODO ooooooooo, I think these could be made into Rxs/Vars for responsive rendering on screen resizing.
//  println("CanvasHeight: " + canvasHeight)
//  println("CanvasWidth: " + canvasWidth)
  private val spatialCanvas =
    SpatialCanvas(scene.canvasDimensions._1, scene.canvasDimensions._2, canvasHeight, canvasWidth)

  val svgNode: JsDom.TypedTag[SVG] =
    svgTags
      .svg(
        attr("viewBox") := s"0 0 $canvasWidth $canvasHeight", // TODO double check order here
        onwheel := { wheelEvent: dom.MouseEvent =>
          println("we want to zoom in/out here." + wheelEvent)
        }
      )(
        svgTags
          .g(
            createSvgReps(
              scene.applyToAllVehicles(createCarSvgRepresentation)
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

  private def renderedWidthInPixels(vehicle: PilotedVehicle): String =
    (vehicle.width / spatialCanvas.widthDistancePerPixel).px

  private def renderedHeightInPixels(vehicle: PilotedVehicle): String =
    (vehicle.height / spatialCanvas.heightDistancePerPixel).px

  // TODO This should go somewhere else, on its own.
  private def createCarSvgRepresentation(vehicle: PilotedVehicle): JsDom.TypedTag[G] = {
    val CIRCLE: String = "conceptG"

    val spatial = SpatialFor.disect(vehicle)
    val x = spatial.x / spatialCanvas.widthDistancePerPixel
    val y = spatial.y / spatialCanvas.heightDistancePerPixel

    svgTags.g(
      cls := CIRCLE
    )(
      svgAttrs.transform := s"translate($x, $y)"
    )(
      svgTags.image(
        href := "images/sedan.svg",
        width := renderedWidthInPixels(vehicle),
        height := renderedHeightInPixels(vehicle),
        onclick := { _: dom.MouseEvent =>
          println(vehicle.uuid)
        }
      )
    )
  }
}
