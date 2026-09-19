package com.billding

import com.billding.network._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import squants.space.Meters

class NetworkFixturesSpec extends AnyFlatSpec with Matchers {

  "straightChain(3, Meters(200))" should "total 600 m across two continuation movements" in {
    val (network, index) = NetworkFixtures.straightChain(3, Meters(200))

    NetworkValidation.faults(network) shouldBe Nil

    network.sections.keySet shouldBe Set(SectionId("mainline-0"), SectionId("mainline-1"), SectionId("mainline-2"))
    val totalLength = network.sections.values.map(_.length).reduce(_ + _)
    totalLength shouldBe Meters(600)

    network.movements should have size 2
    network.movements.map(_.id) shouldBe List(MovementId("mainline-0-1"), MovementId("mainline-1-2"))
    network.movements.foreach { movement =>
      movement.kind shouldBe MovementKind.Continuation
      movement.control shouldBe Control.Uncontrolled
    }

    index.successors(SectionId("mainline-0")) shouldBe List(SectionId("mainline-1"))
    index.successors(SectionId("mainline-1")) shouldBe List(SectionId("mainline-2"))
    index.successors(SectionId("mainline-2")) shouldBe Nil
  }

  it should "give every lane in a multi-lane chain its own continuation chain, with adjacency between neighbours" in {
    val (network, index) = NetworkFixtures.straightChain(2, Meters(100), lanes = 2)

    NetworkValidation.faults(network) shouldBe Nil

    network.sections.keySet shouldBe Set(
      SectionId("mainline-0-lane-0"),
      SectionId("mainline-1-lane-0"),
      SectionId("mainline-0-lane-1"),
      SectionId("mainline-1-lane-1")
    )
    network.movements.map(_.id) should contain theSameElementsAs List(
      MovementId("mainline-0-1-lane-0"),
      MovementId("mainline-0-1-lane-1")
    )

    index.successors(SectionId("mainline-0-lane-0")) shouldBe List(SectionId("mainline-1-lane-0"))
    index.successors(SectionId("mainline-0-lane-1")) shouldBe List(SectionId("mainline-1-lane-1"))

    // Lane 0 (rightmost) and lane 1 are adjacent for the whole of each segment,
    // symmetrically.
    network.neighboursOf(SectionId("mainline-0-lane-0")) shouldBe List(
      LaneNeighbour(
        SectionId("mainline-0-lane-0"),
        SectionId("mainline-0-lane-1"),
        Side.LeftOf,
        Meters(0),
        Meters(100),
        Meters(0)
      )
    )
    network.neighboursOf(SectionId("mainline-0-lane-1")) shouldBe List(
      LaneNeighbour(
        SectionId("mainline-0-lane-1"),
        SectionId("mainline-0-lane-0"),
        Side.RightOf,
        Meters(0),
        Meters(100),
        Meters(0)
      )
    )
  }

  "ySplit" should "build a fault-free network with a trunk diverging into two branches" in {
    val (network, index) = NetworkFixtures.ySplit(Meters(80), Meters(40))

    NetworkValidation.faults(network) shouldBe Nil

    network.sections.keySet shouldBe Set(SectionId("trunk"), SectionId("branch-left"), SectionId("branch-right"))
    index.successors(SectionId("trunk")) shouldBe List(SectionId("branch-left"), SectionId("branch-right"))

    network.movements.foreach { movement =>
      movement.kind shouldBe MovementKind.Diverge
      movement.control shouldBe Control.Uncontrolled
    }

    network.section(SectionId("branch-left")).get.length.toMeters shouldBe 40.0 +- 1e-6
    network.section(SectionId("branch-right")).get.length.toMeters shouldBe 40.0 +- 1e-6
  }

  "rampMerge" should "build a fault-free network carrying the acceleration window's lane adjacency" in {
    val (network, index) = NetworkFixtures.rampMerge(Meters(300), Meters(150))

    NetworkValidation.faults(network) shouldBe Nil

    network.sections.keySet shouldBe Set(
      SectionId("mainline-approach"),
      SectionId("mainline-accel"),
      SectionId("ramp-approach"),
      SectionId("ramp-accel")
    )

    index.successors(SectionId("mainline-approach")) shouldBe List(SectionId("mainline-accel"))
    index.successors(SectionId("mainline-accel")) shouldBe Nil
    index.successors(SectionId("ramp-accel")) shouldBe Nil

    val mainlineToRamp = network
      .neighboursOf(SectionId("mainline-accel"))
      .find(_.neighbour == SectionId("ramp-accel"))
      .getOrElse(fail("expected a LaneNeighbour from mainline-accel to ramp-accel"))
    val rampToMainline = network
      .neighboursOf(SectionId("ramp-accel"))
      .find(_.neighbour == SectionId("mainline-accel"))
      .getOrElse(fail("expected a LaneNeighbour from ramp-accel to mainline-accel"))

    mainlineToRamp.side shouldBe Side.RightOf
    rampToMainline.side shouldBe Side.LeftOf

    // The window spans the whole of the acceleration lane, and mapping there and
    // back is the identity.
    val s = Meters(75)
    val there = mainlineToRamp.neighbourPosition(s)
    there shouldBe Some(s)
    there.flatMap(rampToMainline.neighbourPosition) shouldBe Some(s)

    mainlineToRamp.neighbourPosition(Meters(-1)) shouldBe None
    mainlineToRamp.neighbourPosition(Meters(151)) shouldBe None
  }

  "tJunction" should "build a fault-free network with stop control on the minor approach" in {
    val (network, index) = NetworkFixtures.tJunction(Meters(30))

    NetworkValidation.faults(network) shouldBe Nil

    network.sections.keySet shouldBe Set(
      SectionId("major-west"),
      SectionId("major-east"),
      SectionId("minor-exit"),
      SectionId("minor-approach")
    )

    index.successors(SectionId("major-west")) should contain theSameElementsAs List(
      SectionId("major-east"),
      SectionId("minor-exit")
    )
    index.predecessors(SectionId("major-east")) should contain theSameElementsAs List(
      SectionId("major-west"),
      SectionId("minor-approach")
    )

    val through = network.movements.find(_.id == MovementId("major-west-major-east")).get
    through.kind shouldBe MovementKind.Continuation
    through.control shouldBe Control.Uncontrolled

    val turnOff = network.movements.find(_.id == MovementId("major-west-minor-exit")).get
    turnOff.kind shouldBe MovementKind.Turn
    turnOff.control shouldBe Control.Uncontrolled

    val turnOn = network.movements.find(_.id == MovementId("minor-approach-major-east")).get
    turnOn.kind shouldBe MovementKind.Turn
    turnOn.control shouldBe Control.Stop

    // Every arm - straight or turning - is exactly armLength long.
    network.sections.values.foreach(_.length.toMeters shouldBe 30.0 +- 1e-6)
  }
}
