package fr.iscpif.client


import com.billding.physics.Spatial
import com.billding.traffic._
import org.scalajs.dom
import org.scalajs.dom.Element
import squants.motion.KilometersPerHour

import scala.concurrent.Future
import squants.{Length, Time}

import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.runNow
import squants.space.Kilometers
import squants.time.{Milliseconds, Seconds}
import rx._

import scaladget.tools.JsRxTags._
import scalatags.JsDom.all._

import scalatags.generic

@JSExportTopLevel("Client")
object Client {
  val GLOBAL_T: Var[Time] = Var(Seconds(0))

  val speedLimit = KilometersPerHour(65)

  val originSpatial = Spatial((0, 0, 0, Kilometers))
  val endingSpatial = Spatial((0.5, 0, 0, Kilometers))

  val speed = Var(KilometersPerHour(50))

  val street = Street(Seconds(2), originSpatial, endingSpatial, speed.now, 1)

  val canvasDimensions: (Length, Length) = (Kilometers(.25), Kilometers(.5))
  implicit val DT = Milliseconds(20)
  val originalScene: SceneImpl = SceneImpl(
    List(street),
    GLOBAL_T.now,
    DT,
    speedLimit,
    canvasDimensions
  )
  val model = Model(originalScene)
  val buttonBehaviors = ButtonBehaviors(model)
  val controlElements = CreateControlElements(buttonBehaviors)


  // Just a snippet to remind me how to pass html parameters around
  val startingColor: generic.Modifier[Element] = modifier(
    color := "blue"
  )

  val car: PilotedVehicleImpl =
    PilotedVehicle.commuter(Spatial.BLANK, new IntelligentDriverModelImpl)

  def disruptLane(lane: LaneImpl, model: Model): LaneImpl =
    if (model.disruptLane.now == true) {
      model.disruptLane() = false
      lane.addDisruptiveVehicle(car)
    } else {
      lane
    }

  def disruptLaneExisting(lane: LaneImpl, model: Model): LaneImpl =
    if (model.disruptLaneExisting.now == true) {
      model.disruptLaneExisting() = false
      lane.disruptVehicles()
    } else {
      lane
    }

  def updateLane(lane: LaneImpl, model: Model): LaneImpl = {
    // TODO Move this to match other UI response conditionals above.

    val laneAfterDisruption = disruptLane(lane, model)
    val laneAfterDisruptionExisting = disruptLaneExisting(laneAfterDisruption, model)

    val newSource =
      laneAfterDisruptionExisting.vehicleSource
        .copy(spacingInTime = model.carTiming.now)
        .updateSpeed(model.speed.now)
    laneAfterDisruptionExisting.copy(vehicleSource = newSource)
  }

  @JSExport
  def run() {
    controlElements.createLayout(dom.document.body)

    // TODO figure out how to simply pass model.sceneVar here
    val window: Var[Window] = Var(new Window(model.sceneVar.now))
    dom.window.setInterval(() => {
      window() = resetIfNecessary(model, window)
      if (model.paused.now == false) {
        // Figure out more direct way of making this connection between t's
        GLOBAL_T() = model.sceneVar.now.t

        val newStreets = model.sceneVar.now.streets.map { street: StreetImpl =>
          val newLanes: List[LaneImpl] = street.lanes.map(updateLane(_, model))
          street.copy(lanes = newLanes)
        }
        model.updateSceneWithStreets(newStreets)
        model.updateScene(speedLimit)

        window() = new Window(model.sceneVar.now)
        window.now.svgNode.forceRedraw()
      }
      serialization.serializeIfNecessary(model)
      serialization.deserializeIfNecessary(model, window)

    }, DT.toMilliseconds / 5) // TODO Make this understandable and easily modified. Just some simple algebra.
  }

  def resetIfNecessary(model: Model, window: Var[Window]): Window =
    if (model.resetScene.now == true) {
      model.reset
      val newWindow = new Window(model.sceneVar.now)
      newWindow.svgNode.forceRedraw()
      newWindow
    } else {
      window.now
    }

}

object Post extends autowire.Client[String, upickle.default.Reader, upickle.default.Writer] {

  override def doCall(req: Request): Future[String] = {
    val url = req.path.mkString("/")
    dom.ext.Ajax.post(
      url = "http://localhost:8080/" + url,
      data = upickle.default.write(req.args)
    ).map {
      _.responseText
    }
  }

  def read[Result: upickle.default.Reader](p: String): Result = upickle.default.read[Result](p)

  def write[Result: upickle.default.Writer](r: Result): String = upickle.default.write(r)
}
