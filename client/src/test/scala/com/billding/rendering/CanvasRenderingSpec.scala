package com.billding.rendering

import org.scalatest.FlatSpec

class CanvasRenderingSpec extends  FlatSpec {
  it should "add a disruptive car" in {
    Range.Double(0.0, 5, .01)
//      .map(x=>{println(x); x})
      .foreach(x=>println(CanvasRendering.warpLongStraightLineToSmoothSquareWave(x)))

//    accelerations.head shouldBe speedingUp
//    every(accelerations.tail) shouldBe slowingDown
  }


}
