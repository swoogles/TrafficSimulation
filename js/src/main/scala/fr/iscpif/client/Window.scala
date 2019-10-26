package fr.iscpif.client

import com.billding.rendering.SpatialCanvasImpl
import fr.iscpif.client.previouslySharedCode.physics.{SpatialFor, SpatialImpl}
import fr.iscpif.client.previouslySharedCode.serialization.BillSquants
import fr.iscpif.client.previouslySharedCode.traffic.{DriverImpl, PilotedVehicle, PilotedVehicleImpl, Scene, SceneImpl}
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
import squants.motion._
import squants.{Length, QuantityVector, Time, Velocity}
import play.api.libs.json.{Format, Json}

/*
  * TODO It might make more sense for this to accept a List[JsDom.TypedTag[G]]
  * and canvas dimensions to not muck around with anything specific to the scene.
  */
class Window(scene: Scene, canvasHeight: Int, canvasWidth: Int)(
    implicit ctx: Ctx.Owner, format: Format[SceneImpl]) {
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
    implicit val df: Format[Distance] = BillSquants.distance.format
    implicit val tf: Format[Time] = BillSquants.time.format
    implicit val vf: Format[Velocity] = BillSquants.velocity.format
    implicit val dQvf: Format[QuantityVector[Distance]] =
      BillSquants.distance.formatQv
    implicit val vQvf: Format[QuantityVector[Velocity]] =
      BillSquants.velocity.formatQv
    implicit val spatialFormat: Format[SpatialImpl] = Json.format[SpatialImpl]
    implicit val driverFormat: Format[DriverImpl] = Json.format[DriverImpl]
    implicit val spatialForPilotedVehicle: SpatialFor[PilotedVehicle] = {
      case vehicle: PilotedVehicleImpl => vehicle.spatial
    }
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
