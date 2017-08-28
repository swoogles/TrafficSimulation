package client

import com.billding.rendering.SpatialCanvas
import com.billding.traffic.{PilotedVehicle, Scene}
import org.scalajs.dom

import scala.scalajs.js
import rx._

import scalatags.JsDom.all._
import scalatags.JsDom.svgAttrs
import scalatags.JsDom.svgTags
import scaladget.stylesheet.all._
import scaladget.api.svg._
import scaladget.tools.JsRxTags._
import org.scalajs.dom.raw._

trait Selectable {
  val selected: Var[Boolean] = Var(false)
}

class Window(scene: Scene) {

  import com.billding.rendering.SpatialCanvasImpl

  val canvasHeight = 800
  val canvasWidth = 1500
  val spatialCanvas = SpatialCanvasImpl(scene.canvasDimensions._1, scene.canvasDimensions._2, canvasHeight, canvasWidth)

  val previousSvg: Node = dom.document.getElementsByTagName("svg").item(0)
  if ( previousSvg != null ) {
    dom.document.body.removeChild(previousSvg)
  }

  val svgNode = {
    val child = svgTags.svg(
      width := canvasWidth,
      height := canvasHeight
    ).render
    dom.document.body.appendChild(child)
    child
  }

  new GraphCreator(
    svgNode,
    scene,
    spatialCanvas
  )
}

class GraphCreator(svg: SVGElement, _scene: Scene, _spatialCanvas: SpatialCanvas) {
  implicit val ctx: Ctx.Owner = Ctx.Owner.safe()

  val CIRCLE: String = "conceptG"

  implicit def dynamicToString(d: js.Dynamic): String = d.asInstanceOf[String]

  implicit def dynamicToBoolean(d: js.Dynamic): Boolean = d.asInstanceOf[Boolean]

  // SVG DEFINITIONS
  val svgG = svgTags.g.render

  // EVENT DEFINITIONS FOR MOUSEUP AND MOUSEDOWN ON THE SCENE
  svg.onmousemove = (me: MouseEvent) => mousemove(me)

  def mousemove(me: MouseEvent) = {
//      val x = me.clientX.toInt
//      val y = me.clientY.toInt
//      if (me.shiftKey) {
  }

  def carReal(vehicle: PilotedVehicle) = {
    import com.billding.physics.SpatialForDefaults
    import com.billding.physics.SpatialForDefaults.spatialForPilotedVehicle
    val spatial = SpatialForDefaults.disect(vehicle)
    val x =
      spatial.r.coordinates.head / _spatialCanvas.widthDistancePerPixel
    val yInit =
      spatial.r.coordinates.tail.head / _spatialCanvas.heightDistancePerPixel
    val y =
      yInit
//      CanvasRendering.warpLongStraightLineToSmoothSquareWave(x) + 500 // Hack to prevent vehicles jumping above canvas

    val renderedWidth = vehicle.spatial.dimensions.coordinates(0) / _spatialCanvas.widthDistancePerPixel
    val renderedHeight = vehicle.spatial.dimensions.coordinates(1) / _spatialCanvas.heightDistancePerPixel

    val element: SVGElement = Rx {
      svgTags.g(
        ms(CIRCLE + {})
      )(
        svgAttrs.transform := s"translate($x, $y)")(
        svgTags.image(href := "images/sedan.svg", width := renderedWidth.px, height := renderedHeight.px).render
      )
    }
    val gCircle = svgTags.g(element).render
    gCircle
  }

  lazy val vehicles: Var[Seq[Var[PilotedVehicle]]] = Var(Seq())
  val vehiclesImmutable: Seq[PilotedVehicle] = _scene.streets.flatMap(_.lanes.flatMap(_.vehicles))
  _scene.streets.flatMap(_.lanes.flatMap(_.vehicles).map { vehicle =>
    addVehicle(vehicle)
  })

  def addToScene[T](s: Var[Seq[Var[T]]], draw: T => SVGElement): Node = {
    val element: SVGElement = Rx {
      svgTags.g(
        for {
          t <- s()
        } yield {
          draw(t.now)
        }
      )
    }
    svgG.appendChild(svgTags.g(element).render).render
  }

  addToScene(vehicles, carReal)

  def addVehicle(pilotedVehicle: PilotedVehicle): Unit = {
    vehicles() = vehicles.now :+ Var(pilotedVehicle)
  }
}
