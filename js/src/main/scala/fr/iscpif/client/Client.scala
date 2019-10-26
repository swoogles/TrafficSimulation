package fr.iscpif.client

import fr.iscpif.client.physics.Spatial
import fr.iscpif.client.traffic.{DriverImpl, Lane, PilotedVehicle, SceneImpl, StreetImpl, VehicleImpl, VehicleSourceImpl}
import fr.iscpif.client.uimodules.Model
import org.scalajs.dom
import org.scalajs.dom.raw.Node

import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}
import rx.Rx
import scaladget.tools.JsRxTags._
import fr.iscpif.client.physics.{SpatialFor, SpatialImpl}
import fr.iscpif.client.serialization.BillSquants
import play.api.libs.json.{Format, Json}
import squants.motion._
import squants.{Mass, QuantityVector, Time, Velocity}
import squants.space.Kilometers
import squants.time.Milliseconds

@JSExportTopLevel("Client")
object Client {

  val originSpatial = Spatial((0, 0, 0, Kilometers))
  val endingSpatial = Spatial((0.5, 0, 0, Kilometers))

//  override def main(args: Array[String]): Unit = {
//    println("Hello world!")
//  }
//
//  override def delayedInit(body: => Unit) = {
//    println("dummy text, printed before initialization of C")
//    body // evaluates the initialization code of C
//  }

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

  val sceneVar: Rx[SceneImpl] = Rx {
    model.sceneVar() // Ne
  }

  val controlElements =
    ControlElements(
      ButtonBehaviors(model)
    )

  // Should directly use sceneVar
  val GLOBAL_T: Rx[Time] = Rx {
    sceneVar().t
  }

  implicit val df: Format[Distance] = BillSquants.distance.format
  implicit val tf: Format[Time] = BillSquants.time.format
  implicit val vf: Format[Velocity] = BillSquants.velocity.format

  implicit val dQvf: Format[QuantityVector[Distance]] =
    BillSquants.distance.formatQv
  implicit val vQvf: Format[QuantityVector[Velocity]] =
    BillSquants.velocity.formatQv
  implicit val spatialFormat: Format[SpatialImpl] = Json.format[SpatialImpl]
  implicit val driverFormat: Format[DriverImpl] = Json.format[DriverImpl]
  implicit val mf: Format[Mass] = BillSquants.mass.format
  implicit val af: Format[Acceleration] = BillSquants.acceleration.format

  implicit val vehicleFormat: Format[VehicleImpl] = Json.format[VehicleImpl]
  implicit val spatialForPilotedVehicle: SpatialFor[PilotedVehicle] = {
    case vehicle: PilotedVehicle => vehicle.spatial
  }
  implicit val pilotedVehicleFormat: Format[PilotedVehicle] =
    Json.format[PilotedVehicle]

  implicit val vehicleSourceFormat: Format[VehicleSourceImpl] =
    Json.format[VehicleSourceImpl]

  implicit val laneFormat: Format[Lane] = Json.format[Lane]
  implicit val streetFormat: Format[StreetImpl] = Json.format[StreetImpl]

  implicit val sceneFormats: Format[SceneImpl] = Json.format[SceneImpl]

  @JSExport
  def run() {
    dom.document.body.appendChild(controlElements.createLayout())

    val canvasHeight = 800
    val canvasWidth = 1500

    val windowLocal: Rx[Window] = Rx {
      new Window(sceneVar(), canvasHeight, canvasWidth)
    }

    windowLocal.trigger {
      val previousSvg: Node = dom.document.getElementsByTagName("svg").item(0)
      if (previousSvg != null) {
        dom.document.body.removeChild(previousSvg)
      }
      dom.document.body.appendChild(windowLocal.now.svgNode.render)
    }

    val x: Int = dom.window.setInterval(() => {
      model.respondToAllInput()
    }, DT.toMilliseconds / 5) // TODO Make this understandable and easily modified. Just some simple algebra.
  }

}
