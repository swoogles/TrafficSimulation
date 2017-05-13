package client

import com.billding._
import com.billding.physics.Spatial
import com.billding.traffic._
import fr.iscpif.client.{GraphOriginal, WindowOriginal}
import org.scalajs.dom

import scala.concurrent.Future
import rx._
import squants.Length

import scala.scalajs.js.annotation.JSExport
import scala.scalajs.concurrent.JSExecutionContext.Implicits.runNow
import squants.motion._
import squants.space.{Kilometers, LengthUnit, Meters}
import squants.time.{Milliseconds, Seconds}

@JSExport("Client")
object Client {

  val helloValue = Var(0)
  val caseClassValue = Var("empty")

  val idm: IntelligentDriverModel = new IntelligentDriverModelImpl
  val speedLimit = KilometersPerHour(40)

  def createVehicle(
                     pIn1: (Double, Double, Double, LengthUnit),
                     vIn1: (Double, Double, Double, VelocityUnit)): PilotedVehicle = {
    PilotedVehicle.commuter(pIn1, vIn1, idm)
  }

  val zeroDimensions: (Double, Double, Double, LengthUnit) = (0, 2, 0, Meters)
  val originSpatial = Spatial((0, 0, 0, Meters), (0.1, 0, 0, KilometersPerHour), zeroDimensions)
  val endingSpatial = Spatial((100, 0, 0, Kilometers), (0.1, 0, 0, KilometersPerHour), zeroDimensions)

  /**
    * TODO: Values should be improved through other means discussed here:
    * [[com.billding.rendering.CanvasRendering]]
    */
  val vehiclesApproachingASlowCar = List(
    createVehicle((60, 0, 0, Meters), (10, 0, 0, KilometersPerHour)),
    createVehicle((20, 0, 0, Meters), (40, 0, 0, KilometersPerHour)),
    createVehicle((10, 0, 0, Meters), (40, 0, 0, KilometersPerHour)),
    createVehicle((0, 0, 0, Meters), (40, 0, 0, KilometersPerHour))
  )

  val standStillTrafficResuming = List(
    createVehicle((40, 0, 0, Meters), (0.1, 0, 0, KilometersPerHour)),
    createVehicle((30, 0, 0, Meters), (0.1, 0, 0, KilometersPerHour)),
    createVehicle((20, 0, 0, Meters), (0.1, 0, 0, KilometersPerHour)),
    createVehicle((10, 0, 0, Meters), (0.1, 0, 0, KilometersPerHour))
  )

  val noStartingVehicles = List(
  )

  val source = VehicleSourceImpl(Seconds(5), originSpatial, endingSpatial, speedLimit)
  val lane = new LaneImpl(
    vehiclesApproachingASlowCar,
//    standStillTrafficResuming,
//    noStartingVehicles,
    source,
    originSpatial,
    endingSpatial
  )
  val t = Seconds(2)
  val canvasDimensions = Dimensions(Kilometers(.7), Kilometers(.3))
  implicit val dt = Milliseconds(20)
  val scene: Scene = SceneImpl(List(lane), t, dt, speedLimit, canvasDimensions)

  var sceneVolatile = scene
  @JSExport
  def run() {
    val nodes = Seq( )
    val edges = Seq( )
    val millisecondsPerRefresh = 500
    var window = new Window(sceneVolatile, nodes, edges)
    dom.window.setInterval(() => {
      sceneVolatile = sceneVolatile.update(speedLimit)
      val numVehicles = sceneVolatile.lanes.head.vehicles.length
      window = new Window(sceneVolatile, nodes, edges)
      window.svgNode.forceRedraw()
    }, dt.toMilliseconds / 5)
  }
}

object Post extends autowire.Client[String, upickle.default.Reader, upickle.default.Writer] {

  override def doCall(req: Request): Future[String] = {
    val url = req.path.mkString("/")
    dom.ext.Ajax.post(
      url = "http://localhost:8080/" + url,
      data = upickle.default.write(req.args)
    ).map {
      _.responseText
    }
  }

  def read[Result: upickle.default.Reader](p: String) = upickle.default.read[Result](p)

  def write[Result: upickle.default.Writer](r: Result) = upickle.default.write(r)
}
