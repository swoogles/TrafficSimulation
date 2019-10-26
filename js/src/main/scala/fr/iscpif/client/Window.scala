package fr.iscpif.client

import com.billding.rendering.SpatialCanvasImpl
import fr.iscpif.client.previouslySharedCode.traffic.{PilotedVehicle, Scene}
import org.scalajs.dom
import org.scalajs.dom.raw.SVGElement
import rx.{Ctx, Rx}

import scalatags.JsDom.svgAttrs
import scalatags.JsDom.svgTags
import scaladget.stylesheet.all.ms
import scaladget.tools.JsRxTags._
import org.scalajs.dom.svg.{G, SVG}

import scalatags.JsDom
import scalatags.JsDom.all._

/*
  * TODO It might make more sense for this to accept a List[JsDom.TypedTag[G]]
  * and canvas dimensions to not muck around with anything specific to the scene.
  */
class Window(scene: Scene, canvasHeight: Int, canvasWidth: Int)(
    implicit ctx: Ctx.Owner) {
  println("making a new Window")

  // TODO ooooooooo, I think these could be made into Rxs/Vars for responsive rendering on screen resizing.
  val spatialCanvas = SpatialCanvasImpl(scene.canvasDimensions._1,
                                        scene.canvasDimensions._2,
                                        canvasHeight,
                                        canvasWidth)

  val svgNode: JsDom.TypedTag[SVG] =
    svgTags
      .svg(
        width := canvasWidth,
        height := canvasHeight,
        onclick := { (e: dom.MouseEvent) =>
          println(e)
        },
        onwheel := { (wheelEvent: dom.MouseEvent) =>
          println("we want to zoom in/out here." + wheelEvent)
        // Add mousewheel behavior here?
        }
      )(
        svgTags
          .g(
            createSvgReps(
              scene.applyToAllVehicles(carReal)
            )
          )
      )

  private def createSvgReps(drawables: Seq[JsDom.TypedTag[SVGElement]])
    : JsDom.TypedTag[SVGElement] = {
    svgTags.g(
      for {
        t <- drawables
      } yield {
        t
      }
    )
  }

  // TODO This should go somewhere else, on its own.
  private def carReal(vehicle: PilotedVehicle): JsDom.TypedTag[G] = {
    val CIRCLE: String = "conceptG"
    import fr.iscpif.client.previouslySharedCode.physics.SpatialForDefaults
    import fr.iscpif.client.previouslySharedCode.physics.SpatialForDefaults.spatialForPilotedVehicle
    val spatial = SpatialForDefaults.disect(vehicle)
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
