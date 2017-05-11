package client

import com.billding._
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
  val speedLimit = KilometersPerHour(150)

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
  val vehicles = List(
    createVehicle((200, 0, 0, Meters), (20, 0, 0, KilometersPerHour)),
//    createVehicle((80, 0, 0, Meters), (70, 0, 0, KilometersPerHour)),
    createVehicle((60, 0, 0, Meters), (100, 0, 0, KilometersPerHour))
  )

  val source = VehicleSourceImpl(Seconds(1), originSpatial)
  val lane = new LaneImpl(vehicles, source, originSpatial, endingSpatial)
  val t = Seconds(0)
  val canvasDimensions: ((Length, Length),(Length, Length)) = ((-Kilometers(1), -Kilometers(1)), (Kilometers(1), Kilometers(1)))
  implicit val dt = Milliseconds(20)
  val scene: Scene = SceneImpl(
    List(lane),
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
      sceneVolatile = sceneVolatile.update(speedLimit)
      window = new Window(sceneVolatile, nodes, edges)
      window.svgNode.forceRedraw()
      /** TODO lane.leader.follower
        * How cool would that be?
        * Look for it in [[com.billding.Lane]], cause this is ugly.
       */
      val leadingVehicle: PilotedVehicle = sceneVolatile.lanes.head.vehicles.head
      val followingVehicle: PilotedVehicle = sceneVolatile.lanes.head.vehicles.tail.head
      println("Distance between: " + leadingVehicle.spatial.distanceTo(followingVehicle.spatial))
//      println("following vehicle.v.x: " + sceneVolatile.lanes.head.vehicles.tail.head.spatial.v.coordinates(0))
    }, dt.toMilliseconds / 10)
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
