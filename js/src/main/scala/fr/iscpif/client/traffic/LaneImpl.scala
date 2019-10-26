package fr.iscpif.client.traffic

import fr.iscpif.client.physics.{Spatial, SpatialImpl}
import squants.motion.Distance
import squants.space.{Length, Meters}
import squants.{QuantityVector, Velocity}

case class LaneImpl(
                     vehicles: List[PilotedVehicle],
                     vehicleSource: VehicleSourceImpl,
                     beginning: SpatialImpl,
                     end: SpatialImpl,
                     speedLimit: Velocity
) extends Lane {

  val length: Length = beginning.distanceTo(end)

  val infinityPointForward: QuantityVector[Distance] =
    beginning.vectorTo(end).normalize.map(_ * 10000)
  val infinityPointBackwards: QuantityVector[Distance] =
    beginning.vectorTo(end).normalize.map(_ * -10000)

  val vehicleAtInfinityForward: PilotedVehicle = {
    val spatial = Spatial.withVecs(infinityPointForward)
    PilotedVehicle.commuter2(spatial, new IntelligentDriverModelImpl, spatial)
  }
  val vehicleAtInfinityBackwards: PilotedVehicle = {
    val spatial = Spatial.withVecs(infinityPointBackwards)
    PilotedVehicle.commuter2(spatial, new IntelligentDriverModelImpl, spatial)
  }
  val infinitySpatial: SpatialImpl = vehicleAtInfinityForward.spatial

  /*
    Look at reusing this for finding leading/following cars in neighboring lane.
   */
  def addDisruptiveVehicle(pilotedVehicle: PilotedVehicle): LaneImpl = {
    val disruptionPoint: QuantityVector[Distance] =
      end.vectorTo(beginning).times(.25)

    val betterVec: QuantityVector[Distance] =
      disruptionPoint.plus(end.r)

    val isPastDisruption =
      (v: PilotedVehicle) =>
        v.spatial.vectorTo(end).magnitude < disruptionPoint.magnitude

    val newPilotedVehicle = pilotedVehicle.move(betterVec)
    val (pastVehicles, approachingVehicles) =
      this.vehicles.partition(isPastDisruption)
    val vehicleList: List[PilotedVehicle] =
      (pastVehicles :+ newPilotedVehicle.target(end)) ::: approachingVehicles
    this.copy(vehicles = vehicleList)
  }

  /**
    * TODO: Also check lane start/end points OR that fraction is between 0 and 1. I think Option B.
    */
  def vehicleCanBePlaced(pilotedVehicle: PilotedVehicle,
                         fractionCompleted: Double): Boolean = {
    val disruptionPoint: QuantityVector[Distance] =
      beginning.vectorTo(end).times(fractionCompleted)

    val vehicleInLane = pilotedVehicle.move(disruptionPoint)
    // TODO Use actual vehicle sizes instead of set meters distance
    val interferes: Boolean =
      this.vehicles.exists(
        curVehicle => curVehicle.distanceTo(vehicleInLane) < Meters(3)
      )

    !interferes
  }

  def disruptVehicles(): LaneImpl = {
    val (pastVehicles, approachingVehicles) =
      this.vehicles.splitAt(this.vehicles.length - 5)

    val (disruptedVehicle :: restOfApproachingVehicles) = approachingVehicles
    val vehicleList: List[PilotedVehicle] =
      (pastVehicles :+ disruptedVehicle.stop()) ::: restOfApproachingVehicles
    this.copy(vehicles = vehicleList)
  }

}
