package com.billding.traffic

import squants.Acceleration
import squants.motion.MetersPerSecondSquared
import squants.time.{Seconds, Time}

/**
  * The six accelerations a lane change is judged by.
  *
  * MOBIL is written in terms of what every affected driver would be doing before and after,
  * so gathering them in one place keeps the criteria themselves readable as the formulas
  * they are. Whoever builds one of these owns the geometry; this file owns the judgement.
  *
  * A missing follower contributes zero to both of its terms, which is the right answer:
  * a change nobody is behind inconveniences nobody.
  */
case class LaneChangeSituation(
  selfBefore: Acceleration, // acc(M)
  selfAfter: Acceleration, // acc'(M')
  oldFollowerBefore: Acceleration, // acc(B)
  oldFollowerAfter: Acceleration, // acc'(B)
  newFollowerBefore: Acceleration, // acc(B')
  newFollowerAfter: Acceleration // acc'(B')
) {

  def gainToSelf: Acceleration = selfAfter - selfBefore

  def costToOthers: Acceleration =
    (oldFollowerBefore - oldFollowerAfter) + (newFollowerBefore - newFollowerAfter)
}

/**
  * MOBIL: Minimizing Overall Braking Induced by Lane Change.
  *
  * A driver changes lane when two things hold at once: the car it would cut in front of can
  * still stop safely, and the whole neighbourhood comes out far enough ahead to be worth the
  * bother. `politeness` is how much of the neighbourhood's loss the driver counts as its own,
  * and it is the interesting dial: at zero the driver takes any gap that suits it and leaves
  * the car behind to sort itself out, which is exactly the errant change that starts a wave.
  *
  * Resources:
  * http://akesting.de/download/MOBIL_TRR_2007.pdf
  * http://www.traffic-simulation.de/info/MOBIL.html
  */
case class MOBIL(
  politeness: Double = MOBIL.DefaultPoliteness,
  threshold: Acceleration = MOBIL.DefaultThreshold,
  safeBraking: Acceleration = MOBIL.DefaultSafeBraking,
  keepRightBias: Acceleration = MOBIL.DefaultKeepRightBias,
  cooldown: Time = MOBIL.DefaultCooldown
) {

  /**
    * Would the car being cut in front of have to brake harder than it can bear?
    *
    * This one is not negotiable and not weighted by politeness: however much a driver wants
    * a gap, it doesn't take one that puts somebody into the back of it.
    */
  def isSafe(situation: LaneChangeSituation): Boolean =
    situation.newFollowerAfter > -safeBraking

  /**
    * acc'(M') - acc(M) + p [ (acc'(B) - acc(B)) + (acc'(B') - acc(B')) ] > a_thr
    *
    * Written as gain-minus-weighted-cost, which is the same inequality read out loud.
    */
  def incentive(situation: LaneChangeSituation): Acceleration =
    situation.gainToSelf - situation.costToOthers * politeness

  /**
    * Nudge towards the outside lane, so the inside one is somewhere you overtake rather than
    * somewhere you sit. Without it traffic spreads evenly and no one ever moves back over,
    * which costs you most of the lane changes worth watching.
    */
  def biasFor(from: Int, to: Int): Acceleration =
    if (to < from) keepRightBias else -keepRightBias

  /** The whole decision: safe enough to do, and worth enough to bother. */
  def wants(situation: LaneChangeSituation, from: Int, to: Int): Boolean =
    isSafe(situation) && motivation(situation, from, to) > threshold

  /** How badly, for ranking competing changes against each other. */
  def motivation(situation: LaneChangeSituation, from: Int, to: Int): Acceleration =
    incentive(situation) + biasFor(from, to)
}

object MOBIL {

  /** Treiber's suggested politeness. Drop it towards zero to get selfish merges. */
  val DefaultPoliteness: Double = 0.2

  /** How much better off a change has to leave things before it is worth making. */
  val DefaultThreshold: Acceleration = MetersPerSecondSquared(0.2)

  /** The hardest braking a driver is willing to force on the car it cuts in front of. */
  val DefaultSafeBraking: Acceleration = MetersPerSecondSquared(4.0)

  val DefaultKeepRightBias: Acceleration = MetersPerSecondSquared(0.3)

  /** Long enough that a car settles into its new lane instead of dithering across the line. */
  val DefaultCooldown: Time = Seconds(3)

  /** The eagerness slider's range, from "only if it really helps" to "at the slightest gain". */
  val MostReluctant: Acceleration = MetersPerSecondSquared(0.8)

  /**
    * Turn a 0-to-1 eagerness into the threshold it means, so the control reads the way round
    * a person expects: more eagerness, more lane changes.
    */
  def thresholdFor(eagerness: Double): Acceleration =
    MostReluctant * (1.0 - math.max(0.0, math.min(1.0, eagerness)))
}
