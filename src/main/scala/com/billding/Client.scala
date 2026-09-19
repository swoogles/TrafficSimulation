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
        scenes.lopsidedTwoLaneRing,
        scenes.singleRoadNetwork
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

  /**
    * Every current touch, in the pixel coordinate space [[Window]] and [[Camera]] share -
    * `svgContainer.clientWidth` by [[availableHeight]] - rather than in the raw `clientX`/
    * `clientY` a `Touch` reports, which are page pixels and only agree with that space if the
    * container happens to render at exactly its own client size.
    */
  private def touchPoints(
    touchList: dom.TouchList,
    svgContainer: Element
  ): List[(Int, Double, Double)] = {
    val rect = svgContainer.getBoundingClientRect()
    val scaleX = if (rect.width <= 0) 1.0 else svgContainer.clientWidth / rect.width
    val scaleY = if (rect.height <= 0) 1.0 else availableHeight(svgContainer) / rect.height

    (0 until touchList.length).map { i =>
      val touch = touchList(i)
      // `identifier` comes back as a Double (every JS number does) even though it's always a
      // small whole number in practice - Model's touch maps key on Int.
      (touch.identifier.toInt, (touch.clientX - rect.left) * scaleX, (touch.clientY - rect.top) * scaleY)
    }.toList
  }

  // Currently this needs access to the window
  def setupSvgAndButtonResponses(svgContainer: Element): Int = {
    println("!1 svgContainer height: " + svgContainer.clientHeight)
    println("!1 svgContainer width: " + svgContainer.clientWidth)

    // Create a reactive window that updates when the scene advances or the camera moves -
    // I3's pan/pinch only ever touches model.camera, so a window that watched sceneVar alone
    // would sit on the old view until the next simulation tick happened to redraw it.
    val windowSignal: Signal[Window] = sceneVar.combineWith(model.camera.signal).map {
      case (scene, camera) =>
        new Window(scene, svgContainer.clientWidth, availableHeight(svgContainer), camera)
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

    /*
    Two-finger pan/pinch (I3, creativity_plan's camera-navigation row). Listeners live on
    `svgContainer` itself rather than on the `<svg>` `windowSignal` swaps in and out - that
    inner node is torn down and rebuilt on every scene tick (and now on every camera move too),
    so touch identifiers tracked against it would be lost mid-gesture the moment a tick landed.
    `svgContainer` is the one node that survives every rebuild.

    Ownership is decided by touch count alone, per the plan: exactly two fingers drive the
    camera, any other count (0, 1, or 3+) leaves it alone. Model.touchChanged resets the
    baseline on every start/end/cancel without moving the camera, so a finger added or removed
    can't make the view jump; Model.touchMoved is the only thing that ever calls
    Camera.followingTouches.
     */
    svgContainer.addEventListener("touchstart", { event: dom.TouchEvent =>
      model.touchChanged(touchPoints(event.touches, svgContainer))
    })
    svgContainer.addEventListener("touchend", { event: dom.TouchEvent =>
      model.touchChanged(touchPoints(event.touches, svgContainer))
    })
    svgContainer.addEventListener("touchcancel", { event: dom.TouchEvent =>
      model.touchChanged(touchPoints(event.touches, svgContainer))
    })
    svgContainer.addEventListener("touchmove", { event: dom.TouchEvent =>
      val points = touchPoints(event.touches, svgContainer)
      // Only a two-finger move is ours to consume - a single finger is left free to scroll
      // the page (phase J will give it a job of its own; nothing does yet).
      if (points.size == 2) event.preventDefault()
      model.touchMoved(points, svgContainer.clientWidth, availableHeight(svgContainer))
    })

    // requestAnimationFrame hands the callback a DOMHighResTimeStamp (ms since navigation
    // start), not a delta - the delta since the previous callback is what the accumulator in
    // Model.respondToAllInput wants. There's no previous callback on the very first frame, so
    // that one reports zero elapsed time rather than the time since navigation started, which
    // would otherwise look like a startup stall and immediately burn through catch-up steps.
    var lastFrameTimeMillis: Option[Double] = None

    def callback: js.Function1[Double, Unit] = (timeMillis) => {
      val elapsed = Milliseconds(lastFrameTimeMillis.fold(0.0)(timeMillis - _))
      lastFrameTimeMillis = Some(timeMillis)

      // Measured every frame, the same reasoning availableHeight itself documents: a camera
      // fit made against a stale size would be wrong the moment the window (or a phone's
      // orientation) changed.
      model.noteCanvasSize(svgContainer.clientWidth, availableHeight(svgContainer))
      model.respondToAllInput(elapsed)

      dom.window.requestAnimationFrame(callback)
    }
    dom.window.requestAnimationFrame(callback)

    0 // Return value
  }

}
