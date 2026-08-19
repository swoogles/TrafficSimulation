package com.billding.traffic

import squants.Time
import squants.time.Seconds

/**
  * How many cars have finished the course, and how fast they are finishing.
  *
  * The total belongs to the simulation - a lane knows when a car has driven off the end of
  * it, and a ring knows when one has gone past the counting line. A rate does not: it only
  * exists relative to a stretch of time somebody picked, so it is worked out here, from the
  * finishers that fall inside a window that moves along with the clock.
  *
  * A running total is all the simulation is asked for, rather than a stream of events. It is
  * the one thing a lane can report that cannot go wrong when the page resets a scene, reloads
  * a preset or drops a car for want of room: a number that only ever goes up, and whose
  * increments this has to notice rather than be told about.
  */
case class CompletionTally(
  total: Int = 0,
  /** When each recent finisher finished, newest first. Anything older than the window is gone. */
  recent: List[Time] = Nil,
  /** The clock reading this was last brought up to date with. */
  clock: Time = Seconds(0),
  /** When it started watching, so an early reading isn't divided by hardly any time at all. */
  watchingSince: Option[Time] = None
) {

  /**
    * Take a new running total off the simulation, with the clock reading `now`.
    *
    * The finishers are dated by the tick that noticed them rather than by the moment they
    * crossed, because the tick is all the resolution there is to have - at a tenth of a
    * second, a car is either over the line this frame or it is not.
    */
  def observing(runningTotal: Int, now: Time): CompletionTally = {
    val arrived = math.max(0, runningTotal - total)
    val cutoff = now - CompletionTally.Window
    CompletionTally(
      runningTotal,
      (List.fill(arrived)(now) ::: recent).filter(_ > cutoff),
      now,
      Some(watchingSince.getOrElse(now))
    )
  }

  /**
    * Cars a minute over the recent past, or None while there is too little past to divide by.
    *
    * The window rather than the whole run, because the question is what the road is doing
    * now: turn the density dial up and a lifetime average would spend the rest of the run
    * admitting it.
    *
    * Minutes of the traffic's time, which is not minutes of yours. The page advances the
    * simulation a tenth of a second per frame, so the clock the cars keep runs several times
    * faster than the one you are watching them on, and this is a rate on theirs.
    */
  def perMinute: Option[Double] =
    watchingSince
      .map(started => CompletionTally.atMost(clock - started, CompletionTally.Window))
      .filter(_ >= CompletionTally.MinimumSpan)
      .map(span => recent.size / span.toMinutes)
}

object CompletionTally {

  /**
    * How much of the recent past a rate is measured over.
    *
    * Long enough that a handful of cars reads as a rate rather than as a coincidence, short
    * enough that it follows the dials. Half a minute of the traffic's time is a few seconds
    * of watching, which is about as long as anyone will wait for a number to catch up.
    */
  val Window: Time = Seconds(30)

  /**
    * Below this there is not enough time behind a reading for it to be one.
    *
    * Without it the first car over the line arrives after a tenth of a second of watching and
    * reads as six hundred an hour, which is a true division and a useless number.
    */
  val MinimumSpan: Time = Seconds(5)

  private def atMost(span: Time, limit: Time): Time = if (span < limit) span else limit
}
