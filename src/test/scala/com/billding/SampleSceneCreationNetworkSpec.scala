package com.billding

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import squants.space.Kilometers
import squants.time.{Milliseconds, Time}

import com.billding.network.NetworkValidation
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
    // fallen off the far end yet - this is a test of ticking safely, not of finishing the course.
    advanced.traffic.all.size + advanced.completed shouldBe 7

    // Every car that is still on the road must have landed somewhere `renderables` can place -
    // the same "poses on the road" guarantee `NetworkSceneSpec` checks for a fixture network.
    advanced.renderables.size shouldBe advanced.traffic.all.size
  }
}
