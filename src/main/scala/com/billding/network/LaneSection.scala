package com.billding.network

import com.billding.physics.Path
import squants.motion.Velocity
import squants.space.Length

/**
  * One directed physical lane: a `Path` to drive along, how wide it is, its
  * speed limit, and the elevation level it sits on.
  *
  * This is geometry and identity only. Nothing here knows what a vehicle is -
  * no occupancy, no queue, no mutable state. Runtime traffic (phase C) is a
  * layer built on top of a graph of these, never a field on one of them.
  *
  * `layer` is a physical level, not a drawing order: two sections whose plan
  * views overlap on different layers (an overpass and the road beneath it)
  * have no relationship at all, and are not candidates for a movement between
  * them just because they cross on screen.
  */
final case class LaneSection(
  id: SectionId,
  path: Path,
  width: Length,
  speedLimit: Velocity,
  layer: Int
) {

  def length: Length = path.totalLength
}
