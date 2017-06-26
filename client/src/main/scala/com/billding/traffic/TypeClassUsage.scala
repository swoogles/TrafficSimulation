package com.billding.traffic

import com.billding.physics.{Spatial, SpatialForDefaults}
import com.billding.physics.SpatialForDefaults.spatialForPilotedVehicle
import squants.motion.KilometersPerHour
import squants.space.Kilometers

/**
  * Created by bfrasure on 6/26/17.
  */
object TypeClassUsage {
  val idm: IntelligentDriverModel = new IntelligentDriverModelImpl
  val drivenVehicle1: PilotedVehicle = PilotedVehicle.commuter( (0, 0, 0, Kilometers), (120, 0, 0, KilometersPerHour), idm)
  val drivenVehicle2: PilotedVehicle = PilotedVehicle.commuter( (0, 2, 0, Kilometers), (120, 0, 0, KilometersPerHour), idm)

  val res: Spatial = SpatialForDefaults.disect(drivenVehicle1)

}
