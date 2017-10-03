package fr.iscpif.client

import fr.iscpif.client.uimodules.Model
import org.scalajs.dom
import org.scalajs.dom.Event
import org.scalajs.dom.html.Input
import org.scalajs.dom.raw.HTMLInputElement
import squants.motion.KilometersPerHour
import squants.time.Seconds

case class ButtonBehaviors(val model: Model) {
  val togglePause = (e: dom.Event) => {
    val elementClicked = e.target.asInstanceOf[HTMLInputElement]
    elementClicked.value = if (model.paused.now == true) "Pause" else "Unpause"
    model.paused() = !model.paused.now
  }

  val toggleDisrupt =
    (_: Event) => model.disruptions.disruptLane() = true

  val toggleDisruptExisting =
    (_: Event) => model.disruptions.disruptLaneExisting() = true

  val initiateSceneReset =
    (_: Event) => model.resetScene() = true

  val initiateSceneSerialization =
    (_: Event) => model.serializeScene() = true

  val initiateSceneDeserialization =
    (_: Event) => model.deserializeScene() = true

  def updateTimingSlider(newTiming: Int): Unit = {
    model.carTiming() = Seconds(newTiming) / 10
  }

  def updateSpeedSlider(newTiming: Int): Unit = {
    model.speed() = KilometersPerHour(newTiming)
  }

  val updateSlider: (Event) => Unit = (e: Event) => {
    val value = e.target match {
      case inputElement: Input  => inputElement.value.toInt
      case _ => 3 // TODO de-magick this
    }
    updateTimingSlider(value)
  }

  val speedSliderUpdate: (Event) => Unit = (e: Event) => {
    val value = e.target match {
      case inputElement: Input  => inputElement.value.toInt
      case _ => 65 // TODO de-magick this
    }
    updateSpeedSlider(value)
  }
}
