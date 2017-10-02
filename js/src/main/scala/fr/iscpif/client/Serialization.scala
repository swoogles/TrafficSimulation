package fr.iscpif.client

import fr.iscpif.client.uimodules.Model

import scala.scalajs.concurrent.JSExecutionContext.Implicits.runNow
import rx._
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
          import com.billding.serialization.TrafficJson.defaultSerialization.sceneFormats
          val res = Json.fromJson(
            Json.parse(xhr.responseText)
          ).get
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
      import com.billding.serialization.TrafficJson.defaultSerialization.sceneFormats

      val f = Ajax.post("http://localhost:8080/writeScene", data = Json.toJson(model.sceneVar.now).toString)
      f.onComplete {
        case Success(_) => println("serialized some stuff and sent it off")
        case Failure(cause) => println("failed: " + cause)
      }
      model.serializeScene() = false
    }
  }

}
