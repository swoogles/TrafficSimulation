package fr.iscpif.client

import com.billding.physics.Spatial
import fr.iscpif.client.uimodules.Model
import org.scalajs.dom

import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}
import squants.space.Kilometers
import squants.time.{Milliseconds, Time}
import rx.Rx

import scaladget.tools.JsRxTags._

@JSExportTopLevel("Client")
object Client extends App {

  val originSpatial = Spatial((0, 0, 0, Kilometers))
  val endingSpatial = Spatial((0.5, 0, 0, Kilometers))

  override def main(args: Array[String]): Unit = {
    println("Hello world!")
  }

  override def delayedInit(body: => Unit) = {
    println("dummy text, printed before initialization of C")
    body // evaluates the initialization code of C
  }

  implicit val DT: Time = Milliseconds(20)
  val scenes = new SampleSceneCreation(endingSpatial)
  val model: Model =
    Model(
      scenes.startingScene.scene,
      List(
        scenes.emptyScene,
        scenes.scene1,
        scenes.scene2,
        scenes.multipleStoppedGroups
      ),
      SerializationFeatures("localhost", 8080, "http")
    )

  val controlElements =
    ControlElements(
      ButtonBehaviors(model)
    )

  val GLOBAL_T = Rx {
    model.sceneVar().t
  }

  @JSExport
  def run() {
    dom.document.body.appendChild(controlElements.createLayout())

    val window: Rx[Window] = Rx {
      new Window(model.sceneVar())
    }
    dom.window.setInterval(() => {
      model.respondToAllInput()
    }, DT.toMilliseconds / 5) // TODO Make this understandable and easily modified. Just some simple algebra.
  }

}
