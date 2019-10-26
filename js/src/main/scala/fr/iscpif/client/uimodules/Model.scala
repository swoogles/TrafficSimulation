package fr.iscpif.client.uimodules

import fr.iscpif.client.physics.Spatial
import fr.iscpif.client.traffic.{
  IntelligentDriverModelImpl,
  LaneImpl,
  PilotedVehicle,
  SceneImpl
}
import rx.{Ctx, Rx, Var}
import squants.Time
import squants.motion.{KilometersPerHour, Velocity}
import fr.iscpif.client.{NamedScene, SerializationFeatures}
import play.api.libs.json.Format

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
  def respondToAllInput()(implicit format: Format[SceneImpl])
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
    preloadedScenes: List[NamedScene] = List(),
    serializationFeatures: SerializationFeatures,
    // These should probably be gleaned from the scene itself.
    speed: Var[Velocity] = Var(KilometersPerHour(50)),
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
  val carSpeedText: Rx.Dynamic[String] = Rx(s"Current car speed ${speed()} ")

  val carTiming: Var[Time] = Var(
    originalScene.streets
      .flatMap(street =>
        street.lanes.map(lane => lane.vehicleSource.spacingInTime))
      .head
  )

  val carTimingText: Rx.Dynamic[String] = Rx(
    s"Current car timing ${carTiming()} ")

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

  def loadNamedScene(name: String): Unit = {
    val retrievedSceneAttempt =
      preloadedScenes.find(scene => scene.name.equals(name))
    if (retrievedSceneAttempt.isDefined) {
      loadScene(retrievedSceneAttempt.get.scene)
    } else {
      println("couldn't find a matching scene for name: " + name)
    }

  }

  def loadScene(scene: SceneImpl): Unit = {
    sceneVar() = scene
    carTiming() = sceneVar.now.streets
      .flatMap(street =>
        street.lanes.map(lane => lane.vehicleSource.spacingInTime))
      .head
    paused() = true
  }

  private def reset: Unit = {
    sceneVar() = originalScene
    resetScene() = false
  }

  private def resetIfNecessary(): Unit =
    if (resetScene.now == true) {
      reset
    }

  val car: PilotedVehicle =
    PilotedVehicle.commuter2(Spatial.BLANK, new IntelligentDriverModelImpl, Spatial.BLANK)

  private def disrupt(lane: LaneImpl): LaneImpl = {
    this.disruptions.disruptLane() = false
    lane.addDisruptiveVehicle(car)
  }

  def disruptLane(lane: LaneImpl): LaneImpl =
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
    val laneAfterDisruption = disruptLane(lane)
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

  def respondToAllInput()(implicit format: Format[SceneImpl]): Unit = {
    this.resetIfNecessary()
    this.updateLanesAndScene()
    serializationFeatures.serializeIfNecessary(this)
    serializationFeatures.deserializeIfNecessary(this)
  }

}
