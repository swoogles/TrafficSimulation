package com.billding

import org.scalatest._
import matchers._
import squants.motion.{Acceleration, MetersPerSecondSquared}

trait SquantsMatchers {

  // TODO Um, unfuck these messages
  object SpeedingUp extends BeMatcher[Acceleration] {
    def apply(left: Acceleration) =
      MatchResult(
        left > MetersPerSecondSquared(0),
        s"""Acceleration was slowing down. """,
        s""
      )
  }

  object SlowingDown extends BeMatcher[Acceleration] {
    def apply(left: Acceleration) =
      MatchResult(
        left < MetersPerSecondSquared(0),
        s"""Acceleration was speeding up. """,
        s""
      )
  }

  class SittingStillMatcher(implicit tolerance: Acceleration) extends BeMatcher[Acceleration] {
    def apply(left: Acceleration) =
      MatchResult(
        left =~ MetersPerSecondSquared(0),
        s"""Acceleration was not constant. """,
        s""
      )
  }

  def MaintainingVelocity(implicit tolerance: Acceleration) = new SittingStillMatcher()
}

// Make them easy to import with:
// import SquantsMatchers._
object SquantsMatchers extends SquantsMatchers
