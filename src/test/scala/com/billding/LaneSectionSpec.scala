package com.billding

import com.billding.network.{LaneSection, SectionId}
import com.billding.physics.{ArcPath, RingPath, StraightPath}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import squants.motion.{Distance, KilometersPerHour}
import squants.space.Meters
import squants.QuantityVector

import scala.math.Pi

class LaneSectionSpec extends AnyFlatSpec with Matchers {

  private val origin = QuantityVector[Distance](Meters(0), Meters(0), Meters(0))
  private val laneWidth = Meters(3.5)
  private val speedLimit = KilometersPerHour(50)

  "A LaneSection on a straight path" should "report the path's length" in {
    val path = StraightPath(origin, QuantityVector[Distance](Meters(100), Meters(0), Meters(0)))
    val section = LaneSection(SectionId("mainline-0"), path, laneWidth, speedLimit, layer = 0)

    section.length shouldBe path.totalLength
  }

  "A LaneSection on an arc path" should "report the path's length" in {
    val path = ArcPath(origin, radius = Meters(20), startAngle = 0.0, sweep = Pi / 2)
    val section = LaneSection(SectionId("bend-0"), path, laneWidth, speedLimit, layer = 0)

    section.length shouldBe path.totalLength
  }

  "A LaneSection on a ring path" should "report the path's length" in {
    val path = RingPath.ofCircumference(Meters(400))
    val section = LaneSection(SectionId("loop-0"), path, laneWidth, speedLimit, layer = 0)

    section.length shouldBe path.totalLength
  }

  "SectionId" should "compare equal by value" in {
    SectionId("mainline-0") shouldBe SectionId("mainline-0")
    SectionId("mainline-0") should not be SectionId("mainline-1")
  }
}
