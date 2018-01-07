package fr.iscpif.client

import com.billding.traffic.SceneImpl
import fr.iscpif.client.uimodules.Model

import scala.scalajs.concurrent.JSExecutionContext.Implicits.runNow
import org.scalajs.dom.ext.Ajax
import play.api.libs.json.Json

import scala.util.{Failure, Success}

case class SerializationFeatures(hostName: String,
                                 port: Int,
                                 protocol: String) {
  val fullHost = s"$protocol://$hostName:$port"

  /**
    * TODO: Deserialization is killing the vehicle source now. Not sure when that was introduced.
    */
  var serializedSceneJson: play.api.libs.json.JsValue = null
  var volatileScene: SceneImpl = null
  def deserializeIfNecessary(model: Model): Unit = {
    if (model.deserializeScene.now == true) {
      val f = Ajax.get(s"$fullHost/loadScene")
      f.onComplete {
        case Success(xhr) => {
          val res =
            Json
              .parse(xhr.responseText)
              .as[SceneImpl] // Might want to use safer .asOpt
          model.loadScene(res)
        }

        case Failure(cause) => println("failed: " + cause)
      }
      model.deserializeScene() = false
    }
  }

  def serializeIfNecessary(model: Model): Unit = {
    if (model.serializeScene.now == true) {
      val curScene = model.sceneVar.now
      serializedSceneJson = Json.toJson(curScene)
      volatileScene = curScene

      val f = Ajax.post(s"$fullHost/writeScene",
                        data = Json.toJson(curScene).toString)
      f.onComplete {
        case Success(_)     => println("serialized some stuff and sent it off")
        case Failure(cause) => println("failed: " + cause)
      }
      model.serializeScene() = false
    }
  }

}
