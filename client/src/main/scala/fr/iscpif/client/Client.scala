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

/**
  * I think my primary problem with the "collision == death" issue is that I can't start
  * re-accelerating in the desired direction after everything is zeroed out.
  */
@JSExport("Client")
object Client {

  val helloValue = Var(0)
  val caseClassValue = Var("empty")

  val idm: IntelligentDriverModel = new IntelligentDriverModelImpl
  val speedLimit = KilometersPerHour(65)

  def createVehicle(
                     pIn1: (Double, Double, Double, LengthUnit),
                     vIn1: (Double, Double, Double, VelocityUnit)): PilotedVehicle = {
    PilotedVehicle.commuter(pIn1, vIn1, idm)
  }

  val zeroDimensions: (Double, Double, Double, LengthUnit) = (0, 2, 0, Meters)
  val leadVehicleXPos = -10
  val originSpatial = Spatial((-100, 0, 0, Meters), (0.1, 0, 0, KilometersPerHour), zeroDimensions)
  val endingSpatial = Spatial((100, 0, 0, Kilometers), (0.1, 0, 0, KilometersPerHour), zeroDimensions)
  val originSpatial2 = Spatial((-100, 20, 0, Meters), (0.1, 0, 0, KilometersPerHour), zeroDimensions)
  val endingSpatial2 = Spatial((100, 20, 0, Kilometers), (0.1, 0, 0, KilometersPerHour), zeroDimensions)

  val herdSpeed = 65
  val velocitySpatial = Spatial((0, 0, 0, Meters), (herdSpeed, 0, 0, KilometersPerHour), zeroDimensions)
  /**
    * TODO: Values should be improved through other means discussed here:
    * [[com.billding.rendering.CanvasRendering]]
    */


  val vehicles = List(
    createVehicle((leadVehicleXPos, 0, 0, Meters), (herdSpeed-40, 0, 0, KilometersPerHour))
  )

  /** TODO: Source location should be determined inside Lane constructor
    * Velocity spacial can *also* be determined by stop/start and a given speed.
    */

  val source = VehicleSourceImpl(Seconds(1), originSpatial, velocitySpatial)
  val source2 = VehicleSourceImpl(Seconds(2), originSpatial2, velocitySpatial)
  val lane = new LaneImpl(vehicles, source, originSpatial, endingSpatial)
  val lane2 = new LaneImpl(Nil, source2, originSpatial2, endingSpatial2)
  val t = Seconds(0)
  val canvasDimensions: (Length, Length) = (Kilometers(.5), Kilometers(.25))
  implicit val dt = Milliseconds(20)
  val scene: Scene = SceneImpl(
    List(lane, lane2),
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
        * Look for it in [[Lane]], cause this is ugly.
       */
      val leadingVehicle: PilotedVehicle = sceneVolatile.lanes.head.vehicles.head
      val followingVehicle: PilotedVehicle = sceneVolatile.lanes.head.vehicles.tail.head
//      println("Distance between: " + leadingVehicle.spatial.distanceTo(followingVehicle.spatial))
//      println("following vehicle.v.x: " + sceneVolatile.lanes.head.vehicles.tail.head.spatial.v.coordinates(0))
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
