package com.billding

import com.billding.traffic.StreetScene
import com.billding.uimodules.Model
import org.scalajs.dom.ext.Ajax
import play.api.libs.json.{Format, Json}

import scala.util.{Failure, Success}

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

class SerializationFeatures(hostName: String, port: Int, protocol: String) {
  private val fullHost = s"$protocol://$hostName:$port"

  private var volatileScene: StreetScene = null

  def deserializeIfNecessary(model: Model)(implicit format: Format[StreetScene]): Unit =
    if (model.deserializeScene.now() == true) {
      val f = Ajax.get(s"$fullHost/loadScene")
      f.onComplete {
        case Success(xhr) => {
          val res =
            Json
              .parse(xhr.responseText)
              .as[StreetScene] // Might want to use safer .asOpt
          model.loadScene(res)
        }

        case Failure(cause) => println("failed: " + cause)
      }
      model.deserializeScene.set(false)
    }

  def serializeIfNecessary(model: Model)(implicit format: Format[StreetScene]): Unit =
    if (model.serializeScene.now() == true) {
      model.sceneVar.now() match {
        case curScene: StreetScene =>
          volatileScene = curScene

          val f = Ajax.post(s"$fullHost/writeScene", data = Json.toJson(curScene).toString)
          f.onComplete {
            case Success(_)     => println("serialized some stuff and sent it off")
            case Failure(cause) => println("failed: " + cause)
          }
        // A ring is built from a car count and a circumference rather than a list of
        // vehicles, so there is nothing worth shipping to the server yet.
        case other => println("can only serialize street scenes so far, not: " + other)
      }
      model.serializeScene.set(false)
    }

}
