package com.billding

import com.billding.physics.{RingPath, StraightPath}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import squants.motion.Distance
import squants.space.{Length, Meters}
import squants.QuantityVector

class PathSpec extends AnyFlatSpec with Matchers {

  private val Tolerance = 1e-9

  private val ring = RingPath.ofCircumference(Meters(400))

  private def metersFrom(vector: QuantityVector[Distance]): Seq[Double] =
    vector.coordinates.map(_.toMeters)

  private def distanceFromCenter(s: Length): Double =
    (ring.pointAt(s) - ring.center).magnitude.toMeters

  "A ring path" should "close on itself after one lap" in {
    metersFrom(ring.pointAt(ring.totalLength))
      .zip(metersFrom(ring.pointAt(Meters(0))))
      .foreach {
        case (afterALap, atTheStart) => afterALap shouldBe atTheStart +- Tolerance
      }
  }

  it should "keep every arc length out on the circle" in
  (0 to 20).foreach { twentieth =>
    distanceFromCenter(ring.totalLength * (twentieth / 20.0)) shouldBe
    ring.radius.toMeters +- Tolerance
  }

  it should "head perpendicular to the radius" in
  (0 to 20).foreach { twentieth =>
    val s = ring.totalLength * (twentieth / 20.0)
    val outward = (ring.pointAt(s) - ring.center).map { component: Distance =>
      component.toMeters
    }.normalize
    val alongTheRoad = ring.headingAt(s)

    val dotProduct =
      outward.coordinates.zip(alongTheRoad.coordinates).map { case (a, b) => a * b }.sum

    dotProduct shouldBe 0.0 +- Tolerance
  }

  it should "wrap arc lengths back onto the loop" in {
    ring.normalize(Meters(450)).toMeters shouldBe 50.0 +- Tolerance
    ring.normalize(Meters(-50)).toMeters shouldBe 350.0 +- Tolerance
    ring.normalize(Meters(400)).toMeters shouldBe 0.0 +- Tolerance
    ring.normalize(Meters(-850)).toMeters shouldBe 350.0 +- Tolerance
  }

  it should "measure gaps forward around the loop" in {
    ring.forwardGap(Meters(350), Meters(50)).toMeters shouldBe 100.0 +- Tolerance
    ring.forwardGap(Meters(50), Meters(350)).toMeters shouldBe 300.0 +- Tolerance
  }

  it should "split the whole loop between any two cars" in
  (0 to 20).foreach { twentieth =>
    val ahead = Meters(17.5)
    val behind = ring.totalLength * (twentieth / 21.0)
    val bothWaysRound =
      ring.forwardGap(ahead, behind) + ring.forwardGap(behind, ahead)

    bothWaysRound.toMeters shouldBe ring.totalLength.toMeters +- Tolerance
  }

  /**
    * The failure the whole arc-length design exists to prevent: straight-line distance says
    * these two cars are on top of each other, when in fact one has the entire loop ahead of it.
    */
  it should "report the road ahead, not the chord, to the car it just lapped" in {
    val head = Meters(0)
    val justBehindIt = Meters(375)

    ring.forwardGap(head, justBehindIt).toMeters shouldBe 375.0 +- Tolerance
    (ring.pointAt(justBehindIt) - ring.pointAt(head)).magnitude should be < Meters(25)
  }

  private val straight = StraightPath(
    QuantityVector[Distance](Meters(0), Meters(0), Meters(0)),
    QuantityVector[Distance](Meters(30), Meters(40), Meters(0))
  )

  "A straight path" should "measure its own length" in {
    straight.totalLength.toMeters shouldBe 50.0 +- Tolerance
  }

  it should "place arc lengths along itself" in {
    metersFrom(straight.pointAt(Meters(25))) shouldBe Seq(15.0, 20.0, 0.0)
    metersFrom(straight.pointAt(Meters(0))) shouldBe Seq(0.0, 0.0, 0.0)
    metersFrom(straight.pointAt(straight.totalLength)) shouldBe Seq(30.0, 40.0, 0.0)
  }

  it should "hold one heading the whole way" in {
    straight.headingAt(Meters(0)).coordinates shouldBe Seq(0.6, 0.8, 0.0)
    straight.headingAt(Meters(50)).coordinates shouldBe straight.headingAt(Meters(0)).coordinates
  }

  it should "clamp arc lengths to its ends rather than wrapping" in {
    straight.normalize(Meters(-10)).toMeters shouldBe 0.0
    straight.normalize(Meters(80)).toMeters shouldBe 50.0
    straight.normalize(Meters(20)).toMeters shouldBe 20.0
  }

  it should "report a negative gap to a car that is behind" in {
    straight.forwardGap(Meters(40), Meters(10)).toMeters shouldBe -30.0 +- Tolerance
  }
}
