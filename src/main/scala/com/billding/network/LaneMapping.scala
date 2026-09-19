package com.billding.network

import squants.space.Length

/**
  * Maps a position on one section to the corresponding position on a
  * neighbouring lane, using the [[LaneNeighbour]] intervals `RoadNetwork`
  * carries rather than the whole-lane-progress assumption in
  * `TrackRoad.transpose`.
  *
  * `transpose` preserves fraction-of-total-length, which is only meaningful
  * when both lanes span the same stretch of road - true of concentric rings,
  * false the moment lanes differ in length, which is exactly what an
  * acceleration lane is. This looks up the interval that actually says where
  * `section` and its neighbour run side by side, and only answers inside it.
  */
object LaneMapping {

  /**
    * Where `s` on `section` lands on the lane to `side` of it, or [[None]]
    * when no [[LaneNeighbour]] on that side covers `s` - which is the right
    * answer beyond a ramp's acceleration region or a lane drop, not a
    * missing case to paper over.
    */
  def neighbourAt(net: RoadNetwork, section: SectionId, s: Length, side: Side): Option[(SectionId, Length)] =
    net
      .neighboursOf(section)
      .filter(_.side == side)
      .flatMap(n => n.neighbourPosition(s).map(mapped => (n.neighbour, mapped)))
      .headOption
}
