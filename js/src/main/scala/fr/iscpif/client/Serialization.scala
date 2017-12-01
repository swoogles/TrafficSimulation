package fr.iscpif.client

import com.billding.traffic.SceneImpl
import fr.iscpif.client.uimodules.Model

import scala.scalajs.concurrent.JSExecutionContext.Implicits.runNow
import org.scalajs.dom.ext.Ajax
import play.api.libs.json.Json

import scala.util.{Failure, Success}

package object serialization {
  /**
    * TODO: Deserialization is killing the vehicle source now. Not sure when that was introduced.
    */
  def deserializeIfNecessary(model: Model): Unit = {
    if (model.deserializeScene.now == true) {
      val f = Ajax.get("http://localhost:8080/loadScene")
      f.onComplete {
        case Success(xhr) => {
          val res =
            Json.parse(xhr.responseText).as[SceneImpl] // Might want to use safer .asOpt
          model.sceneVar() = res
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

      val f = Ajax.post("http://localhost:8080/writeScene", data = Json.toJson(model.sceneVar.now).toString)
      f.onComplete {
        case Success(_) => println("serialized some stuff and sent it off")
        case Failure(cause) => println("failed: " + cause)
      }
      model.serializeScene() = false
    }
  }

}
