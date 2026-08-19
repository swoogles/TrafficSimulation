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
import com.raquo.laminar.api.L.{render, Observer, Signal}
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

  The lopsided one because it starts moving immediately: cars are indicating inside a second,
  where the free-flowing ring takes about twenty to produce its first change. Given the
  traffic thirty seconds - about as long as anyone gives a page before deciding it is a still
  picture - that is the difference between landing on traffic doing something and landing on
  traffic about to.

  It is worth knowing that this one is front-loaded, though. Its lane changes are the two
  lanes levelling out, so they are over inside fifteen seconds and it settles at eleven and
  nine and stops. The free-flowing ring is the opposite shape: slower to start, and then it
  keeps overtaking for as long as you leave it running. If this page ever wants a scene to sit
  and watch rather than a scene to arrive at, that is the one.
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
    // Mounted by Laminar rather than appended as a node, which is what puts the bindings in
    // the panel's labels under an owner and keeps them live.
    val _ = render(controlsContainer, controlElements.layout)
    val svgContainerAttempt: Option[Element] = Option(dom.document.getElementById("svg-container"))
    svgContainerAttempt match {
      case Some(svgContainer) => setupSvgAndButtonResponses(svgContainer)
      case None =>
        println("We can't do any svg setup on a page that doesn't have a container to hold it.");
    }
  }

  /**
    * How much of the window is left below the top of the canvas, less a share kept back for
    * the controls.
    *
    * Measured rather than assumed, and measured again on every frame, which is what makes
    * turning a phone on its side work without anybody listening for it: the canvas is rebuilt
    * from the scene each tick anyway, so it picks up the new window on the way past.
    *
    * The share held back is what stops the road filling the screen and pushing every button
    * off the bottom of it. It is the road's job to want less than this if it cannot use it -
    * a ring that has settled for a square canvas leaves far more than this showing.
    */
  private def availableHeight(svgContainer: Element): Int = {
    val topOfCanvas = svgContainer.getBoundingClientRect().top
    val window = dom.window.innerHeight

    val toTheBottomOfTheWindow = window - topOfCanvas - RoomForTheControls
    val neverMoreThanMostOfIt = window * MostOfTheWindow

    math.max(MinimumCanvasHeight, math.min(toTheBottomOfTheWindow, neverMoreThanMostOfIt)).toInt
  }

  /** At most this much of the window goes to the road, however much room there is. */
  private val MostOfTheWindow = 0.68

  /**
    * Room kept below the road for the controls: the bar and the readings under it.
    *
    * A fixed reserve rather than the panel's measured height on purpose. The panel grows when
    * you open a setting, and taking that off the road would resize the road at the exact
    * moment you opened a control in order to watch the road - so this is what the controls
    * take when none of them is open, and an opened one is allowed to run off the bottom of a
    * short window instead.
    *
    * Three rows now, not two: the counter and the rate sit above the buttons, so the reserve
    * went up by a row's worth when they arrived.
    */
  private val RoomForTheControls = 180

  /** Below this the drawing is not worth looking at, whatever the window is doing. */
  private val MinimumCanvasHeight = 200

  // Currently this needs access to the window
  def setupSvgAndButtonResponses(svgContainer: Element): Int = {
    println("!1 svgContainer height: " + svgContainer.clientHeight)
    println("!1 svgContainer width: " + svgContainer.clientWidth)

    // Create a reactive window that updates when scene changes
    val windowSignal: Signal[Window] = sceneVar.map { scene =>
      new Window(scene, svgContainer.clientWidth, availableHeight(svgContainer))
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
