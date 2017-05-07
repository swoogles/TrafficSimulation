package com.billding

import org.scalatest._
import matchers._
import squants.motion.{Acceleration, MetersPerSecondSquared}

trait SquantsMatchers {

  class AccelerationMatcher() extends BeMatcher[Acceleration] {
    def apply(left: Acceleration) =
      MatchResult(
        left > MetersPerSecondSquared(0),
        s"""Acceleration was slowing down. """,
        s"second message"
      )
  }

  class AccelerationBeMatcher() extends BeMatcher[Acceleration] {
    def apply(left: Acceleration) =
      MatchResult(
        left < MetersPerSecondSquared(0),
        s"""Acceleration was slowing down. """,
        s"second message"
      )
  }

  class SittingStillMatcher(implicit tolerance: Acceleration) extends BeMatcher[Acceleration] {
    def apply(left: Acceleration) =
      MatchResult(
        left =~ MetersPerSecondSquared(0),
        s"""Acceleration was slowing down. """,
        s"second message"
      )
  }

  def speedingUp() = new AccelerationMatcher()
  def slowingDown() = new AccelerationBeMatcher()
  def maintainingVelocity(implicit tolerance: Acceleration) = new SittingStillMatcher()
}

// Make them easy to import with:
// import SquantsMatchers._
object SquantsMatchers extends SquantsMatchers
