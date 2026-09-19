package com.billding

import com.billding.network._
import com.billding.physics.StraightPath
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import squants.motion.{Distance, KilometersPerHour}
import squants.space.Meters
import squants.QuantityVector

class LaneAdjacencySpec extends AnyFlatSpec with Matchers {

  private val laneWidth = Meters(3.5)
  private val speedLimit = KilometersPerHour(50)

  private def straightSection(id: String, length: Double): LaneSection =
    LaneSection(
      SectionId(id),
      StraightPath(
        QuantityVector[Distance](Meters(0), Meters(0), Meters(0)),
        QuantityVector[Distance](Meters(length), Meters(0), Meters(0))
      ),
      laneWidth,
      speedLimit,
      layer = 0
    )

  "Two equal-length parallel sections" should "map positions symmetrically, there and back" in {
    val left = straightSection("left", 200)
    val right = straightSection("right", 200)

    val leftToRight = LaneNeighbour(
      section = SectionId("left"),
      neighbour = SectionId("right"),
      side = Side.RightOf,
      fromS = Meters(0),
      toS = Meters(200),
      offset = Meters(0)
    )
    val rightToLeft = LaneNeighbour(
      section = SectionId("right"),
      neighbour = SectionId("left"),
      side = Side.LeftOf,
      fromS = Meters(0),
      toS = Meters(200),
      offset = Meters(0)
    )

    val network = RoadNetwork(
      sections = Map(SectionId("left") -> left, SectionId("right") -> right),
      movements = Nil,
      laneNeighbours = List(leftToRight, rightToLeft)
    )

    network.neighboursOf(SectionId("left")) shouldBe List(leftToRight)
    network.neighboursOf(SectionId("right")) shouldBe List(rightToLeft)

    val s = Meters(123.4)
    val there = leftToRight.neighbourPosition(s)
    there shouldBe Some(s)

    val back = there.flatMap(rightToLeft.neighbourPosition)
    back shouldBe Some(s)
  }

  "A short ramp adjacent to the mainline over a 150 m window" should
    "map correctly inside the window and report nothing outside it" in {
    val mainline = straightSection("mainline", 500)
    val ramp = straightSection("ramp", 150)

    // The ramp joins the mainline starting 200 m in, and offers 150 m of
    // acceleration lane before merging - shorter than either section, which is
    // the case TrackRoad.transpose cannot express: it is not a uniform fraction
    // of either section's whole length.
    val mainlineToRamp = LaneNeighbour(
      section = SectionId("mainline"),
      neighbour = SectionId("ramp"),
      side = Side.RightOf,
      fromS = Meters(200),
      toS = Meters(350),
      offset = Meters(-200)
    )
    val rampToMainline = LaneNeighbour(
      section = SectionId("ramp"),
      neighbour = SectionId("mainline"),
      side = Side.LeftOf,
      fromS = Meters(0),
      toS = Meters(150),
      offset = Meters(200)
    )

    val network = RoadNetwork(
      sections = Map(SectionId("mainline") -> mainline, SectionId("ramp") -> ramp),
      movements = Nil,
      laneNeighbours = List(mainlineToRamp, rampToMainline)
    )

    // Inside the window: a car 250 m along the mainline is 50 m along the ramp.
    mainlineToRamp.neighbourPosition(Meters(250)) shouldBe Some(Meters(50))
    // And mapping back is the identity.
    mainlineToRamp
      .neighbourPosition(Meters(250))
      .flatMap(rampToMainline.neighbourPosition) shouldBe Some(Meters(250))

    // The window's own edges are included.
    mainlineToRamp.neighbourPosition(Meters(200)) shouldBe Some(Meters(0))
    mainlineToRamp.neighbourPosition(Meters(350)) shouldBe Some(Meters(150))

    // Outside the window, on the mainline itself, there is no ramp alongside it.
    mainlineToRamp.neighbourPosition(Meters(100)) shouldBe None
    mainlineToRamp.neighbourPosition(Meters(400)) shouldBe None

    // The network's own accessor only reports what was declared for that section.
    network.neighboursOf(SectionId("mainline")) shouldBe List(mainlineToRamp)
    network.neighboursOf(SectionId("ramp")) shouldBe List(rampToMainline)
  }

  "A neighbour interval" should "refuse to be built backwards" in {
    an[IllegalArgumentException] should be thrownBy {
      LaneNeighbour(
        section = SectionId("a"),
        neighbour = SectionId("b"),
        side = Side.LeftOf,
        fromS = Meters(100),
        toS = Meters(50),
        offset = Meters(0)
      )
    }
  }
}
