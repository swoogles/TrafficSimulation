package fr.iscpif.client

import com.billding.traffic.{SceneImpl, StreetImpl}
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

  val savedScene: Var[SceneImpl] = Var(originalScene)
  // TODO Make this private
  var sceneVar: Var[SceneImpl] = Var(originalScene)
  val carTimingText: Rx.Dynamic[String] = Rx(s"Current car timing ${carTiming()} ")
  val carSpeedText: Rx.Dynamic[String] = Rx(s"Current car speed ${speed()} ")

  val disruptLane = disruptions.disruptLane
  val disruptLaneExisting = disruptions.disruptLaneExisting

  def updateSceneWithStreets(streets: List[StreetImpl]) =
    sceneVar() = sceneVar.now.copy(streets = streets)

  def updateScene(speedLimit: Velocity)(implicit dt: Time) =
    sceneVar() = sceneVar.now.update(speedLimit)

  def pause: ModelTrait = this.copy(paused = Var(true))
  def unpause: ModelTrait =   this.copy(paused = Var(false))
  def reset: ModelTrait = {
    sceneVar() = originalScene
    resetScene() = false
    this
  }
}
