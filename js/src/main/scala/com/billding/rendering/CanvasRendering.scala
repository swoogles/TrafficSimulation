package com.billding.rendering

import com.billding.physics.Spatial
import org.scalajs.dom.ImageData

trait SpriteMap

trait Renderable {
  val sprites: ImageData
  val spatial: Spatial
}

object CanvasRendering {
  /*
    TODO This has NO concept of svg scaling, so the cars could very well be GINORMOUS.

    This is a great learning experience.
    When I was doing everything using only traits, progress soared in the beginning,
    but slowed considerably as time went on. I thought this meant it was time to switch
    over to implementations. Most excitedly, I could start rendering things in the
    browser. My first problem was a true physics problem. I was failing to calculate
    acceleration along the direction of motion. This might have been a mistake I would
    make in any case, but it feels like I caused this grief by not starting with pure
    vector equations/functions throughout. Maybe I would have avoided this problem
    completely if I had stuck to the APIs that the Squants types give me. They should
    have good reasons for their choices, based on how great this library is in all other
    aspects so far.

    I might also be completely off-base, and the problem doesn't lie in the rendering
    at all. It must though.
    Oh wow.
                                    Meters = pixels
    That's the problem. Of course the scaling is terrible.
      -Come up with appropriate pixel <=> Distance conversion.
        -6.inches.per.pixel
          -pixel == int. It's the End of the Line.
 */

}
