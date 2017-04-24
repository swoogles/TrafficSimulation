sealed trait Maneuver
case object Brake extends Maneuver
case object Accelerate extends Maneuver
case object Maintain extends Maneuver
case object Coast extends Maneuver

trait Driver {
  val responseTime: Float
}

trait Vehicle {
  val speed: Int
  val weight: Int
  val brakingAbility: Float
}

trait PilotedVehicle {
  val driver: Driver
  val vehicle: Vehicle
}

trait Scene {
  def vehicles(): List[PilotedVehicle]
}

trait Universe {
  def calculateDriverResponse(vehicle: PilotedVehicle, scene: Scene): Maneuver
  def getAllActions(scene: Scene): List[Maneuver]
}

trait VehicleSource {
  def vehicles(): Stream[PilotedVehicle]
}

trait Lane {
  def vehicles(): List[PilotedVehicle]
}

trait Road {
  def lanes: List[Lane]
}