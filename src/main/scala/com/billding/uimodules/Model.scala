package com.billding.uimodules

import com.billding.{NamedScene, SerializationFeatures}
import com.billding.physics.Spatial
import com.billding.traffic.{IntelligentDriverModelImpl, Lane, PilotedVehicle, Scene}
import com.raquo.laminar.api.L.{Var, Signal}
import squants.Time
import squants.motion.{KilometersPerHour, Velocity}
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
  def respondToAllInput()(implicit format: Format[Scene]): Unit
}

/**
  * Look into nesting more specific...Modules?
  * A 1-level list of Vars is already becoming unwieldy, and it's not sustainable.

  Thoughts about Vars/Rxs
    I think they should *only* relate to input, and not go down into any of the classes inside a
    given Scene, which does *not* interact directly with the user. The page model does, not the scene.
  */
case class Model(
                  originalScene: Scene,
                  preloadedScenes: List[NamedScene] = List(),
                  serializationFeatures: SerializationFeatures,
                  // These should probably be gleaned from the scene itself.
                  speed: Var[Velocity] = Var(KilometersPerHour(50)),
                  paused: Var[Boolean] = Var(false),
                  resetScene: Var[Boolean] = Var(false),
                  vehicleCount: Var[Int] = Var(0),
                  disruptions: Disruptions = Disruptions()
)
    extends Serialization
    with ModelTrait {
  implicit private val DT: Time = originalScene.dt
  // TODO Make this private
  val sceneVar: Var[Scene] = Var(originalScene)
  val carSpeedText: Signal[String] = speed.signal.map(s => s"Current car speed $s ")

  val carTiming: Var[Time] = Var(
    originalScene.streets
      .flatMap(street => street.lanes.map(lane => lane.vehicleSource.spacingInTime))
      .head
  )

  val carTimingText: Signal[String] = carTiming.signal.map(t => s"Current car timing $t ")

  val pauseText: Signal[String] = paused.signal.map(p => if (p) "Unpause" else "Pause")

  def togglePause(): Unit =
    paused.set(!paused.now())

  def pause(): Unit =
    paused.set(true)

  def loadNamedScene(name: String): Unit = {
    val retrievedSceneAttempt =
      preloadedScenes.find(scene => scene.name.equals(name))
    if (retrievedSceneAttempt.isDefined) {
      loadScene(retrievedSceneAttempt.get.scene)
    } else {
      println("couldn't find a matching scene for name: " + name)
    }

  }

  def loadScene(scene: Scene): Unit = {
    sceneVar.set(scene)
    carTiming.set(sceneVar.now().streets
      .flatMap(street => street.lanes.map(lane => lane.vehicleSource.spacingInTime))
      .head)
    paused.set(true)
  }

  private def reset: Unit = {
    sceneVar.set(originalScene)
    resetScene.set(false)
  }

  private def resetIfNecessary(): Unit =
    if (resetScene.now() == true) {
      reset
    }

  val car: PilotedVehicle =
    PilotedVehicle.commuter2(Spatial.BLANK, new IntelligentDriverModelImpl, Spatial.BLANK)

  private def disrupt(lane: Lane): Lane = {
    this.disruptions.disruptLane.set(false)
    lane.addDisruptiveVehicle(car)
  }

  def disruptLane(lane: Lane): Lane =
    if (this.disruptions.disruptLane.now() == true)
      disrupt(lane)
    else
      lane

  private def disruptExisting(lane: Lane): Lane = {
    this.disruptions.disruptLaneExisting.set(false)
    lane.disruptVehicles()
  }

  def disruptLaneExisting(lane: Lane): Lane =
    if (this.disruptions.disruptLaneExisting.now() == true)
      disruptExisting(lane)
    else
      lane

  private def updateLane(lane: Lane): Lane = {
    val laneAfterDisruption = disruptLane(lane)
    val laneAfterDisruptionExisting = disruptLaneExisting(laneAfterDisruption)

    val newSource =
      laneAfterDisruptionExisting.vehicleSource
        .copy(spacingInTime = this.carTiming.now())
        .updateSpeed(this.speed.now())
    laneAfterDisruptionExisting.copy(vehicleSource = newSource)
  }

  private def updateScene(speedLimit: Velocity) =
    sceneVar.set(sceneVar.now().updateWithSpeedLimit(speedLimit))

  private def updateLanesAndScene(): Unit =
    if (this.paused.now() == false) {
      val newScene = this.sceneVar.now().updateAllStreets(this.updateLane)
      this.sceneVar.set(newScene)
      this.updateScene(this.sceneVar.now().speedLimit)
    }

  def respondToAllInput()(implicit format: Format[Scene]): Unit = {
    this.resetIfNecessary()
    this.updateLanesAndScene()
    serializationFeatures.serializeIfNecessary(this)
    serializationFeatures.deserializeIfNecessary(this)
  }

}
