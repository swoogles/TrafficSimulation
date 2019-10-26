package fr.iscpif.client.traffic

import squants.motion.Acceleration
import squants.Time

trait LaneFunctions {
  // TODO: Test new vehicles from source
  def update(lane: Lane, t: Time, dt: Time): Lane
  def responsesInOneLanePrep(lane: Lane): List[Acceleration]
}
