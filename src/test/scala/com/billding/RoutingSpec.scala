package com.billding

import com.billding.network._
import com.billding.physics.StraightPath
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import squants.motion.{Distance, KilometersPerHour}
import squants.space.Meters
import squants.QuantityVector

class RoutingSpec extends AnyFlatSpec with Matchers {

  private val laneWidth = Meters(3.5)
  private val speedLimit = KilometersPerHour(50)

  private def straightSection(id: String, from: Double, to: Double): LaneSection =
    LaneSection(
      SectionId(id),
      StraightPath(
        QuantityVector[Distance](Meters(from), Meters(0), Meters(0)),
        QuantityVector[Distance](Meters(to), Meters(0), Meters(0))
      ),
      laneWidth,
      speedLimit,
      layer = 0
    )

  "A Y-split" should "route to either branch" in {
    val (_, index) = NetworkFixtures.ySplit(Meters(100), Meters(50))

    Routing.planRoute(index, SectionId("trunk"), SectionId("branch-left")) shouldBe
      Some(List(MovementId("trunk-branch-left")))
    Routing.planRoute(index, SectionId("trunk"), SectionId("branch-right")) shouldBe
      Some(List(MovementId("trunk-branch-right")))

    Routing.isReachable(index, SectionId("trunk"), SectionId("branch-left")) shouldBe true
    Routing.isReachable(index, SectionId("trunk"), SectionId("branch-right")) shouldBe true
  }

  "A route to a section with no path from the origin" should "be None" in {
    val (_, index) = NetworkFixtures.straightChain(3, Meters(100))

    // Movements only run mainline-0 -> mainline-1 -> mainline-2, so travelling
    // backwards has no route at all.
    Routing.planRoute(index, SectionId("mainline-2"), SectionId("mainline-0")) shouldBe None
    Routing.isReachable(index, SectionId("mainline-2"), SectionId("mainline-0")) shouldBe false
  }

  "A route to an unknown section" should "also be None, not throw" in {
    val (_, index) = NetworkFixtures.straightChain(2, Meters(100))

    Routing.planRoute(index, SectionId("mainline-0"), SectionId("does-not-exist")) shouldBe None
    Routing.planRoute(index, SectionId("does-not-exist"), SectionId("mainline-0")) shouldBe None
  }

  "A route from a section to itself" should "be an empty, already-there route" in {
    val (_, index) = NetworkFixtures.straightChain(2, Meters(100))

    Routing.planRoute(index, SectionId("mainline-0"), SectionId("mainline-0")) shouldBe Some(Nil)
    Routing.isReachable(index, SectionId("mainline-0"), SectionId("mainline-0")) shouldBe true
  }

  "Two routes of different free-flow time to the same destination" should "resolve to the shorter one" in {
    // start -> fast -> dest is a short hop; start -> slow -> dest is a long
    // one at the same speed limit, so the fast branch must win on travel time,
    // not on any other tie-break.
    val start = straightSection("start", 0, 100)
    val fast = straightSection("fast", 100, 150) // 50 m
    val slow = straightSection("slow", 1000, 1300) // 300 m
    val dest = straightSection("dest", 2000, 2100)

    val movements = List(
      Movement.continuation(MovementId("start-slow"), SectionId("start"), SectionId("slow")),
      Movement.continuation(MovementId("start-fast"), SectionId("start"), SectionId("fast")),
      Movement.continuation(MovementId("slow-dest"), SectionId("slow"), SectionId("dest")),
      Movement.continuation(MovementId("fast-dest"), SectionId("fast"), SectionId("dest"))
    )

    val network = RoadNetwork(
      sections = List(start, fast, slow, dest).map(s => s.id -> s).toMap,
      movements = movements
    )
    val index = NetworkIndex(network)

    Routing.planRoute(index, SectionId("start"), SectionId("dest")) shouldBe
      Some(List(MovementId("start-fast"), MovementId("fast-dest")))
  }

  "A route through a chain" should "list every movement in order" in {
    val (_, index) = NetworkFixtures.straightChain(4, Meters(100))

    Routing.planRoute(index, SectionId("mainline-0"), SectionId("mainline-3")) shouldBe
      Some(
        List(
          MovementId("mainline-0-1"),
          MovementId("mainline-1-2"),
          MovementId("mainline-2-3")
        )
      )

    Routing.isReachable(index, SectionId("mainline-0"), SectionId("mainline-3")) shouldBe true
  }
}
