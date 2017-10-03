package fr.iscpif.client

import fr.iscpif.client.uimodules.Model
import org.scalajs.dom
import org.scalajs.dom.Event
import org.scalajs.dom.html.Input
import org.scalajs.dom.raw.{HTMLInputElement, MouseEvent, WheelEvent}
import rx.{Ctx, Obs, Var}
import squants.motion.KilometersPerHour
import squants.time.Seconds

case class ButtonBehaviors(val model: Model)(implicit ctx: Ctx.Owner) {
  val togglePause: (Event) => Unit = (e: dom.Event) => {
    val elementClicked =
      e.target.asInstanceOf[HTMLInputElement]

    model.paused() = !model.paused.now
    model.pauseText.trigger(elementClicked.value = model.pauseText.now)
  }


  // TODO make mousewheel behavior
  private val onMouseWheelUp: (WheelEvent) => Unit =
    (e) => println("mouse event! we should zoom in/out now!")

  private val resetToTrue: Var[Boolean] => Event => Unit =
    (theBool: Var[Boolean]) =>
      (_: Event) =>
        theBool() = true

  val toggleDisrupt =
    resetToTrue(model.disruptions.disruptLane)

  val toggleDisruptExisting =
    resetToTrue(model.disruptions.disruptLaneExisting)

  val initiateSceneReset =
    resetToTrue(model.resetScene)

  val initiateSceneSerialization =
    resetToTrue(model.serializeScene)

  val initiateSceneDeserialization =
    resetToTrue(model.deserializeScene)

  private def genericSlider: (Int => Unit) => Event => Unit =
    (theBehavior) =>
      (e: Event) => {
        val value = e.target match {
          case inputElement: Input  => inputElement.value.toInt
        }
        theBehavior(value)
      }

  val updateSlider: (Event) => Unit =
    genericSlider(
      (newTiming: Int) =>
        model.carTiming() = Seconds(newTiming) / 10
    )

  val speedSliderUpdate: (Event) => Unit =
    genericSlider(
      (newSpeed: Int) =>
        model.speed() = KilometersPerHour(newSpeed)
    )

}
