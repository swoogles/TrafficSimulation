package com.billding

import com.billding.physics.{Spatial, SpatialFor}
import com.billding.serialization.BillSquants
import com.billding.traffic.{
  Driver,
  Lane,
  PilotedVehicle,
  Scene,
  Street,
  StreetScene,
  Vehicle,
  VehicleSourceImpl
}
import com.billding.uimodules.Model
import squants.motion.{Acceleration, Distance}
import org.scalajs.dom
import org.scalajs.dom.raw.{Element, Node}

import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}
import com.raquo.laminar.api.L.{Observer, Signal}
import com.raquo.airstream.ownership.Owner
import play.api.libs.json.{Format, Json}
import squants.{Mass, QuantityVector, Time, Velocity}
import squants.space.Kilometers
import squants.time.Milliseconds

import scala.scalajs.js

@JSExportTopLevel("Client")
object Client {

  val originSpatial = Spatial((0, 0, 0, Kilometers))
  val endingSpatial = Spatial((0.5, 0, 0, Kilometers))

  implicit val DT: Time = Milliseconds(100)
  val scenes = new SampleSceneCreation(endingSpatial)

  /*
  The landing scene is a two-lane ring rather than a single car on a straight road, because
  the straight road is one lane: nothing on it can change lanes, so everything the simulation
  has learned to show about lane changing was invisible until you went looking through the
  presets for a road that had two of them.

  The lopsided one rather than the denser, more interesting ones, because it is the only one
  that shows you anything soon enough. Given the traffic thirty seconds - about as long as
  anyone gives a page before deciding it is a still picture - the free-flowing ring changes
  lanes not at all, the standing-wave ring is too tightly packed for a gap to be worth taking
  and also changes lanes not at all, and this one has a driver indicating about a twelfth of
  the time. An imbalance is what gives anybody a reason to move.
   */
  val model: Model =
    Model(
      scenes.lopsidedTwoLaneRing.scene,
      List(
        scenes.emptyScene,
        scenes.scene1,
        scenes.scene2,
        scenes.multipleStoppedGroups,
        scenes.quietRing,
        scenes.busyRing,
        scenes.jammedRing,
        scenes.quietTwoLaneRing,
        scenes.waveProneTwoLaneRing,
        scenes.lopsidedTwoLaneRing
      ),
      new SerializationFeatures("localhost", 8080, "http")
    )

  val sceneVar: Signal[Scene] = model.sceneVar.signal

  // Lazy so that merely loading this module doesn't touch the DOM. Client is a top-level
  // export, so the test runner initializes it too, where there is no document to render into.
  lazy val controlElements: ControlElements =
    ControlElements(
      ButtonBehaviors(model)
    )

  // Should directly use sceneVar
  val GLOBAL_T: Signal[Time] = sceneVar.map(_.t)

  implicit val df: Format[Distance] = BillSquants.distance.format
  implicit val tf: Format[Time] = BillSquants.time.format
  implicit val vf: Format[Velocity] = BillSquants.velocity.format

  implicit val dQvf: Format[QuantityVector[Distance]] =
    BillSquants.distance.formatQv

  implicit val vQvf: Format[QuantityVector[Velocity]] =
    BillSquants.velocity.formatQv
  implicit val spatialFormat: Format[Spatial] = Json.format[Spatial]
  implicit val driverFormat: Format[Driver] = Json.format[Driver]
  implicit val mf: Format[Mass] = BillSquants.mass.format
  implicit val af: Format[Acceleration] = BillSquants.acceleration.format

  implicit val vehicleFormat: Format[Vehicle] = Json.format[Vehicle]

  implicit val spatialForPilotedVehicle: SpatialFor[PilotedVehicle] = {
    case vehicle: PilotedVehicle => vehicle.spatial
  }

  implicit val pilotedVehicleFormat: Format[PilotedVehicle] =
    Json.format[PilotedVehicle]

  implicit val vehicleSourceFormat: Format[VehicleSourceImpl] =
    Json.format[VehicleSourceImpl]

  implicit val laneFormat: Format[Lane] = Json.format[Lane]
  implicit val streetFormat: Format[Street] = Json.format[Street]

  // Only the street scene round-trips: a ring is described by its shape and how many cars
  // are on it, which is a different thing to serialize and nothing reads it yet.
  implicit val sceneFormats: Format[StreetScene] = Json.format[StreetScene]

  @JSExport
  def run(): Unit = {
    println("DT: " + DT)
    val controlsContainer = dom.document.getElementById("controls-container")
    controlsContainer.appendChild(controlElements.createLayout())
    val svgContainerAttempt: Option[Element] = Option(dom.document.getElementById("svg-container"))
    svgContainerAttempt match {
      case Some(svgContainer) => setupSvgAndButtonResponses(svgContainer)
      case None =>
        println("We can't do any svg setup on a page that doesn't have a container to hold it.");
    }
  }

  // Currently this needs access to the window
  def setupSvgAndButtonResponses(svgContainer: Element): Int = {
    println("!1 svgContainer height: " + svgContainer.clientHeight)
    println("!1 svgContainer width: " + svgContainer.clientWidth)

    // Create a reactive window that updates when scene changes
    val windowSignal: Signal[Window] = sceneVar.map { scene =>
      new Window(scene, svgContainer.clientWidth)
    }

    // Subscribe to scene changes and update SVG
    implicit val owner: Owner = new Owner {}
    val observer = Observer[Window] { window =>
      val previousSvg: Node = svgContainer.getElementsByTagName("svg").item(0)
      if (previousSvg != null) {
        svgContainer.removeChild(previousSvg)
      }
      svgContainer.appendChild(window.svgNode.render)
    }
    val subscription = windowSignal.addObserver(observer)

    def callback: js.Function1[Double, Unit] = (double) => {
      model.respondToAllInput()

      dom.window.requestAnimationFrame(callback)
    }
    dom.window.requestAnimationFrame(callback)

    0 // Return value
  }

}
