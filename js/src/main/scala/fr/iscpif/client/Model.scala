package fr.iscpif.client

import com.billding.traffic.{SceneImpl, StreetImpl}
import rx.{Ctx, Rx, Var}
import squants.Time
import squants.motion.{KilometersPerHour, Velocity}
import squants.time.Seconds

case class Model (
  originalScene: SceneImpl,
  speed: Var[Velocity] = Var(KilometersPerHour(50)),
  carTiming: Var[Time] = Var(Seconds(3)),
  paused: Var[Boolean] = Var(false),
  disruptLane: Var[Boolean] = Var(false),
  disruptLaneExisting: Var[Boolean] = Var(false),
  resetScene: Var[Boolean] = Var(false),
  serializeScene: Var[Boolean] = Var(false),
  deserializeScene: Var[Boolean] = Var(false),
  vehicleCount: Var[Int] = Var(0)
)(implicit ctx: Ctx.Owner) {
  val savedScene: Var[SceneImpl] = Var(originalScene)
  var sceneVar: Var[SceneImpl] = Var(originalScene)
  val carTimingText: Rx.Dynamic[String] = Rx(s"Current car timing ${carTiming()} ")
  val carSpeedText: Rx.Dynamic[String] = Rx(s"Current car speed ${speed()} ")

  def updateSceneWithStreets(streets: List[StreetImpl]) =
    sceneVar() = sceneVar.now.copy(streets = streets)

  def updateScene(speedLimit: Velocity)(implicit dt: Time) =
    sceneVar() = sceneVar.now.update(speedLimit)
}
