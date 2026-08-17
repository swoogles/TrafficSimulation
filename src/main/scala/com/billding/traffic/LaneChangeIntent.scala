package com.billding.traffic

import squants.Time
import squants.time.Seconds

/**
  * A driver who has decided to change lanes, and is announcing it before doing anything.
  *
  * MOBIL decides and moves in the same instant, which is fine as physics and hopeless to
  * watch: by the time there is anything on the screen to see, the interesting part - a
  * driver picking a gap - is already over. An intent puts a beat between the decision and
  * the move, which is the only place a warning can be drawn, and is what a real driver does
  * with an indicator anyway.
  *
  * The decision is not binding. When the beat is up the driver looks again at the road as it
  * now stands, and a gap that has closed in the meantime is a change that doesn't happen.
  */
case class LaneChangeIntent(
  to: Int,
  duration: Time,
  elapsed: Time = Seconds(0),
  /**
    * Set for the change the ring is deliberately provoked into, which is going to happen
    * whatever MOBIL thinks of it - the whole point of that button is a driver who shouldn't.
    */
  insistent: Boolean = false
) {

  def advancedBy(dt: Time): LaneChangeIntent = copy(elapsed = elapsed + dt)

  /** Has the driver waited out its own indicator? */
  def isRipe: Boolean = elapsed >= duration

  /**
    * 0 when the driver has just made up its mind, 1 as it starts to cross.
    *
    * Deliberately left to run past 1 for a driver still waiting on its gap, so that whatever
    * is drawing the warning has a clock that keeps ticking rather than a needle stuck at the
    * end of its travel.
    */
  def progress: Double =
    if (duration <= Seconds(0)) 1.0
    else elapsed / duration
}
