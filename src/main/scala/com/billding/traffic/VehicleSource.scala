package com.billding.traffic

import com.billding.physics.Spatial
import squants.{Time, Velocity}

trait VehicleSource {
  def produceVehicle(t: Time, dt: Time, destination: Spatial): Option[PilotedVehicle]
}

case class VehicleSourceImpl(
                              spacingInTime: Time,
                              spatial: Spatial,
                              startingVelocitySpacial: Spatial
) {

  def produceVehicle(t: Time, dt: Time, destination: Spatial): Option[PilotedVehicle] = {
    // Woohoo! this was the problem! t was coming in as ms after loading the new scene for some reason...
    // Should make this prettier/type-safe in the future.
    val res = t.toSeconds % spacingInTime.toSeconds
    if (res.abs < dt.toSeconds) {
      val vehicleSpatial =
        Spatial.withVecs(spatial.r, startingVelocitySpacial.v, spatial.dimensions)
      Some(PilotedVehicle.commuter2(vehicleSpatial, new IntelligentDriverModelImpl, destination))
    } else Option.empty
  }

  def updateSpeed(speed: Velocity): VehicleSourceImpl = {
    val startingV = startingVelocitySpacial.v.normalize.map { x: Velocity =>
      x.value * speed
    }
    this.copy(startingVelocitySpacial = this.startingVelocitySpacial.copy(v = startingV))
  }
}
