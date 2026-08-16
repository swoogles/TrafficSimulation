package com.billding

import com.billding.svgRendering.{Projection, RenderedVehicle, RoadRing, RoadShape, RoadStrip}
import com.billding.traffic.Scene
import org.scalajs.dom
import org.scalajs.dom.svg.{G, SVG}
import scalatags.JsDom
import scalatags.JsDom.all._
import scalatags.JsDom.{svgAttrs, svgTags}
import squants.space.Length

/*
 * TODO It might make more sense for this to accept a List[JsDom.TypedTag[G]]
 * and canvas dimensions to not muck around with anything specific to the scene.
 */
object Window {

  /*
  These live out here rather than in the class body on purpose. svgNode is a val that renders
  during construction, so any field declared below it is still null by the time it is read.
   */
  private val Tarmac = "#23282f"
  private val EdgeLine = "#ffffff"

  /** How far in from the kerb the painted lines sit, as a fraction of the road's width. */
  private val EdgeLineInset = 0.42

  /**
    * Painted lines are a share of the road's own width, so they stay in proportion as the
    * canvas grows, with a floor that keeps them from vanishing on a small one.
    */
  private def edgeLineWidth(roadWidthInPixels: Double): Double =
    math.max(1.0, roadWidthInPixels * 0.12)
}

class Window(scene: Scene, canvasWidth: Int) {
  import Window.{edgeLineWidth, EdgeLine, EdgeLineInset, Tarmac}

  private val projection: Projection = scene.project(canvasWidth)

  val svgNode: JsDom.TypedTag[SVG] =
    svgTags
      .svg(
        attr("viewBox") := projection.viewBox,
        onwheel := { wheelEvent: dom.MouseEvent =>
          println("we want to zoom in/out here." + wheelEvent)
        }
      )(
        svgTags.g(
          createSvgReps(scene.roadShapes.map(createRoadSvgRepresentation)),
          createSvgReps(scene.renderables.map(createCarSvgRepresentation))
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

  /**
    * The road under the traffic: a band of tarmac with a line down either edge.
    *
    * A ring is drawn as an actual circle rather than a many-sided polygon, so it stays
    * smooth however far in you zoom.
    */
  private def createRoadSvgRepresentation(road: RoadShape): JsDom.TypedTag[G] =
    road match {
    case RoadRing(center, radius, width) =>
      val cx = projection.xOf(center)
      val cy = projection.yOf(center)

      def ring(atRadius: Length, colour: String, thickness: Double) =
        svgTags.circle(
          svgAttrs.cx := cx.toString,
          svgAttrs.cy := cy.toString,
          svgAttrs.r := projection.across(atRadius).toString,
          svgAttrs.fill := "none",
          svgAttrs.stroke := colour,
          svgAttrs.strokeWidth := thickness.toString
        )

      val tarmacWidth = projection.across(width)
      val lineWidth = edgeLineWidth(tarmacWidth)

      svgTags.g(cls := "roadway")(
        ring(radius, Tarmac, tarmacWidth),
        ring(radius + width * EdgeLineInset, EdgeLine, lineWidth),
        ring(radius - width * EdgeLineInset, EdgeLine, lineWidth)
      )

    case RoadStrip(from, to, width) =>
      val toEdgeLine = projection.down(width) * EdgeLineInset

      def stripe(offset: Double, colour: String, thickness: Double) =
        svgTags.line(
          svgAttrs.x1 := projection.xOf(from).toString,
          svgAttrs.y1 := (projection.yOf(from) + offset).toString,
          svgAttrs.x2 := projection.xOf(to).toString,
          svgAttrs.y2 := (projection.yOf(to) + offset).toString,
          svgAttrs.stroke := colour,
          svgAttrs.strokeWidth := thickness.toString
        )

      val tarmacWidth = projection.down(width)
      val lineWidth = edgeLineWidth(tarmacWidth)

      svgTags.g(cls := "roadway")(
        stripe(0, Tarmac, tarmacWidth),
        stripe(-toEdgeLine, EdgeLine, lineWidth),
        stripe(toEdgeLine, EdgeLine, lineWidth)
      )
  }

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
