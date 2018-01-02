package fr.iscpif.client

import com.billding.physics.Spatial
import com.billding.traffic._
import fr.iscpif.client.uimodules.Model
import org.scalajs.dom
import org.scalajs.dom.Element
import squants.motion.{KilometersPerHour, Velocity}

import squants.Length

import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}
import squants.space.Kilometers
import squants.time.{Milliseconds, Seconds}
import rx.{Rx, Var}

import scaladget.tools.JsRxTags._
import scalatags.JsDom.all._
import scalatags.generic

@JSExportTopLevel("Client")
//@JSExport("Client")
object Client extends App {
  val speedLimit: Velocity = KilometersPerHour(65)

  val originSpatial = Spatial((0, 0, 0, Kilometers))
  val endingSpatial = Spatial((0.5, 0, 0, Kilometers))

  val speed = Var(KilometersPerHour(50))

  override def main(args: Array[String]): Unit = {
    println("Hello world!")
  }

  override def delayedInit(body: => Unit) = {
    println("dummy text, printed before initialization of C")
    body // evaluates the initialization code of C
  }

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
  // TODO create serialization here
  val model: Model =
    Model(
      originalScene,
      SerializationFeatures("localhost", 8080, "http")
    )

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
