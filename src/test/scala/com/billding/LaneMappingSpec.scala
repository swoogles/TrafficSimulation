package com.billding

import com.billding.network._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import squants.space.Meters

class LaneMappingSpec extends AnyFlatSpec with Matchers {

  "neighbourAt" should "round-trip a position inside a ramp's acceleration window" in {
    val (network, _) = NetworkFixtures.rampMerge(mainlineLength = Meters(250), accelLength = Meters(150))

    val s = Meters(75)
    val there = LaneMapping.neighbourAt(network, SectionId("mainline-accel"), s, Side.RightOf)
    there shouldBe Some((SectionId("ramp-accel"), s))

    val back = there.flatMap { case (section, mapped) =>
      LaneMapping.neighbourAt(network, section, mapped, Side.LeftOf)
    }
    back shouldBe Some((SectionId("mainline-accel"), s))
  }

  it should "answer None just outside the window, on either end" in {
    val (network, _) = NetworkFixtures.rampMerge(mainlineLength = Meters(250), accelLength = Meters(150))

    LaneMapping.neighbourAt(network, SectionId("mainline-accel"), Meters(-1), Side.RightOf) shouldBe None
    LaneMapping.neighbourAt(network, SectionId("mainline-accel"), Meters(151), Side.RightOf) shouldBe None

    // The endpoints themselves are inside the (inclusive) interval.
    LaneMapping.neighbourAt(network, SectionId("mainline-accel"), Meters(0), Side.RightOf) shouldBe
      Some((SectionId("ramp-accel"), Meters(0)))
    LaneMapping.neighbourAt(network, SectionId("mainline-accel"), Meters(150), Side.RightOf) shouldBe
      Some((SectionId("ramp-accel"), Meters(150)))
  }

  it should "map a ramp adjacent over only 150 m of a 400 m mainline within that window alone" in {
    // mainline-approach (250 m) then mainline-accel (150 m): 400 m of mainline in
    // total, but the ramp is only ever alongside the 150 m mainline-accel
    // section. neighbourAt is asked in mainline-accel's own local coordinates,
    // so every position on that section (0 to 150 m) is within the window, and
    // there is nothing to ask on mainline-approach at all - it carries no
    // LaneNeighbour, so every position on it answers None.
    val (network, _) = NetworkFixtures.rampMerge(mainlineLength = Meters(250), accelLength = Meters(150))

    network.section(SectionId("mainline-approach")).get.length shouldBe Meters(250)
    network.section(SectionId("mainline-accel")).get.length shouldBe Meters(150)

    LaneMapping.neighbourAt(network, SectionId("mainline-approach"), Meters(100), Side.RightOf) shouldBe None
    LaneMapping.neighbourAt(network, SectionId("mainline-accel"), Meters(100), Side.RightOf) shouldBe
      Some((SectionId("ramp-accel"), Meters(100)))
  }

  it should "ignore a neighbour declared on the other side" in {
    val (network, _) = NetworkFixtures.rampMerge(mainlineLength = Meters(250), accelLength = Meters(150))

    // mainline-accel's ramp neighbour is to its RightOf; nothing sits LeftOf it.
    LaneMapping.neighbourAt(network, SectionId("mainline-accel"), Meters(75), Side.LeftOf) shouldBe None
  }

  it should "apply a non-zero offset when mapping into the neighbour's own coordinates" in {
    val (network, _) = NetworkFixtures.straightChain(1, Meters(100), lanes = 2)

    LaneMapping.neighbourAt(network, SectionId("mainline-0-lane-0"), Meters(40), Side.LeftOf) shouldBe
      Some((SectionId("mainline-0-lane-1"), Meters(40)))
    LaneMapping.neighbourAt(network, SectionId("mainline-0-lane-1"), Meters(40), Side.RightOf) shouldBe
      Some((SectionId("mainline-0-lane-0"), Meters(40)))
  }

  it should "find nothing outside every declared interval, including a gap between two on the same side" in {
    val laneNeighbours = List(
      LaneNeighbour(SectionId("a"), SectionId("b"), Side.LeftOf, Meters(0), Meters(50), Meters(0)),
      LaneNeighbour(SectionId("a"), SectionId("b"), Side.LeftOf, Meters(100), Meters(150), Meters(0))
    )
    val network = RoadNetwork(Map.empty, Nil, laneNeighbours)

    LaneMapping.neighbourAt(network, SectionId("a"), Meters(25), Side.LeftOf) shouldBe
      Some((SectionId("b"), Meters(25)))
    LaneMapping.neighbourAt(network, SectionId("a"), Meters(75), Side.LeftOf) shouldBe None
    LaneMapping.neighbourAt(network, SectionId("a"), Meters(125), Side.LeftOf) shouldBe
      Some((SectionId("b"), Meters(125)))
    LaneMapping.neighbourAt(network, SectionId("a"), Meters(200), Side.LeftOf) shouldBe None
    LaneMapping.neighbourAt(network, SectionId("c"), Meters(25), Side.LeftOf) shouldBe None
  }
}
