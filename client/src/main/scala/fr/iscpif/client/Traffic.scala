sealed trait Maneuver
case object Brake extends Maneuver
case object Accelerate extends Maneuver
case object Maintain extends Maneuver // Should this also be the move when driver is "cooling down" ?
case object Coast extends Maneuver

trait Point3D
trait Vec3D
trait Spatial {
  val pos: Point3D
}

trait Driver {
  val reactionTime: Float
  val pos: Point3D
}

trait Vehicle {
  val speed: Int
  val weight: Int
  val brakingAbility: Float
  // Should this be private and only usable via "vectorBetween" functions and the like?
  val pos: Point3D
}

object Spatial {
  def vecBetween(observer: Spatial, target: Spatial) = ???
}

trait PilotedVehicle {
  val driver: Driver
  val vehicle: Vehicle
  val currentManeuver: Maneuver
  val maneuverTakenAt: Float
}

trait Scene {
  def vehicles(): List[PilotedVehicle]
}

trait VehicleSource {
  def vehicles(): Stream[PilotedVehicle]
  def produceVehicle(t: Float): Option[PilotedVehicle]
  // Figure out how to accommodate both behaviors
  val spacingInDistance: Float
  val spacingInTime: Float
}
object VehicleSource {
  def withTimeSpacing(averageDt: Float): VehicleSource = ???
  def withDistanceSpacing(averageDpos: Float): VehicleSource = ???
}

trait Lane {
  def vehicles(): List[PilotedVehicle]
  val vehicleSource: VehicleSource
}

trait Road {
  def lanes: List[Lane]
  def produceVehicles(t: Float)
}

trait Universe {
  def calculateDriverResponse(vehicle: PilotedVehicle, scene: Scene): Maneuver
  def getAllActions(scene: Scene): List[Maneuver]
  def update(scene: Scene, dt: Float): List[Maneuver]
  def reactTo(decider: PilotedVehicle, obstacle: Vehicle)
}

