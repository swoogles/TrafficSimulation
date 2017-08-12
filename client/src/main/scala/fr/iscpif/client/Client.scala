package client

import com.billding._
import com.billding.physics.{South, Spatial}
import com.billding.traffic._
import fr.iscpif.client.{GraphOriginal, WindowOriginal}
import org.scalajs.dom
import org.scalajs.dom.html.Input
import org.w3c.dom.html.HTMLInputElement

import scala.concurrent.Future
import rx._
import squants.{Length, Time}

import scala.scalajs.js.annotation.JSExport
import scala.scalajs.concurrent.JSExecutionContext.Implicits.runNow
import squants.motion._
import squants.space.{Kilometers, LengthUnit, Meters}
import squants.time.{Milliseconds, Seconds}

import scalacss.internal.Macros.Color
import scalacss.internal.mutable.{GlobalRegistry, Register, StyleSheet}
import scalatags.generic.Modifier
import rx._

import scaladget.tools.JsRxTags._
import scalatags.JsDom.all._
import scalacss.ScalatagsCss._

package object client {
  val CssSettings = scalacss.devOrProdDefaults
}

import client.CssSettings._

object OutterStyles {

  object TrafficStyles extends StyleSheet.Inline {
    import dsl._

    val blue: Color = c"#0000FF"
    val green = c"#00FF00"
    val currentColor = Var(blue)
  }

}

@JSExport("Client")
/*
TODO: Address my concern of traits vs CC's
  I should keep *all* implemntations out of traits as much as possible
  Then I avoid the need to explicitly invoke the copy constructor.
*/
object Client {
  var GLOBAL_T: Time = Seconds(0)

  val idm: IntelligentDriverModel = new IntelligentDriverModelImpl
  val speedLimit = KilometersPerHour(65)

  val zeroDimensions: (Double, Double, Double, LengthUnit) = (0, 2, 0, Meters)
  val originSpatial = Spatial((0, 0, 0, Meters))
  val endingSpatial = Spatial((100, 0, 0, Kilometers))

  val herdSpeed = 65

  val vehicles: List[PilotedVehicle] = List( )

  val speed = Var(KilometersPerHour(50))
  val street = Street(Seconds(2), originSpatial, endingSpatial, South, speed.now, 1 )

  val t = Seconds(0)
  val canvasDimensions: (Length, Length) = (Kilometers(.5), Kilometers(.25))
  implicit val DT = Milliseconds(20)
  val scene: SceneImpl = SceneImpl(
    List(street),
    t,
    DT,
    speedLimit,
    canvasDimensions
  )
  val carTiming: Var[Time] = Var(Seconds(3))

  val startingColor = modifier(
    color := "blue"
  )

  val mods = Var(startingColor)
  val c = Var("blue")
  val text = Rx(s"It is a ${c()} text!")
  val carTimingText = Rx(s"Current car timing ${carTiming()} ")
  val carSpeedText = Rx(s"Current car speed ${speed()} ")

  val disruptLane = Var(false)

  import OutterStyles.TrafficStyles
//  implicit val tolerance = Seconds(.1)

  def updateTimingSlider(newTiming: Int): Unit = {
    carTiming() = Seconds(newTiming)
  }

  def updateSpeedSlider(newTiming: Int): Unit = {
    speed() = KilometersPerHour(newTiming)
  }

  val ToggleDisrupt = (e: dom.Event) => {
    disruptLane() = true
  }

  val updateSlider = (e: dom.Event) => {
    val value = e.target match {
      case inputElement: Input  => inputElement.value.toInt
      case _ => 3
    }
    updateTimingSlider(value)
  }

  val speedSliderUpdate = (e: dom.Event) => {
    val value = e.target match {
      case inputElement: Input  => inputElement.value.toInt
      case _ => 65
    }
    updateSpeedSlider(value)
  }

  @JSExport
  def run() {
    val nodes = Seq( )
    val edges = Seq( )
    val millisecondsPerRefresh = 500

    import scalatags.JsDom.all._
      dom.document.body.appendChild(
        button(
        )(carTimingText).render
      )

    dom.document.body.appendChild(
      input(
        tpe := "button",
        value := "Disrupt the flow",
        oninput := updateSlider
        //      inputNumber --> sliderEvents,
      ).render
    )

    dom.document.body.appendChild(
      input(
        tpe := "range",
        min := 1,
        max := 10,
        value := 3,
        oninput := updateSlider
        //      inputNumber --> sliderEvents,
      ).render
    )

    dom.document.body.appendChild(
      button(
      )(carSpeedText).render
    )

    dom.document.body.appendChild(
      input(
        id := "speedSlider",
        tpe := "range",
        min := 20,
        max := 80,
        value := 65,
        step := 5,
        oninput := speedSliderUpdate
        //      inputNumber --> sliderEvents,
      ).render
    )

    var sceneVolatile: SceneImpl = scene
    var window = new Window(sceneVolatile, nodes, edges)
    dom.window.setInterval(() => {
      GLOBAL_T = sceneVolatile.t
      val newStreets = sceneVolatile.streets.map { street: Street =>
        val newLanes: List[LaneImpl] =
          street.lanes.map(lane => {
            val newSource = lane.vehicleSource.copy(spacingInTime = carTiming.now).updateSpeed(speed.now)
            lane.copy(vehicleSource = newSource)
          })
        street.copy(lanes = newLanes)
      }
      sceneVolatile = sceneVolatile.copy(streets = newStreets)

        sceneVolatile = sceneVolatile.update(speedLimit)
        window = new Window(sceneVolatile, nodes, edges)
        window.svgNode.forceRedraw()
    }, DT.toMilliseconds / 5)
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
