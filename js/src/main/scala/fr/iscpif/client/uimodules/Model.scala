package fr.iscpif.client.uimodules

import com.billding.physics.Spatial
import com.billding.traffic.{
  IntelligentDriverModelImpl,
  LaneImpl,
  PilotedVehicle,
  PilotedVehicleImpl,
  SceneImpl,
}
import rx.{Ctx, Rx, Var}
import squants.Time
import squants.motion.{KilometersPerHour, Velocity}
import squants.time.Seconds

import fr.iscpif.client.SerializationFeatures

trait Serialization {
  val serializeScene: Var[Boolean] = Var(false)
  val deserializeScene: Var[Boolean] = Var(false)
}

case class Disruptions(
    disruptLane: Var[Boolean] = Var(false),
    disruptLaneExisting: Var[Boolean] = Var(false)
)

trait ModelTrait {
  def togglePause(): Unit
  def pause(): Unit
  def respondToAllInput()
}

/**
  * Look into nesting more specific...Modules?
  * A 1-level list of Vars is already becoming unwieldy, and it's not sustainable.

  Thoughts about Vars/Rxs
    I think they should *only* relate to input, and not go down into any of the classes inside a
    given Scene, which does *not* interact directly with the user. The page model does, not the scene.
  */
case class Model(
    originalScene: SceneImpl,
    serializationFeatures: SerializationFeatures,
    // These should probably be gleaned from the scene itself.
    speed: Var[Velocity] = Var(KilometersPerHour(50)),
    carTiming: Var[Time] = Var(Seconds(3)),
    paused: Var[Boolean] = Var(false),
    resetScene: Var[Boolean] = Var(false),
    vehicleCount: Var[Int] = Var(0),
    disruptions: Disruptions = Disruptions()
)(implicit ctx: Ctx.Owner)
    extends Serialization
    with ModelTrait {
  private implicit val DT: Time = originalScene.dt
  // TODO Make this private
  val sceneVar: Var[SceneImpl] = Var(originalScene)
  val carTimingText: Rx.Dynamic[String] = Rx(
    s"Current car timing ${carTiming()} ")
  val carSpeedText: Rx.Dynamic[String] = Rx(s"Current car speed ${speed()} ")

  val pauseText = Rx {
    if (paused())
      "Unpause"
    else
      "Pause"
  }

  def togglePause(): Unit =
    paused() = !paused.now

  def pause(): Unit =
    paused() = true

  def loadScene(scene: SceneImpl): Unit = {
    sceneVar() = scene
    paused() = true
  }

//  private def pause: ModelTrait = this.copy(paused = Var(true))
  private def unpause: ModelTrait = this.copy(paused = Var(false))
  private def reset: ModelTrait = {
    sceneVar() = originalScene
    resetScene() = false
    this
  }

  private def resetIfNecessary(): Unit =
    if (resetScene.now == true) {
      reset
    }

  val car: PilotedVehicleImpl =
    PilotedVehicle.commuter(Spatial.BLANK, new IntelligentDriverModelImpl)

  private def disrupt(lane: LaneImpl): LaneImpl = {
    this.disruptions.disruptLane() = false
    lane.addDisruptiveVehicle(car)
  }

  def disruptLane(lane: LaneImpl, model: Model): LaneImpl =
    if (this.disruptions.disruptLane.now == true)
      disrupt(lane)
    else
      lane

  private def disruptExisting(lane: LaneImpl): LaneImpl = {
    this.disruptions.disruptLaneExisting() = false
    lane.disruptVehicles()
  }

  def disruptLaneExisting(lane: LaneImpl): LaneImpl =
    if (this.disruptions.disruptLaneExisting.now == true)
      disruptExisting(lane)
    else
      lane

  private def updateLane(lane: LaneImpl): LaneImpl = {
    val laneAfterDisruption = disruptLane(lane, this)
    val laneAfterDisruptionExisting = disruptLaneExisting(laneAfterDisruption)

    val newSource =
      laneAfterDisruptionExisting.vehicleSource
        .copy(spacingInTime = this.carTiming.now)
        .updateSpeed(this.speed.now)
    laneAfterDisruptionExisting.copy(vehicleSource = newSource)
  }

  private def updateScene(speedLimit: Velocity) =
    sceneVar() = sceneVar.now.updateSpeedLimit(speedLimit)

  private def updateLanesAndScene(): Unit = {
    if (this.paused.now == false) {
      val newScene = this.sceneVar.now.updateAllStreets(this.updateLane)
      this.sceneVar() = newScene
      this.updateScene(this.sceneVar.now.speedLimit)
    }
  }

  def respondToAllInput(): Unit = {
    this.resetIfNecessary()
    this.updateLanesAndScene()
    serializationFeatures.serializeIfNecessary(this)
    serializationFeatures.deserializeIfNecessary(this)
  }

}
