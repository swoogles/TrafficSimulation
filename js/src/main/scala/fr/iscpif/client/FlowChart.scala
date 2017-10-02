package fr.iscpif.client

/*
 * Copyright (C) 22/09/14 // mathieu.leclaire@openmole.org
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

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
    dom.document.body.appendChild(child.render)
    child
  }

  new GraphCreator(svgNode,
    scene,
    spatialCanvas
  )
}

class GraphCreator(svg: SVGElement, _scene: Scene, _spatialCanvas: SpatialCanvas) {

  implicit val ctx: Ctx.Owner = Ctx.Owner.safe()

  val CIRCLE: String = "conceptG"
  val DELETE_KEY = 46

  implicit def dynamicToString(d: js.Dynamic): String = d.asInstanceOf[String]

  implicit def dynamicToBoolean(d: js.Dynamic): Boolean = d.asInstanceOf[Boolean]

  // SVG DEFINITIONS
  val svgG = svgTags.g.render
  val defs = svgTags.defs.render

  svg.appendChild(svgG)
  svg.appendChild(defs)

  def carReal(vehicle: PilotedVehicle) = {
    import com.billding.physics.SpatialForDefaults
    import com.billding.physics.SpatialForDefaults.spatialForPilotedVehicle
    val spatial = SpatialForDefaults.disect(vehicle)
    val x = spatial.r.coordinates.head / _spatialCanvas.widthDistancePerPixel
    val y = spatial.r.coordinates.tail.head / _spatialCanvas.heightDistancePerPixel
    val xV = spatial.v.coordinates.head
    val renderedWidth = vehicle.spatial.dimensions.coordinates(0) / _spatialCanvas.widthDistancePerPixel
    val renderedHeight = vehicle.spatial.dimensions.coordinates(1) / _spatialCanvas.heightDistancePerPixel

    val element: SVGElement = Rx {
      svgTags.g(
        ms(CIRCLE)
      )(
        svgAttrs.transform := s"translate($x, $y)")(
          svgTags.image(href := "images/sedan.svg", width := renderedWidth.px, height := renderedHeight.px).render
      )
    }
    val gCircle = svgTags.g(element).render

    gCircle
  }

  // TODO Process Scene.lanes & Scene.lanes.vehicles
  lazy val vehicles: Var[Seq[Var[PilotedVehicle]]] = Var(Seq())
  val vehiclesImmutable: Seq[PilotedVehicle] = _scene.streets.flatMap(_.lanes.flatMap(_.vehicles))
  _scene.streets.flatMap(_.lanes.flatMap(_.vehicles).map { vehicle =>
    addVehicle(vehicle)
  })


  // ADD ALL vehicles ON THE SCENE. THE RX SEQUENCE IS AUTOMATICALLY RUN IN CASE OF vehicles ALTERATION
  def addToScene[T](s: Var[Seq[Var[T]]], draw: T => SVGElement) = {
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

  // ADD, SELECT AND REMOVE ITEMS
  def addVehicle(pilotedVehicle: PilotedVehicle): Unit = {
    vehicles() = vehicles.now :+ Var(pilotedVehicle)
  }
}
