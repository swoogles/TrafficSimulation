package client


import com.billding.physics.{South, Spatial}
import com.billding.traffic._
import org.scalajs.dom
import org.scalajs.dom.html.Input
import org.scalajs.dom.raw.{HTMLElement, HTMLInputElement}

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

import scala.util.{Failure, Success}

case class CreateControlElements(buttonBehaviors: ButtonBehaviors) {
  def createButtons(element: HTMLElement) = {
    val columnDiv = div(
      cls := "col-md-6 text-center"
    ).render
    val buttonStyleClasses = "bttn-simple bttn-md bttn-primary lightly-padded"

    columnDiv.appendChild(
      input(
        tpe := "button",
        cls := buttonStyleClasses,
        value := "Pause",
        onclick := buttonBehaviors.togglePause
      ).render
    )

    columnDiv.appendChild(
      input(
        tpe := "button",
        cls := buttonStyleClasses,
        value := "Reset the scene!",
        onclick := buttonBehaviors.initiateSceneReset
      ).render
    )

    columnDiv.appendChild(
      input(
        tpe := "button",
        cls := buttonStyleClasses,
        value := "Serialize the scene",
        onclick := buttonBehaviors.initiateSceneSerialization
      ).render
    )

    columnDiv.appendChild(
      input(
        tpe := "button",
        cls := buttonStyleClasses,
        value := "Deserialize the scene",
        onclick := buttonBehaviors.initiateSceneDeserialization
      ).render
    )

    columnDiv.appendChild(
      input(
        cls := buttonStyleClasses,
        tpe := "button",
        value := "Disrupt the flow",
        onclick := buttonBehaviors.toggleDisrupt
      ).render
    )

    columnDiv.appendChild(
      input(
        cls := buttonStyleClasses,
        tpe := "button",
        value := "Disrupt the flow Existing",
        onclick := buttonBehaviors.toggleDisruptExisting
      ).render
    )

    element.appendChild(columnDiv)
  }

  def createSliders(element: HTMLElement) = {
    val columnDiv = div(
      cls := "col-md-6 text-center"
    )(
      button(buttonBehaviors.model.carTimingText).render,

      input(
        tpe := "range",
        min := 1,
        max := 10,
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
  val originalScene: Var[SceneImpl],
  val speed: Var[Velocity] = Var(KilometersPerHour(50)),
  val carTiming: Var[Time] = Var(Seconds(3)),
  val paused: Var[Boolean] = Var(false),
  val disruptLane: Var[Boolean] = Var(false),
  val disruptLaneExisting: Var[Boolean] = Var(false),
  val resetScene: Var[Boolean] = Var(false),
  val serializeScene: Var[Boolean] = Var(false),
  val deserializeScene: Var[Boolean] = Var(false),
  val vehicleCount: Var[Int] = Var(0)
                 ) {
  val savedScene = Var(originalScene)
  val carTimingText: Rx.Dynamic[String] = Rx(s"Current car timing ${carTiming()} ")
  val carSpeedText: Rx.Dynamic[String] = Rx(s"Current car speed ${speed()} ")
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
  val carTiming: Var[Time] = Var(Seconds(3))
  val carTimingText = Rx(s"Current car timing ${carTiming()} ")
  val carSpeedText = Rx(s"Current car speed ${speed()} ")
  val paused = Var(false)
  val disruptLane = Var(false)
  val disruptLaneExisting = Var(false)
  val resetScene: Var[Boolean] = Var(false)
  val serializeScene = Var(false)
  val deserializeScene = Var(false)
  val vehicleCount = Var(0)

  val street = Street(Seconds(2), originSpatial, endingSpatial, South, speed.now, 1 )

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
  val model = Model(Var(originalScene))
  val buttonBehaviors = ButtonBehaviors(model)
  val controlElements = CreateControlElements(buttonBehaviors)

  val savedScene = Var(originalScene)

  // Just a snippet to remind me how to pass html parameters around
  val startingColor = modifier(
    color := "blue"
  )

  val car =
    PilotedVehicle.commuter(Spatial.BLANK, new IntelligentDriverModelImpl, Spatial.BLANK)

  def updateTimingSlider(newTiming: Int): Unit = {
    carTiming() = Seconds(newTiming)
  }

  def updateSpeedSlider(newTiming: Int): Unit = {
    speed() = KilometersPerHour(newTiming)
  }

  val togglePause = (e: dom.Event) => {
    val elementClicked = e.target.asInstanceOf[HTMLInputElement]
    elementClicked.value = if (paused.now == true) "Pause" else "Unpause"
    paused() = !paused.now
  }

  val toggleDisrupt =
    (e: dom.Event) => disruptLane() = true

  val toggleDisruptExisting =
    (e: dom.Event) => disruptLaneExisting() = true

  val initiateSceneReset =
    (e: dom.Event) => resetScene() = true

  val initiateSceneSerialization =
    (e: dom.Event) => serializeScene() = true

  val initiateSceneDeserialization = (e: dom.Event) => {
    deserializeScene() = true
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

  def createButtons(element: HTMLElement) = {
    val columnDiv = div(
      cls := "col-md-6 text-center"
    ).render
    val buttonStyleClasses = "bttn-simple bttn-md bttn-primary lightly-padded"

    columnDiv.appendChild(
      input(
        tpe := "button",
        cls := buttonStyleClasses,
        value := "Pause",
        onclick := togglePause
      ).render
    )

    columnDiv.appendChild(
      input(
        tpe := "button",
        cls := buttonStyleClasses,
        value := "Reset the scene!",
        onclick := initiateSceneReset
      ).render
    )

    columnDiv.appendChild(
      input(
        tpe := "button",
        cls := buttonStyleClasses,
        value := "Serialize the scene",
        onclick := initiateSceneSerialization
      ).render
    )

    columnDiv.appendChild(
      input(
        tpe := "button",
        cls := buttonStyleClasses,
        value := "Deserialize the scene",
        onclick := initiateSceneDeserialization
      ).render
    )

    columnDiv.appendChild(
      input(
        cls := buttonStyleClasses,
        tpe := "button",
        value := "Disrupt the flow",
        onclick := toggleDisrupt
      ).render
    )

    columnDiv.appendChild(
      input(
        cls := buttonStyleClasses,
        tpe := "button",
        value := "Disrupt the flow Existing",
        onclick := toggleDisruptExisting
      ).render
    )

    element.appendChild(columnDiv)
  }

  def createSliders(element: HTMLElement) = {
    val columnDiv = div(
      cls := "col-md-6 text-center"
    )(
      button(carTimingText).render,

      input(
        tpe := "range",
        min := 1,
        max := 10,
        value := 3,
        oninput := updateSlider
      ).render,

      button(
      )(carSpeedText).render,

      input(
        id := "speedSlider",
        tpe := "range",
        min := 20,
        max := 80,
        value := 65,
        step := 5,
        oninput := speedSliderUpdate
      ).render
    ).render
    element.appendChild(columnDiv)
  }


  @JSExport
  def run() {
    val nodes = Seq( )
    val edges = Seq( )
    val millisecondsPerRefresh = 500

    val allControls = div(
      cls := "container"
    ).render

    val buttonPanel = div(
      id := "button-panel",
      cls := "row"
    ).render
//    controlElements.createButtons(buttonPanel)
    createButtons(buttonPanel)
    allControls.appendChild(buttonPanel)
    val sliderPanel = div(
      id := "slider-panel",
      cls := "row"
    ).render
    createSliders(sliderPanel)
    allControls.appendChild(sliderPanel)
    dom.document.body.appendChild(allControls)

    div(
      id := "button-panel"
    )

    var sceneVolatile: SceneImpl = originalScene
    var sceneVar = Var(originalScene)
    var window = new Window(sceneVar.now, nodes, edges)
    dom.window.setInterval(() => {
      if (resetScene.now == true) {
        sceneVar() = originalScene
        resetScene() = false
        window = new Window(sceneVar.now, nodes, edges)
        window.svgNode.forceRedraw()
      } else if (paused.now == false) {
//        println("sceneSize: " + sceneVar.now.streets.flatMap(_.lanes.map(_.vehicles.length)).sum )
        GLOBAL_T = sceneVar.now.t

        val newStreets = sceneVar.now.streets.map { street: Street =>
          val newLanes: List[LaneImpl] =
            street.lanes.map(lane => {
              // TODO Move this to match other UI response conditionals above.
              val laneAfterDisruption = if (disruptLane.now == true) {
                disruptLane() = false
                lane.addDisruptiveVehicle(car)
              } else {
                lane
              }
              val laneAfterDisruptionExisting = if (disruptLaneExisting.now == true) {
                disruptLaneExisting() = false
                laneAfterDisruption.disruptVehicles()
              } else {
                laneAfterDisruption
              }
              val newSource = laneAfterDisruptionExisting.vehicleSource.copy(spacingInTime = carTiming.now).updateSpeed(speed.now)
              laneAfterDisruptionExisting.copy(vehicleSource = newSource)
            })
          street.copy(lanes = newLanes)
        }
        sceneVar() = sceneVar.now.copy(streets = newStreets)

        sceneVar() = sceneVar.now.update(speedLimit)
        window = new Window(sceneVar.now, nodes, edges)
        window.svgNode.forceRedraw()
      }
      if (serializeScene.now == true) {
        savedScene() = sceneVar.now
        val f = Ajax.post("http://localhost:8080/writeScene", data=sceneVar.now.toString)
        f.onComplete {
          case Success(xhr) =>
          case Failure(cause) => println("failed: " + cause)
        }
        serializeScene() = false
      }
      if (deserializeScene.now == true) {
        val f = Ajax.get("http://localhost:8080/loadScene", data=sceneVar.now.toString)
        f.onComplete {
          case Success(xhr) => {
            try {
              val deserializedScene = xhr.responseText.asInstanceOf[SceneImpl]
              // Wish this was working :/
              //              sceneVar() = deserializedScene
              sceneVar() = savedScene.now
              window = new Window(sceneVar.now, nodes, edges)
              window.svgNode.forceRedraw()
              paused() = true
            } catch {
              case ex: Exception => println("exception: " + ex)
            }
          }

          case Failure(cause) => println("failed: " + cause)
        }
        deserializeScene() = false
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
