package client


import com.billding.physics.Spatial
import com.billding.traffic._
import shared.Orientation
import org.scalajs.dom
import org.scalajs.dom.Event
import org.scalajs.dom.html.{Div, Input}
import org.scalajs.dom.raw.{HTMLElement, HTMLInputElement, Node}

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

object ApiImpl extends shared.Api {
}


case class CreateControlElements(buttonBehaviors: ButtonBehaviors) {
  def createButtons(): Div = {
    val buttonBaseClasses = "bttn-simple bttn-md lightly-padded"

    val buttonStyleClasses = buttonBaseClasses + " bttn-primary"
    val dangerButtonClasses = buttonBaseClasses + " bttn-danger"

    def button(styleClasses: String): (String, (Event) => Unit ) => Input =
      (content, behavior) =>
        input(
          tpe := "button",
          cls := styleClasses,
          value := content,
          onclick := behavior
        ).render

    val normalButton = button(buttonStyleClasses)
    val dangerButton = button(dangerButtonClasses)

    div(
      cls := "col-md-6 text-center"
    )(
      normalButton("Pause", buttonBehaviors.togglePause),
      normalButton("Reset the scene!", buttonBehaviors.initiateSceneReset),
      normalButton("Serialize the scene", buttonBehaviors.initiateSceneSerialization),
      normalButton("Deserialize the scene", buttonBehaviors.initiateSceneDeserialization),
      dangerButton("Disrupt the flow", buttonBehaviors.toggleDisrupt),
      dangerButton("Disrupt the flow Existing", buttonBehaviors.toggleDisruptExisting)
    ).render
  }

  def createSliders(element: HTMLElement) = {
    val columnDiv = div(
      cls := "col-md-6 text-center"
    )(
      button(buttonBehaviors.model.carTimingText).render,

      input(
        tpe := "range",
        min := 1,
        max := 5,
        value := 3,
        oninput := buttonBehaviors.updateSlider
      ).render,

      button(
      )(buttonBehaviors.model.carSpeedText).render,

      input(
        id := "speedSlider",
        tpe := "range",
        min := 20,
        max := 80,
        value := 65,
        step := 5,
        oninput := buttonBehaviors.speedSliderUpdate
      ).render
    ).render
    element.appendChild(columnDiv)
  }

  def createLayout(element: HTMLElement) = {
    val allControls = div(
      cls := "container"
    ).render

    val buttonPanel = div(
      id := "button-panel",
      cls := "row"
    ).render
    buttonPanel.appendChild(createButtons())
    allControls.appendChild(buttonPanel)
    val sliderPanel = div(
      id := "slider-panel",
      cls := "row"
    ).render
    createSliders(sliderPanel)
    allControls.appendChild(sliderPanel)
    element.appendChild(allControls)
  }
}

case class ButtonBehaviors(val model: Model) {
  val togglePause = (e: dom.Event) => {
    val elementClicked = e.target.asInstanceOf[HTMLInputElement]
    elementClicked.value = if (model.paused.now == true) "Pause" else "Unpause"
    model.paused() = !model.paused.now
  }

  val toggleDisrupt =
    (e: dom.Event) => model.disruptLane() = true

  val toggleDisruptExisting =
    (e: dom.Event) => model.disruptLaneExisting() = true

  val initiateSceneReset =
    (e: dom.Event) => model.resetScene() = true

  val initiateSceneSerialization =
    (e: dom.Event) => model.serializeScene() = true

  val initiateSceneDeserialization = (e: dom.Event) => {
    model.deserializeScene() = true
  }

  def updateTimingSlider(newTiming: Int): Unit = {
    model.carTiming() = Seconds(newTiming)
  }

  def updateSpeedSlider(newTiming: Int): Unit = {
    model.speed() = KilometersPerHour(newTiming)
  }

  val updateSlider = (e: dom.Event) => {
    val value = e.target match {
      case inputElement: Input  => inputElement.value.toInt
      case _ => 3 // TODO de-magick this
    }
    updateTimingSlider(value)
  }

  val speedSliderUpdate = (e: dom.Event) => {
    val value = e.target match {
      case inputElement: Input  => inputElement.value.toInt
      case _ => 65 // TODO de-magick this
    }
    updateSpeedSlider(value)
  }
}

case class Model (
  originalScene: SceneImpl,
  speed: Var[Velocity] = Var(KilometersPerHour(50)),
  carTiming: Var[Time] = Var(Seconds(3)),
  paused: Var[Boolean] = Var(false),
  disruptLane: Var[Boolean] = Var(false),
  disruptLaneExisting: Var[Boolean] = Var(false),
  resetScene: Var[Boolean] = Var(false),
  serializeScene: Var[Boolean] = Var(false),
  deserializeScene: Var[Boolean] = Var(false),
  vehicleCount: Var[Int] = Var(0)
) {
  val savedScene: Var[SceneImpl] = Var(originalScene)
  var sceneVar: Var[SceneImpl] = Var(originalScene)
  val carTimingText: Rx.Dynamic[String] = Rx(s"Current car timing ${carTiming()} ")
  val carSpeedText: Rx.Dynamic[String] = Rx(s"Current car speed ${speed()} ")

  def updateSceneWithStreets(streets: List[StreetImpl]) =
    sceneVar() = sceneVar.now.copy(streets = streets)

  def updateScene(speedLimit: Velocity)(implicit dt: Time) =
    sceneVar() = sceneVar.now.update(speedLimit)
}

@JSExportTopLevel("Client")
/*
TODO: Address my concern of traits vs CC's
  I should keep *all* implementations out of traits as much as possible
  Then I avoid the need to explicitly invoke the copy constructor.
*/
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

  @JSExport
  def run() {
    val millisecondsPerRefresh = 500

    controlElements.createLayout(dom.document.body)

    var window = new Window(model.sceneVar.now)
    dom.window.setInterval(() => {
      if (model.resetScene.now == true) {
        model.sceneVar() = model.originalScene
        model.resetScene() = false
        window = new Window(model.sceneVar.now)
        window.svgNode.forceRedraw()
      } else if (model.paused.now == false) {
        GLOBAL_T = model.sceneVar.now.t

        val newStreets = model.sceneVar.now.streets.map { street: StreetImpl =>
          val newLanes: List[LaneImpl] =
            street.lanes.map(lane => {
              // TODO Move this to match other UI response conditionals above.
              val laneAfterDisruption = if (model.disruptLane.now == true) {
                model.disruptLane() = false
                lane.addDisruptiveVehicle(car)
              } else {
                lane
              }
              val laneAfterDisruptionExisting = if (model.disruptLaneExisting.now == true) {
                model.disruptLaneExisting() = false
                laneAfterDisruption.disruptVehicles()
              } else {
                laneAfterDisruption
              }
              val newSource = laneAfterDisruptionExisting.vehicleSource
                .copy(spacingInTime = model.carTiming.now)
                .updateSpeed(model.speed.now)
              laneAfterDisruptionExisting.copy(vehicleSource = newSource)
            })
          street.copy(lanes = newLanes)
        }
        model.updateSceneWithStreets(newStreets)
        model.updateScene(speedLimit)

        window = new Window(model.sceneVar.now)
        window.svgNode.forceRedraw()
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
      serializeIfNecessary(model)

      if (model.deserializeScene.now == true) {
        val f = Ajax.get("http://localhost:8080/loadScene")
        f.onComplete {
          case Success(xhr) => {
            import com.billding.serialization.TrafficJson.defaultSerialization.sceneFormats
            val res = Json.fromJson(
              Json.parse(xhr.responseText  )
            ).get
            model.sceneVar() = res
            window = new Window(model.sceneVar.now)
            window.svgNode.forceRedraw()
            model.paused() = true
          }

          case Failure(cause) => println("failed: " + cause)
        }
        model.deserializeScene() = false
      }

    }, DT.toMilliseconds / 5) // TODO Make this understandable and easily modified. Just some simple algebra.
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

//import play.api.libs.json.{Format, Json, Reads, Writes}
//object PostJson extends autowire.Client[String, Reads, Writes] {
//
//  override def doCall(req: Request): Future[String] = {
//    val url = req.path.mkString("/")
//    dom.ext.Ajax.post(
//      url = "http://localhost:8080/" + url,
//      data = upickle.default.write(req.args)
//    ).map {
//      _.responseText
//    }
//  }
//
//  def read[JsValue: Reads](p: String) = Json.parse(p)
//
//  def write[Result: Writes](r: Result) = upickle.default.write(r)
//}
