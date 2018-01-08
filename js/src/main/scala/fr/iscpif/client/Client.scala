package fr.iscpif.client

import com.billding.physics.{Spatial, SpatialImpl}
import com.billding.traffic._
import fr.iscpif.client.uimodules.Model
import org.scalajs.dom
import org.scalajs.dom.Element
import squants.motion.{KilometersPerHour, Velocity, VelocityUnit}
import squants.Length

import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}
import squants.space.{Kilometers, LengthUnit, Meters}
import squants.time.{Milliseconds, Seconds, Time}
import rx.{Rx, Var}

import scaladget.tools.JsRxTags._
import scalatags.JsDom.all._
import scalatags.generic

class SampleSceneCreation(endingSpatial: SpatialImpl) {
  import PilotedVehicle.createVehicle
  implicit val DT: Time = Milliseconds(20)

  def simpleVehicle
  (
      pIn1: (Double, Double, Double, LengthUnit),
      vIn1: (Double, Double, Double, VelocityUnit) =
        (0, 0, 0, KilometersPerHour)
      ) = {
    createVehicle(pIn1, vIn1, endingSpatial)
  }

  val scene1 = createWithVehicles(
    List(
      simpleVehicle((100, 0, 0, Meters), (0.1, 0, 0, KilometersPerHour)),
      simpleVehicle((80, 0, 0, Meters), (50, 0, 0, KilometersPerHour)),
      simpleVehicle((60, 0, 0, Meters), (100, 0, 0, KilometersPerHour))
    )
  )

  val scene2 = createWithVehicles(
    List(
      simpleVehicle((100, 0, 0, Meters), (0, 0, 0, KilometersPerHour)),
      simpleVehicle((95, 0, 0, Meters), (0, 0, 0, KilometersPerHour)),
      simpleVehicle((90, 0, 0, Meters), (0, 0, 0, KilometersPerHour))
    )
  )

  def createWithVehicles(vehicles: List[PilotedVehicleImpl]): SceneImpl = {

    val speedLimit: Velocity = KilometersPerHour(65)
    val originSpatial = Spatial((0, 0, 0, Kilometers))
    val endingSpatial = Spatial((0.5, 0, 0, Kilometers))
    val canvasDimensions: (Length, Length) = (Kilometers(.25), Kilometers(.5))

    val vehicleSource = VehicleSourceImpl(Seconds(1), originSpatial, endingSpatial)
    val lane =
//      LaneImpl(vehicles, vehicleSource, originSpatial, endingSpatial, speedLimit)
    Lane.apply(Seconds(2), originSpatial, endingSpatial, speedLimit, vehicles)
    val street = StreetImpl(List(lane), originSpatial, endingSpatial, Seconds(1))
    SceneImpl(
      List(street),
      Seconds(0.2),
      DT,
      speedLimit,
      canvasDimensions
    )
  }
}

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

  val street =
    Street(Seconds(2), originSpatial, endingSpatial, speed.now, numLanes = 1)

  val canvasDimensions: (Length, Length) = (Kilometers(.25), Kilometers(.5))
  implicit val DT: Time = Milliseconds(20)
  val originalScene: SceneImpl = SceneImpl(
    List(street),
    Seconds(0),
    DT,
    speedLimit,
    canvasDimensions
  )
  // TODO create serialization here
  val scenes = new SampleSceneCreation(endingSpatial)
  val model: Model =
    Model(
      scenes.scene2,
      SerializationFeatures("localhost", 8080, "http")
    )

  val controlElements =
    ControlElements(
      ButtonBehaviors(model)
    )

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

    val window: Rx[Window] = Rx {
      new Window(model.sceneVar())
    }
    dom.window.setInterval(() => {
      model.respondToAllInput()
    }, DT.toMilliseconds / 5) // TODO Make this understandable and easily modified. Just some simple algebra.
  }

}
