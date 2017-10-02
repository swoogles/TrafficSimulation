package fr.iscpif.client


import com.billding.physics.Spatial
import com.billding.traffic._
import org.scalajs.dom

import scala.concurrent.Future
import squants.{Length, Time}

import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.runNow
import squants.motion._
import squants.space.{Kilometers, LengthUnit, Meters}
import squants.time.{Milliseconds, Seconds}
import rx._

import scaladget.tools.JsRxTags._
import scalatags.JsDom.all._
import org.scalajs.dom.ext.Ajax
import play.api.libs.json.Json

import scala.util.{Failure, Success}

@JSExportTopLevel("Client")
object Client {
  var GLOBAL_T: Time = Seconds(0)

  val idm: IntelligentDriverModel = new IntelligentDriverModelImpl
  val speedLimit = KilometersPerHour(65)

  val zeroDimensions: (Double, Double, Double, LengthUnit) = (0, 2, 0, Meters)
  val originSpatial = Spatial((0, 0, 0, Meters))
  val endingSpatial = Spatial((0.5, 0, 0, Kilometers))

  val herdSpeed = 65

  val speed = Var(KilometersPerHour(50))

  val street = Street(Seconds(2), originSpatial, endingSpatial, speed.now, 1)

  val t = Seconds(0)
  val canvasDimensions: (Length, Length) = (Kilometers(.25), Kilometers(.5))
  implicit val DT = Milliseconds(20)
  val originalScene: SceneImpl = SceneImpl(
    List(street),
    t,
    DT,
    speedLimit,
    canvasDimensions
  )
  val model = Model(originalScene)
  val buttonBehaviors = ButtonBehaviors(model)
  val controlElements = CreateControlElements(buttonBehaviors)


  // Just a snippet to remind me how to pass html parameters around
  val startingColor = modifier(
    color := "blue"
  )

  val car =
    PilotedVehicle.commuter(Spatial.BLANK, new IntelligentDriverModelImpl, Spatial.BLANK)

  def disruptLane(lane: LaneImpl, model: Model) =
    if (model.disruptLane.now == true) {
      model.disruptLane() = false
      lane.addDisruptiveVehicle(car)
    } else {
      lane
    }

  def disruptLaneExisting(lane: LaneImpl, model: Model) =
    if (model.disruptLaneExisting.now == true) {
      model.disruptLaneExisting() = false
      lane.disruptVehicles()
    } else {
      lane
    }

  def updateLane(lane: LaneImpl) = {
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

    val window: Var[Window] = Var(new Window(model.sceneVar.now))
    dom.window.setInterval(() => {
      resetIfNecessary(model, window)
      if (model.paused.now == false) {
        GLOBAL_T = model.sceneVar.now.t

        val newStreets = model.sceneVar.now.streets.map { street: StreetImpl =>
          val newLanes: List[LaneImpl] = street.lanes.map(updateLane)
          street.copy(lanes = newLanes)
        }
        model.updateSceneWithStreets(newStreets)
        model.updateScene(speedLimit)

        window() = new Window(model.sceneVar.now)
        window.now.svgNode.forceRedraw()
      }
      serializeIfNecessary(model)
      deserializeIfNecessary(model, window)

    }, DT.toMilliseconds / 5) // TODO Make this understandable and easily modified. Just some simple algebra.
  }

  def resetIfNecessary(model: Model, window: Var[Window]): Unit =
    if (model.resetScene.now == true) {
        model.sceneVar() = model.originalScene
        model.resetScene() = false
        window() = new Window(model.sceneVar.now)
        window.now.svgNode.forceRedraw()
      }

  /**
    * TODO: Deserialization is killing the vehicle source now. Not sure when that was introduced.
    */
  def deserializeIfNecessary(model: Model, window: Var[Window]): Unit = {
    if (model.deserializeScene.now == true) {
      val f = Ajax.get("http://localhost:8080/loadScene")
      f.onComplete {
        case Success(xhr) => {
          import com.billding.serialization.TrafficJson.defaultSerialization.sceneFormats
          val res = Json.fromJson(
            Json.parse(xhr.responseText  )
          ).get
          model.sceneVar() = res
          window() = new Window(model.sceneVar.now)
          window.now.svgNode.forceRedraw()
          model.paused() = true
        }

        case Failure(cause) => println("failed: " + cause)
      }
      model.deserializeScene() = false
    }
  }


  def serializeIfNecessary(model: Model): Unit = {
    if (model.serializeScene.now == true) {
      model.savedScene() = model.sceneVar.now
      import com.billding.serialization.TrafficJson.defaultSerialization.sceneFormats

      val f = Ajax.post("http://localhost:8080/writeScene", data = Json.toJson(model.sceneVar.now).toString)
      f.onComplete {
        case Success(xhr) => println("serialized some stuff and sent it off")
        case Failure(cause) => println("failed: " + cause)
      }
      model.serializeScene() = false
    }
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

  def read[Result: upickle.default.Reader](p: String) = upickle.default.read[Result](p)

  def write[Result: upickle.default.Writer](r: Result) = upickle.default.write(r)
}
