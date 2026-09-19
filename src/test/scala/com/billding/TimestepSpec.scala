package com.billding

import com.billding.uimodules.Timestep
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import squants.Time
import squants.time.Milliseconds

/**
  * [[Timestep.accumulate]] is the whole of W8's catch-up policy, with no DOM and no animation
  * frame involved: given whatever fraction of a step was left over last time and how much
  * wall time just elapsed, how many whole fixed steps does that buy, and what's left over.
  *
  * The two things the card cares about are both here: that frame rate doesn't change how much
  * simulated time a given stretch of wall time produces (as long as no single frame is asked
  * to catch up more than the cap allows), and that a long stall is bounded rather than made up
  * in full.
  */
class TimestepSpec extends AnyFlatSpec with Matchers {

  private val step = Timestep.FixedStep
  private val maxSteps = Timestep.MaxStepsPerFrame
  private val zero: Time = Milliseconds(0)

  "accumulate" should "run one step and carry no remainder when elapsed is exactly one step" in {
    val (steps, remainder) = Timestep.accumulate(zero, step)
    steps shouldBe 1
    remainder shouldBe zero
  }

  it should "run no steps yet when elapsed is less than one step, and carry all of it forward" in {
    val elapsed = Milliseconds(40)
    val (steps, remainder) = Timestep.accumulate(zero, elapsed)
    steps shouldBe 0
    remainder shouldBe elapsed
  }

  it should "combine a carried remainder with newly elapsed time before deciding steps" in {
    // 60ms left over from before, 50ms more now: 110ms total is one whole 100ms step with
    // 10ms left over - not zero steps just because the new slice alone is under a step.
    val (steps, remainder) = Timestep.accumulate(Milliseconds(60), Milliseconds(50))
    steps shouldBe 1
    remainder shouldBe Milliseconds(10)
  }

  it should "never carry back a remainder as large as a whole step" in {
    val (steps, remainder) = Timestep.accumulate(Milliseconds(90), Milliseconds(90))
    steps shouldBe 1
    remainder shouldBe Milliseconds(80)
    remainder should be < step
  }

  it should "cap a long stall at the maximum steps per frame" in {
    // A 2-second stall is 20 whole steps' worth of wall time; W8 caps catch-up at 5.
    val (steps, _) = Timestep.accumulate(zero, Milliseconds(2000))
    steps shouldBe maxSteps
    steps should be < 20
  }

  it should "spend, not bank, the time beyond a capped stall's cutoff" in {
    // The 15 steps beyond the cap are gone for good, not queued up to run on some later
    // frame - otherwise a single long stall would eventually catch itself up in full, which
    // is exactly the freeze-on-resume behaviour the cap exists to avoid.
    val (steps, remainder) = Timestep.accumulate(zero, Milliseconds(2000))
    steps shouldBe maxSteps
    remainder shouldBe zero // 2000ms is an exact multiple of the 100ms step
  }

  it should "leave the remainder under one step even when a stall is capped" in {
    val (_, remainder) = Timestep.accumulate(zero, Milliseconds(2037))
    remainder should be < step
    remainder shouldBe Milliseconds(37)
  }

  it should "not run a negative number of steps when elapsed is zero" in {
    val (steps, remainder) = Timestep.accumulate(zero, zero)
    steps shouldBe 0
    remainder shouldBe zero
  }

  /**
    * The card's headline property: the same simulated duration comes out the same whether
    * it's delivered in one frame callback or many, as long as no single frame's elapsed time
    * alone would exceed the cap. Half a second - five steps, right at the cap boundary - fed
    * either as one lump or as twenty 25ms slivers ends at the same step count and remainder.
    */
  it should "produce the same total steps for the same duration whether stepped in 1 frame or 20" in {
    val totalDuration = Milliseconds(500)

    val (oneFrameSteps, oneFrameRemainder) = Timestep.accumulate(zero, totalDuration)

    val twentySlivers = List.fill(20)(Milliseconds(25))
    val (twentyFrameSteps, twentyFrameRemainder) =
      twentySlivers.foldLeft((0, zero)) {
        case ((totalSteps, remainder), elapsed) =>
          val (steps, newRemainder) = Timestep.accumulate(remainder, elapsed)
          (totalSteps + steps, newRemainder)
      }

    twentyFrameSteps shouldBe oneFrameSteps
    twentyFrameRemainder shouldBe oneFrameRemainder
    oneFrameSteps shouldBe 5
  }

  it should "also agree for an odd split into a handful of uneven frames" in {
    val totalDuration = Milliseconds(500)
    val unevenFrames = List(Milliseconds(130), Milliseconds(70), Milliseconds(300))
    unevenFrames.reduce(_ + _) shouldBe totalDuration

    val (steps, remainder) =
      unevenFrames.foldLeft((0, zero)) {
        case ((totalSteps, carry), elapsed) =>
          val (n, newCarry) = Timestep.accumulate(carry, elapsed)
          (totalSteps + n, newCarry)
      }

    steps shouldBe 5
    remainder shouldBe zero
  }

  it should "still bound a stall reached gradually across a handful of already-late frames" in {
    // Two 1-second-late frames in a row: each alone is capped at 5, so ten steps total, not
    // the twenty a naive accumulator would eventually work off across only two more frames.
    val (firstSteps, firstRemainder) = Timestep.accumulate(zero, Milliseconds(1000))
    val (secondSteps, secondRemainder) = Timestep.accumulate(firstRemainder, Milliseconds(1000))

    firstSteps shouldBe maxSteps
    secondSteps shouldBe maxSteps
    secondRemainder shouldBe zero
  }
}
