package fr.iscpif.client

import com.billding.rendering.SpatialCanvasImpl
import com.billding.traffic.{PilotedVehicle, Scene}
import org.scalajs.dom
import rx.{Ctx, Rx}

import scalatags.JsDom.svgAttrs
import scalatags.JsDom.svgTags
import scaladget.stylesheet.all.ms
import scaladget.tools.JsRxTags._
import org.scalajs.dom.raw._
import org.scalajs.dom.svg.G

import scalatags.JsDom.all._

class Window(scene: Scene)(implicit ctx: Ctx.Owner) {

  // TODO ooooooooo, I think these could be made into Rxs/Vars for responsive rendering on screen resizing.
  val canvasHeight = 800
  val canvasWidth = 1500
  val spatialCanvas = SpatialCanvasImpl(scene.canvasDimensions._1, scene.canvasDimensions._2, canvasHeight, canvasWidth)

  val previousSvg: Node = dom.document.getElementsByTagName("svg").item(0)
  if ( previousSvg != null ) {
    dom.document.body.removeChild(previousSvg)
  }

  val svgNode = {
    val child =
      svgTags.svg(
        width := canvasWidth,
        height := canvasHeight,
//        onclick := { (e: dom.MouseEvent) =>
//          println(e)
//        }
      ).render
    dom.document.body.appendChild(child.render)
    child
  }

  svgNode.appendChild(
    drawItemsInNewElement()
  )

  svgNode.forceRedraw()

  private def createSvgReps[T](drawables: Seq[T], draw: T => SVGElement): SVGElement = {
    Rx {
      svgTags.g(
        for {
          t <- drawables
        } yield {
          draw(t)
        }
      )
    }
  }

  private def drawItemsInNewElement() = {
    svgTags.g(
      createSvgReps(scene.allVehicles, carReal)
    ).render
  }

  private def carReal(vehicle: PilotedVehicle): G = {
    val CIRCLE: String = "conceptG"
    import com.billding.physics.SpatialForDefaults
    import com.billding.physics.SpatialForDefaults.spatialForPilotedVehicle
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
    svgTags.g(element).render
  }
}

