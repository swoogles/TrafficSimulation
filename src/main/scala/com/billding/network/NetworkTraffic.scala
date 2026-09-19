package com.billding.network

import java.util.UUID

/**
  * All the vehicles on a [[RoadNetwork]], keyed by which section they are on.
  *
  * Deliberately dumb, the same way [[RoadNetwork]] is: a map and a couple of
  * accessors, with the ordering invariant carried by [[NetworkTraffic.place]]
  * rather than by anything a caller has to remember to do itself.
  */
final case class NetworkTraffic(bySection: Map[SectionId, List[NetworkVehicle]]) {

  /** Every vehicle on the network, exactly once, section order unspecified. */
  def all: List[NetworkVehicle] = bySection.values.toList.flatten

  /** The ordered list of vehicles on `section`, or `Nil` if none are there. */
  def on(section: SectionId): List[NetworkVehicle] = bySection.getOrElse(section, Nil)

  def vehicleWith(uuid: UUID): Option[NetworkVehicle] = all.find(_.piloted.uuid == uuid)
}

object NetworkTraffic {

  val empty: NetworkTraffic = NetworkTraffic(Map.empty[SectionId, List[NetworkVehicle]])

  def of(vehicles: List[NetworkVehicle]): NetworkTraffic =
    vehicles.foldLeft(NetworkTraffic.empty)(place)

  /**
    * Slot `vehicle` into its section's running order.
    *
    * Sorted descending by `s` - leader-first, the same convention `TrackLane`
    * and `Lane` use, where the car nearer the end of the road is the one
    * further along and so the one ahead. A vehicle already present under the
    * same uuid is replaced rather than duplicated, so re-placing a vehicle
    * after it has moved doesn't leave a stale copy behind.
    */
  def place(traffic: NetworkTraffic, vehicle: NetworkVehicle): NetworkTraffic = {
    val withoutStaleCopy =
      traffic.bySection.getOrElse(vehicle.section, Nil).filterNot(_.piloted.uuid == vehicle.piloted.uuid)
    val reordered = (vehicle :: withoutStaleCopy).sortBy(-_.s.toMeters)
    traffic.copy(bySection = traffic.bySection.updated(vehicle.section, reordered))
  }
}
