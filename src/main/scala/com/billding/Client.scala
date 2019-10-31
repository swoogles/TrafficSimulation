package com.billding

import com.billding.physics.{Spatial, SpatialFor}
import com.billding.serialization.BillSquants
import com.billding.traffic.{Driver, Lane, PilotedVehicle, Scene, Street, Vehicle, VehicleSourceImpl}
import com.billding.uimodules.Model
import squants.motion.{Acceleration, Distance}

import org.scalajs.dom
import org.scalajs.dom.raw.Node

import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}
import rx.Rx
import scaladget.tools.JsRxTags._
import play.api.libs.json.{Format, Json}
import squants.{Mass, QuantityVector, Time, Velocity}
import squants.space.Kilometers
import squants.time.Milliseconds

@JSExportTopLevel("Client")
object Client {

  val originSpatial = Spatial((0, 0, 0, Kilometers))
  val endingSpatial = Spatial((0.5, 0, 0, Kilometers))

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

  val sceneVar: Rx[Scene] = Rx {
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
  implicit val spatialFormat: Format[Spatial] = Json.format[Spatial]
  implicit val driverFormat: Format[Driver] = Json.format[Driver]
  implicit val mf: Format[Mass] = BillSquants.mass.format
  implicit val af: Format[Acceleration] = BillSquants.acceleration.format

  implicit val vehicleFormat: Format[Vehicle] = Json.format[Vehicle]
  implicit val spatialForPilotedVehicle: SpatialFor[PilotedVehicle] = {
    case vehicle: PilotedVehicle => vehicle.spatial
  }
  implicit val pilotedVehicleFormat: Format[PilotedVehicle] =
    Json.format[PilotedVehicle]

  implicit val vehicleSourceFormat: Format[VehicleSourceImpl] =
    Json.format[VehicleSourceImpl]

  implicit val laneFormat: Format[Lane] = Json.format[Lane]
  implicit val streetFormat: Format[Street] = Json.format[Street]

  implicit val sceneFormats: Format[Scene] = Json.format[Scene]

  @JSExport
  def run() {
    println("Running with a simple non-cross-build!!!!")
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