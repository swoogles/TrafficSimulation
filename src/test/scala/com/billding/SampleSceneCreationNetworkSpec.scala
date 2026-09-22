package com.billding

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import squants.motion.MetersPerSecond
import squants.space.Kilometers
import squants.time.{Milliseconds, Time}

import com.billding.network.{Conflicts, Control, MovementId, NetworkValidation, SectionId}
import com.billding.physics.Spatial
import com.billding.svgRendering.{RoadArc, RoadStrip}

/**
  * D3's gate check: the demo network the page actually offers - "network, single road" -
  * has to be a real, drivable road and not merely something that happened to compile. A
  * broken demo should fail this suite, not surface for the first time as a silent or
  * exploding scene picker on the running page.
  */
class SampleSceneCreationNetworkSpec extends AnyFlatSpec with Matchers {

  private implicit val dt: Time = Milliseconds(100)
  private val scenes = new SampleSceneCreation(Spatial((0, 0, 0, Kilometers)))

  "the single-road network scene" should "be registered under the name the card asks for" in {
    scenes.singleRoadNetwork.name shouldBe "network, single road"
  }

  it should "be fault-free by NetworkValidation's own check" in {
    NetworkValidation.faults(scenes.singleRoadNetworkScene.network) shouldBe empty
  }

  it should "lay out approach, bend and departure as one straight, one arc, one straight" in {
    val shapes = scenes.singleRoadNetworkScene.roadShapes
    shapes should have size 3
    shapes.collect { case s: RoadStrip => s } should have size 2
    shapes.collect { case a: RoadArc   => a } should have size 1
  }

  it should "start with a handful of cars spread across the whole road, not bunched at one end" in {
    val traffic = scenes.singleRoadNetworkScene.traffic
    traffic.all should have size 7
    scenes.singleRoadNetworkScene.network.sections.keySet.foreach { section =>
      traffic.on(section) should not be empty
    }
  }

  it should "advance under its own tick without losing or crashing on any car" in {
    val start = scenes.singleRoadNetworkScene
    val advanced = (1 to 50).foldLeft(start) { (scene, _) => scene.updateWithSpeedLimit(scene.speedLimit) }

    advanced.t.toSeconds shouldBe 5.0 +- 1e-9
    // Five seconds at highway speed is well short of the ~383 m road, so nobody should have
    // fallen off the far end yet - this is a test of ticking safely, not of finishing the
    // course. The scene's source may have admitted a car or two in those five seconds, so
    // the conservation check is against the seven seeds plus whatever it let in, not a bare
    // seven - a hard-coded 7 here would make this test fail the moment E1's source below is
    // doing its job rather than when a car is actually lost.
    val admitted = advanced.sources.map(_.admitted).sum
    advanced.traffic.all.size + advanced.completed shouldBe 7 + admitted

    // Every car that is still on the road must have landed somewhere `renderables` can place -
    // the same "poses on the road" guarantee `NetworkSceneSpec` checks for a fixture network.
    advanced.renderables.size shouldBe advanced.traffic.all.size
  }

  it should "keep a source arriving on the approach section, so the road doesn't sit empty once the seed cars leave" in {
    val start = scenes.singleRoadNetworkScene

    start.sources.map(_.at) shouldBe List(SectionId("approach"))
    // A source reports a meaningful mean arrival spacing, the same units `StreetScene` uses -
    // this is the "stop being None" half of card E1's `sourceTiming` change.
    start.sourceTiming shouldBe defined

    // 700 ticks of the page's own 100 ms tick is 70 s of simulated time - long past the
    // ~15 s the seven seed cars take to cross the whole ~383 m road, so by then every one of
    // the original seven is gone. This is the regression test for the bug being fixed:
    // without a source, this is exactly the point where the road went, and stayed, empty.
    val advanced = (1 to 700).foldLeft(start) { (scene, _) => scene.updateWithSpeedLimit(scene.speedLimit) }

    advanced.sources.head.admitted should be > 0
    advanced.traffic.all should not be empty
  }

  "the ramp-to-T-junction gate scene" should "be registered and use one validated lane graph" in {
    val scene = scenes.rampToTJunctionScene

    scenes.rampToTJunction.name shouldBe "network, ramp to T-junction"
    NetworkValidation.faults(scene.network) shouldBe empty
    scene.network.sections.keySet should contain allOf (
      SectionId("junction-eastbound-approach"),
      SectionId("junction-eastbound-departure"),
      SectionId("junction-ramp-approach"),
      SectionId("junction-ramp-left-turn"),
      SectionId("junction-westbound-departure")
    )
  }

  it should "make the ramp yield at a real crossing while leaving the main road uncontrolled" in {
    val network = scenes.rampToTJunctionScene.network
    val through = network.movements.find(_.id == MovementId("junction-eastbound-through")).get
    val rampTurn = network.movements.find(_.id == MovementId("junction-ramp-yield-left")).get

    through.control shouldBe Control.Uncontrolled
    rampTurn.control shouldBe Control.Yield
    Conflicts
      .conflicts(network, Set(through.id, rampTurn.id))
      .map(conflict => Set(conflict.a, conflict.b)) should contain(Set(through.id, rampTurn.id))
  }

  it should "let a ramp vehicle wait, turn, and leave while conservation continues to balance" in {
    val start = scenes.rampToTJunctionScene
    val rampApproach = SectionId("junction-ramp-approach")
    val rampTurn = SectionId("junction-ramp-left-turn")
    val westboundDeparture = SectionId("junction-westbound-departure")
    val watched = start.traffic.on(rampApproach).maxBy(_.s.toMeters)
    val watchedUuid = watched.piloted.uuid
    val initialPopulation = start.traffic.all.size

    var scene = start
    var minimumApproachSpeed = watched.speed
    var reachedTurn = false
    var reachedDeparture = false

    for (_ <- 1 to 1600) {
      scene = scene.updateWithSpeedLimit(scene.speedLimit)
      scene.traffic.vehicleWith(watchedUuid).foreach { vehicle =>
        if (vehicle.section == rampApproach && vehicle.speed < minimumApproachSpeed)
          minimumApproachSpeed = vehicle.speed
        if (vehicle.section == rampTurn) reachedTurn = true
        if (vehicle.section == westboundDeparture) reachedDeparture = true
      }
    }

    withClue("the opening eastbound platoon never made the ramp car wait - ") {
      minimumApproachSpeed should be < MetersPerSecond(2)
    }
    withClue("the ramp car never entered its turning arc - ") {
      reachedTurn shouldBe true
    }
    withClue("the ramp car never completed the turn onto the neighbourhood road - ") {
      reachedDeparture shouldBe true
    }

    val admitted = scene.sources.map(_.admitted).sum
    scene.traffic.all.size + scene.completed shouldBe initialPopulation + admitted
  }
}
