package client

import com.billding._
import com.billding.physics.Spatial
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
  // TODO Heh. How awful is this?
  var INFLECTED = false
  var GLOBAL_T: Time = null

  val helloValue = Var(0)
  val caseClassValue = Var("empty")

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
  val leadVehicleXPos = -50
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


  val tmpLane = new LaneImpl(Nil, source, originSpatial, endingSpatial)
  val vehicles = List(
    createVehicle((leadVehicleXPos, 0, 0, Meters), (herdSpeed-60, 0, 0, KilometersPerHour), tmpLane.vehicleAtInfinity.spatial)
  )

  /** TODO: Source location should be determined inside Lane constructor
    * Velocity spacial can *also* be determined by stop/start and a given speed.
    */

  tmpLane.vehicleAtInfinity.spatial
  val source = VehicleSourceImpl(Seconds(1), originSpatial, velocitySpatial)
  val source2 = VehicleSourceImpl(Seconds(2), originSpatial2, velocitySpatial)
  pprint.pprintln("starting vehicle: " + vehicles.head.spatial)
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
    var printed = false
    dom.window.setInterval(() => {
      GLOBAL_T = sceneVolatile.t
      val vehicles = sceneVolatile.lanes.head.vehicles
        if (printed == false && vehicles.length == 2) {
          println("t: " + sceneVolatile.t)
          println("first car: ")
          pprint.pprintln(vehicles.head.spatial)
          println("first follower: ")
          pprint.pprintln(vehicles.tail.head.spatial)
          printed = true
        }
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
