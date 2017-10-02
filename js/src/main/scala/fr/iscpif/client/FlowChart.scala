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

import rx.{Ctx, Rx, Var}

import scalatags.JsDom.svgAttrs
import scalatags.JsDom.svgTags
import scaladget.stylesheet.all.ms
import scaladget.tools.JsRxTags._
import org.scalajs.dom.raw._
import scalatags.JsDom.all._

trait Selectable {
  val selected: Var[Boolean] = Var(false)
}

class Window(scene: Scene)(implicit ctx: Ctx.Owner) {
  import com.billding.rendering.SpatialCanvasImpl

  // TODO ooooooooo, I think these could be made into Rxs/Vars for responsive rendering on screen resizing.
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

  new GraphCreator(
    svgNode,
    scene,
    spatialCanvas
  ).drawItems()

  svgNode.forceRedraw()
}

class GraphCreator(
  svg: SVGElement,
  scene: Scene,
  spatialCanvas: SpatialCanvas
)(implicit ctx: Ctx.Owner) {
  val CIRCLE: String = "conceptG"
  val DELETE_KEY = 46

  // SVG DEFINITIONS
  val svgG = svgTags.g.render

  svg.appendChild(svgG)

  // TODO I dunno, maybe make this less terrible?
  private def carReal(vehicle: PilotedVehicle) = {
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
            height := renderedHeight.px
          )
        )
      }
    svgTags.g(element).render
  }

  val vehiclesImmutable: Seq[PilotedVehicle] =
    scene.streets.flatMap(
      _.lanes.flatMap(_.vehicles)
    )

  // ADD ALL vehicles ON THE SCENE. THE RX SEQUENCE IS AUTOMATICALLY RUN IN CASE OF vehicles ALTERATION
  def createSvgReps[T](drawables: Seq[T], draw: T => SVGElement): SVGElement = {
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

  def drawItems() = {
    svgG.appendChild(
      svgTags.g(
        createSvgReps(vehiclesImmutable, carReal)
      ).render
    ).render
  }

}
