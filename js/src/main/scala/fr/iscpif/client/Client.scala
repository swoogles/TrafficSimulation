package fr.iscpif.client


import com.billding.physics.Spatial
import com.billding.traffic._
import fr.iscpif.client.uimodules.Model
import org.scalajs.dom
import org.scalajs.dom.Element
import squants.motion.{KilometersPerHour, Velocity}

import scala.concurrent.Future
import squants.Length

import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.runNow
import squants.space.Kilometers
import squants.time.{Milliseconds, Seconds}
import rx.{Rx, Var}

import scaladget.tools.JsRxTags._
import scalatags.JsDom.all._
import scalatags.generic

@JSExportTopLevel("Client")
object Client {
  val speedLimit: Velocity = KilometersPerHour(65)

  val originSpatial = Spatial((0, 0, 0, Kilometers))
  val endingSpatial = Spatial((0.5, 0, 0, Kilometers))

  val speed = Var(KilometersPerHour(50))

  val street = Street(Seconds(2), originSpatial, endingSpatial, speed.now, numLanes=1)

  val canvasDimensions: (Length, Length) = (Kilometers(.25), Kilometers(.5))
  implicit val DT = Milliseconds(20)
  val originalScene: SceneImpl = SceneImpl(
    List(street),
    Seconds(0),
    DT,
    speedLimit,
    canvasDimensions
  )
  val model: Model = Model(originalScene)
  val buttonBehaviors = ButtonBehaviors(model)
  val controlElements = ControlElements(buttonBehaviors)

  val GLOBAL_T = Rx {
    model.sceneVar().t
  }

  // Just a snippet to remind me how to pass html parameters around
  val startingColor: generic.Modifier[Element] = modifier(
    color := "blue"
  )

  @JSExport
  def run() {
    dom.document.body.appendChild(controlElements.createLayout())

    val window: Rx[Window] = Rx{
      new Window(model.sceneVar())
    }
    dom.window.setInterval(() => {
      model.respondToAllInput()
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

  def read[Result: upickle.default.Reader](p: String): Result = upickle.default.read[Result](p)

  def write[Result: upickle.default.Writer](r: Result): String = upickle.default.write(r)
}
