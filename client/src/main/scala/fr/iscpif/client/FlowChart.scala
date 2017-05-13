package client

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

import java.util.UUID

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

// DEFINE SOME CASE CLASS TO STORE TASK AND EDGE STRUCTURES
object Graph {

  case class Task(title: Var[String] = Var(""),
                  location: Var[(Double, Double)] = Var((0.0, 0.0))) extends Selectable

  class Edge(val source: Var[Task],
             val target: Var[Task]) extends Selectable

  def task(title: String, x: Double, y: Double) = Task(Var(title), Var((x, y)))

  def edge(source: Task, target: Task) = new Edge(Var(source), Var(target))
}

import Graph._

class Window(scene: Scene, nodes: Seq[Task] = Seq(), edges: Seq[Edge] = Seq()) {

  import com.billding.rendering.SpatialCanvas
  import com.billding.rendering.SpatialCanvasImpl

  val canvasHeight = 800
  val canvasWidth = 1500
  val spatialCanvas = SpatialCanvasImpl(scene.dimensions.height, scene.dimensions.width, canvasHeight, canvasWidth)

  val previousSvg: Node = dom.document.getElementsByTagName("svg").item(0)
  if ( previousSvg != null ) {
      dom.document.body.removeChild(previousSvg)
  }
//  val dimensions
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
    nodes,
    edges,
    spatialCanvas
  )
}

class GraphCreator(svg: SVGElement, _scene: Scene, _tasks: Seq[Task], _edges: Seq[Edge], _spatialCanvas: SpatialCanvas) {

  implicit val ctx: Ctx.Owner = Ctx.Owner.safe()

  val SELECTED: String = "selected"
  val CIRCLE: String = "conceptG"
  val LINK: String = "link"
  val LINK_DRAGLINE: String = "link dragline"
  val HIDDEN: String = "hidden"
  val DELETE_KEY = 46
  val NODE_RADIUS = 50
  val END_ARROW: String = "end-arrow"
  val URL_END_ARROW: String = s"url(#${END_ARROW})"
  val MARK_END_ARROW: String = "mark-end-arrow"
  val URL_MARK_END_ARROW: String = s"url(#${MARK_END_ARROW})"

  // DEFINE A SVG ELEMENT TO DISPLAY A PHANTOM LINK WHILE DRAGGING A LINK FROM ONE TASK TO ANOTHER
  class DragLine {
    private val m: Var[(Int, Int)] = Var((0, 0))
    private val l: Var[(Int, Int)] = Var((0, 0))
    val dragging = Var(false)

    def move(x: Int, y: Int) = {
      dragging() = true
      m() = (x, y)
      this
    }

    def line(x: Int, y: Int) = {
      dragging() = true
      l() = (x, y)
      this
    }

    val render: SVGElement = Rx {
      path(ms = ms(s"$LINK_DRAGLINE ${
        if (dragging()) "" else HIDDEN
      }")).m(m()._1, m()._2).l(l()._1, l()._2)(svgAttrs.markerEnd := URL_MARK_END_ARROW).render
    }
  }

  implicit def dynamicToString(d: js.Dynamic): String = d.asInstanceOf[String]

  implicit def dynamicToBoolean(d: js.Dynamic): Boolean = d.asInstanceOf[Boolean]

  // SVG DEFINITIONS
  val svgG = svgTags.g.render
  val defs = svgTags.defs.render

  val dragLine = new DragLine

  svgG.appendChild(dragLine.render)
  svg.appendChild(svgG)
  svg.appendChild(defs)

  val mouseDownTask: Var[Option[Task]] = Var(None)
  val dragging: Var[Option[DragLine]] = Var(None)

  // EVENT DEFINITIONS FOR MOUSEUP AND MOUSEDOWN ON THE SCENE
  svg.onmousemove = (me: MouseEvent) => mousemove(me)
  svg.onmouseup = (me: MouseEvent) => mouseup(me)

  def mousemove(me: MouseEvent) = {
    Seq(mouseDownTask.now).flatten.map { t ⇒
      val x = me.clientX.toInt
      val y = me.clientY.toInt
      if (me.shiftKey) {
        dragLine.move(t.location.now._1.toInt, t.location.now._2.toInt).line(x, y)
      }
      else {
        t.location() = (x, y)
      }
    }
  }

  def mouseup(me: MouseEvent) = {
    // Hide the drag line
    if (me.shiftKey && !dragLine.dragging.now) {
      val (x, y) = (me.clientX, me.clientY)
      addTask(task(UUID.randomUUID().toString, x, y))
    }
    mouseDownTask() = None
    dragLine.dragging() = false
  }

  // ARROW MARKERS FOR GRAPH LINKS
  def arrow = svgTags.marker(
    svgAttrs.viewBox := "0 -5 10 10",
    svgAttrs.markerWidth := "3.5",
    svgAttrs.markerHeight := "3.5"
    // TODO Fix compile error in example code
    // svgAttrs.orient := "auto",
    // svgAttrs.refX := 32
  )

  def endArrowMarker = arrow(
    id := END_ARROW,
    // svgAttrs.refX := 32,
    path.start(0, -5).l(10, 0).l(0, 5).render
  ).render

  def markEndArrow = arrow(
    id := MARK_END_ARROW,
    // svgAttrs.refX := 7,
    path.start(0, -5).l(10, 0).l(0, 5).render
  ).render

  defs.appendChild(endArrowMarker)
  defs.appendChild(markEndArrow)

  lazy val tasks: Var[Seq[Var[Task]]] = Var(Seq())
  _tasks.map {
    addTask
  }

  // RETURN A SVG CIRCLE, WHICH CAN BE SELECTED (ON CLICK), MOVED OR DELETED (DEL KEY)
   def circle(task: Task) = {
    val element: SVGElement = Rx {
      svgTags.g(
        ms(CIRCLE + {
          if (task.selected()) s" $SELECTED" else ""
        })
      )(
        svgAttrs.transform := s"translate(${
          val location = task.location()
          s"${location._1}, ${location._2}"
        })")(svgTags.circle(svgAttrs.r := NODE_RADIUS).render)
    }
    val gCircle = svgTags.g(element).render

    gCircle.onmousedown = (me: MouseEvent) => {
      mouseDownTask() = Some(task)
      me.stopPropagation
      unselectTasks
      unselectEdges
      task.selected() = !task.selected.now
    }

    gCircle.onmouseup = (me: MouseEvent) => {
      Seq(mouseDownTask.now).flatten.map { mdt ⇒
        if (task != mdt) {
          addEdge(edge(mdt, task))
        }
      }
    }
    gCircle
  }

  def carReal(vehicle: PilotedVehicle) = {
    import com.billding.physics.SpatialForDefaults
    import com.billding.physics.SpatialForDefaults.spatialForPilotedVehicle
    val spatial = vehicle.spatial
    // TODO Move these conversions to appropriate place

//    println("spatial.r: " + spatial.r)
//    println("spatial.v: " + spatial.r)
    val x = spatial.r.coordinates.head / _spatialCanvas.widthDistancePerPixel
    val y = spatial.r.coordinates.tail.head / _spatialCanvas.heightDistancePerPixel
    val xV = spatial.v.coordinates.head
    val renderedWidth = vehicle.spatial.dimensions.coordinates(0) / _spatialCanvas.widthDistancePerPixel
    val renderedHeight = vehicle.spatial.dimensions.coordinates(1) / _spatialCanvas.heightDistancePerPixel

    val element: SVGElement = Rx {
      svgTags.g(
        ms(CIRCLE + {
          // if (task.selected()) s" $SELECTED" else ""
        })
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
  val vehiclesImmutable: Seq[PilotedVehicle] = _scene.lanes.flatMap(_.vehicles)
  _scene.lanes.flatMap(_.vehicles).map { vehicle =>
    addVehicle(vehicle)
  }
  lazy val edges: Var[Seq[Var[Edge]]] = Var(Seq())
  _edges.map { e =>
    addEdge(edge(e.source.now, e.target.now))
  }

  // DEFINE A LINK, WHICH CAN BE SELECTED AND REMOVED (DEL KEY)
  def link(edge: Edge) = {
    val sVGElement: SVGElement = Rx {
      val p = path(ms = (if (edge.selected()) ms(SELECTED) else emptyMod) +++
        ms(LINK)).m(
        edge.source().location()._1.toInt,
        edge.source().location()._2.toInt
      ).l(
        edge.target().location()._1.toInt,
        edge.target().location()._2.toInt
      ).render(svgAttrs.markerEnd := URL_END_ARROW).render

      p.onmousedown = (me: MouseEvent) => {
        unselectTasks
        unselectEdges
        edge.selected() = !edge.selected.now
      }
      p
    }

    svgTags.g(sVGElement).render
  }

  // ADD ALL LINKS AND CIRCLES ON THE SCENE. THE RX SEQUENCE IS AUTOMATICALLY RUN IN CASE OF tasks or edges ALTERATION
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

  addToScene(edges, link)
  addToScene(vehicles, carReal)
//  addToScene(tasks, circle)

  // DEAL WITH DEL KEY ACTION
  dom.document.onkeydown = (e: KeyboardEvent) => {
    e.keyCode match {
      case DELETE_KEY ⇒
        tasks.now.filter(t ⇒ t.now.selected.now).map { t ⇒
          removeTask(t)
        }
        edges.now.filter(e ⇒ e.now.selected.now).map { e ⇒
          removeEdge(e)
        }
      case _ ⇒
    }
  }

  // ADD, SELECT AND REMOVE ITEMS
  def unselectTasks = tasks.now.foreach { t ⇒ t.now.selected() = false }

  def unselectEdges = edges.now.foreach { e ⇒ e.now.selected() = false }

  def removeTask(t: Var[Task]) = {
    tasks() = tasks.now diff Seq(t)
    edges() = edges.now.filterNot(e ⇒ e.now.source.now == t.now || e.now.target.now == t.now)
  }

  def removeEdge(e: Var[Edge]) = {
    edges() = edges.now diff Seq(e)
  }

  def addTask(task: Task): Unit = {
    tasks() = tasks.now :+ Var(task)
  }

  def addEdge(edge: Edge): Unit = {
    edges() = edges.now :+ Var(edge)
  }

  def addVehicle(pilotedVehicle: PilotedVehicle): Unit = {
    vehicles() = vehicles.now :+ Var(pilotedVehicle)
  }
}
