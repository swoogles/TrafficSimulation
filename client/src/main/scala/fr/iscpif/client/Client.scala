package client

import com.billding._
import fr.iscpif.client.{GraphOriginal, WindowOriginal}
import org.scalajs.dom

import scala.concurrent.Future
import rx._

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
  val speedLimit = KilometersPerHour(150)

  def createVehicle(
                     pIn1: (Double, Double, Double, LengthUnit),
                     vIn1: (Double, Double, Double, VelocityUnit)): PilotedVehicle = {
    PilotedVehicle.commuter(Spatial(pIn1, vIn1), idm)
  }

  val originSpatial = Spatial((0, 0, 0, Meters), (0.1, 0, 0, KilometersPerHour))
  val endingSpatial = Spatial((100, 0, 0, Kilometers), (0.1, 0, 0, KilometersPerHour))

  val vehicles = List(
    createVehicle((500, 0, 0, Meters), (10, 0, 0, KilometersPerHour)),
//    createVehicle((80, 0, 0, Meters), (70, 0, 0, KilometersPerHour)),
    createVehicle((60, 0, 0, Meters), (140, 0, 0, KilometersPerHour))
  )

  val source = VehicleSourceImpl(Seconds(1), originSpatial)
  val lane = new LaneImpl(vehicles, source, originSpatial, endingSpatial)
  val t = Seconds(500)
  implicit val dt = Milliseconds(500)
  val scene: Scene = SceneImpl(
    List(lane),
    t,
    dt,
    speedLimit
  )

  @JSExport
  def run() {
    val nodes = Seq( )
    val edges = Seq( )
    val millisecondsPerRefresh = 100
    var sceneVolatile = scene
    var window = new Window(sceneVolatile, nodes, edges)
    dom.window.setInterval(() => {
      sceneVolatile = sceneVolatile.update(speedLimit)
      window = new Window(sceneVolatile, nodes, edges)
      window.svgNode.forceRedraw()
//      println("1st vehicle: " + sceneVolatile.lanes.head.vehicles.head)
    }, millisecondsPerRefresh)
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
