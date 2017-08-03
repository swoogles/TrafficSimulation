package client

import com.billding._
import com.billding.physics.{South, Spatial}
import com.billding.traffic._
import fr.iscpif.client.{GraphOriginal, WindowOriginal}
import org.scalajs.dom

import scala.concurrent.Future
import rx._
import squants.{Length, Time}

import scala.scalajs.js.annotation.JSExport
import scala.scalajs.concurrent.JSExecutionContext.Implicits.runNow
import squants.motion._
import squants.space.{Kilometers, LengthUnit, Meters}
import squants.time.{Milliseconds, Seconds}

/**
  * I think my primary problem with the "collision == death" issue is that I can't start
  * re-accelerating in the desired direction after everything is zeroed out.
  */
@JSExport("Client")
object Client {
  var GLOBAL_T: Time = null

  val idm: IntelligentDriverModel = new IntelligentDriverModelImpl
  val speedLimit = KilometersPerHour(65)

  def createVehicle(
                     pIn1: (Double, Double, Double, LengthUnit),
                     vIn1: (Double, Double, Double, VelocityUnit),
                      destination: Spatial
                   ): PilotedVehicle = {
    PilotedVehicle.commuter(pIn1, vIn1, idm, destination)
  }

  val zeroDimensions: (Double, Double, Double, LengthUnit) = (0, 2, 0, Meters)
  val originSpatial = Spatial((-50, 0, 0, Meters))
  val endingSpatial = Spatial((100, 0, 0, Kilometers))
  val originSpatial2 = Spatial((-100, 20, 0, Meters))
  val endingSpatial2 = Spatial((100, .002, 0, Kilometers))

  val herdSpeed = 65
  /**
    * TODO: Values should be improved through other means discussed here:
    * [[com.billding.rendering.CanvasRendering]]
    */


  val vehicles: List[PilotedVehicle] = List( )

  val street = Street(Seconds(2), originSpatial, endingSpatial, South, 5)

//  val lane = Lane(Seconds(1), originSpatial, endingSpatial, vehicles)
//  val lane2 = Lane(Seconds(2), originSpatial2, endingSpatial2)
  val t = Seconds(0)
  val canvasDimensions: (Length, Length) = (Kilometers(.5), Kilometers(.25))
  implicit val dt = Milliseconds(20)
  val scene: Scene = SceneImpl(
    street.lanes,
//    List(lane, lane2),
    t,
    dt,
    speedLimit,
    canvasDimensions
  )

  @JSExport
  def run() {
    val nodes = Seq( )
    val edges = Seq( )
    val millisecondsPerRefresh = 500
    var sceneVolatile = scene
    var window = new Window(sceneVolatile, nodes, edges)
    dom.window.setInterval(() => {
      GLOBAL_T = sceneVolatile.t
      val vehicles = sceneVolatile.lanes.head.vehicles
        sceneVolatile = sceneVolatile.update(speedLimit)
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
