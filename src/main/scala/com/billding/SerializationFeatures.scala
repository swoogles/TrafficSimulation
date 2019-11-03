package com.billding

import com.billding.traffic.Scene
import com.billding.uimodules.Model
import org.scalajs.dom.ext.Ajax
import play.api.libs.json.{Format, Json}

import scala.util.{Failure, Success}

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

case class SerializationFeatures(hostName: String, port: Int, protocol: String) {
  val fullHost = s"$protocol://$hostName:$port"

  var serializedSceneJson: play.api.libs.json.JsValue = null
  var volatileScene: Scene = null

  def deserializeIfNecessary(model: Model)(implicit format: Format[Scene]): Unit =
    if (model.deserializeScene.now == true) {
      val f = Ajax.get(s"$fullHost/loadScene")
      f.onComplete {
        case Success(xhr) => {
          val res =
            Json
              .parse(xhr.responseText)
              .as[Scene] // Might want to use safer .asOpt
          model.loadScene(res)
        }

        case Failure(cause) => println("failed: " + cause)
      }
      model.deserializeScene() = false
    }

  def serializeIfNecessary(model: Model)(implicit format: Format[Scene]): Unit =
    if (model.serializeScene.now == true) {
      val curScene = model.sceneVar.now
      serializedSceneJson = Json.toJson(curScene)
      volatileScene = curScene

      val f = Ajax.post(s"$fullHost/writeScene", data = Json.toJson(curScene).toString)
      f.onComplete {
        case Success(_)     => println("serialized some stuff and sent it off")
        case Failure(cause) => println("failed: " + cause)
      }
      model.serializeScene() = false
    }

}
