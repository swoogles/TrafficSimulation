package fr.iscpif.client.previouslySharedCode.traffic

import squants.motion.Acceleration
import squants.Time

trait LaneFunctions {
  // TODO: Test new vehicles from source
  def update(lane: LaneImpl, t: Time, dt: Time): Lane
  def responsesInOneLanePrep(lane: Lane): List[Acceleration]
}
