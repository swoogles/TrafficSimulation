package fr.iscpif.client.uimodules

import com.billding.physics.Spatial
import com.billding.traffic.{IntelligentDriverModelImpl, LaneImpl, PilotedVehicle, PilotedVehicleImpl, Scene, SceneImpl, StreetImpl}
import fr.iscpif.client.serialization
import rx.{Ctx, Rx, Var}
import squants.Time
import squants.motion.{KilometersPerHour, Velocity}
import squants.time.Seconds

trait Serialization {
  val serializeScene: Var[Boolean] = Var(false)
  val deserializeScene: Var[Boolean] = Var(false)
}

case class Disruptions(
  disruptLane: Var[Boolean] = Var(false),
  disruptLaneExisting: Var[Boolean] = Var(false)
)

trait ModelTrait {
  def pause: ModelTrait
  def unpause: ModelTrait
//  TODO: This would utiliaze originalScene in a more encapsulated way.
  def reset: ModelTrait
  def resetIfNecessary: Unit
  def updateLane(lane: LaneImpl): LaneImpl
  def updateLanesAndScene()
}

/**
  * Look into nesting more specific...Modules?
  * A 1-level list of Vars is already becoming unwieldy, and it's not sustainable.

  Thoughts about Vars/Rxs
    I think they should *only* relate to input, and not go down into any of the classes inside a
    given Scene, which does *not* interact directly with the user. The page model does, not the scene.
 */
case class Model (
  originalScene: SceneImpl,
  // These should probably be gleaned from the scene itself.
  speed: Var[Velocity] = Var(KilometersPerHour(50)),
  carTiming: Var[Time] = Var(Seconds(3)),
  paused: Var[Boolean] = Var(false),
  resetScene: Var[Boolean] = Var(false),
  vehicleCount: Var[Int] = Var(0),
  disruptions: Disruptions = Disruptions()
)(implicit ctx: Ctx.Owner)
  extends Serialization
  with ModelTrait
{
  private implicit val DT = originalScene.dt
  val savedScene: Var[Scene] = Var(originalScene)
  // TODO Make this private
  var sceneVar: Var[SceneImpl] = Var(originalScene)
  val carTimingText: Rx.Dynamic[String] = Rx(s"Current car timing ${carTiming()} ")
  val carSpeedText: Rx.Dynamic[String] = Rx(s"Current car speed ${speed()} ")

  val pauseText = Rx {
    if (paused())
      "Unpause"
    else
      "Pause"
  }

  def updateScene(speedLimit: Velocity) =
    sceneVar() = sceneVar.now.update(speedLimit)

  def pause: ModelTrait = this.copy(paused = Var(true))
  def unpause: ModelTrait =   this.copy(paused = Var(false))
  def reset: ModelTrait = {
    sceneVar() = originalScene
    resetScene() = false
    this
  }

  def resetIfNecessary: Unit =
    if (resetScene.now == true) {
      reset
    }

  val car: PilotedVehicleImpl =
    PilotedVehicle.commuter(Spatial.BLANK, new IntelligentDriverModelImpl)

  def disruptLane(lane: LaneImpl, model: Model): LaneImpl =
    if (this.disruptions.disruptLane.now == true) {
      this.disruptions .disruptLane() = false
      lane.addDisruptiveVehicle(car)
    } else {
      lane
    }

  def disruptLaneExisting(lane: LaneImpl): LaneImpl =
    if (this.disruptions.disruptLaneExisting.now == true) {
      this.disruptions.disruptLaneExisting() = false
      lane.disruptVehicles()
    } else {
      lane
    }

  def updateLane(lane: LaneImpl): LaneImpl = {
    val laneAfterDisruption = disruptLane(lane, this)
    val laneAfterDisruptionExisting = disruptLaneExisting(laneAfterDisruption)

    val newSource =
      laneAfterDisruptionExisting.vehicleSource
        .copy(spacingInTime = this.carTiming.now)
        .updateSpeed(this.speed.now)
    laneAfterDisruptionExisting.copy(vehicleSource = newSource)
  }

  def updateLanesAndScene() = {
    if (this.paused.now == false) {
      val newScene = this.sceneVar.now.updateAllStreets(this.updateLane(_))
      this.sceneVar() = newScene
      this.updateScene(this.sceneVar.now.speedLimit)
    }

  }

  def respondToAllInput() = {
    this.resetIfNecessary
    this.updateLanesAndScene()
    serialization.serializeIfNecessary(this)
    serialization.deserializeIfNecessary(this)
  }
}
