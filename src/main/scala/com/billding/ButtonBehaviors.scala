package com.billding

import com.billding.uimodules.Model
import org.scalajs.dom
import org.scalajs.dom.Event
import org.scalajs.dom.html.Input
import org.scalajs.dom.raw.{HTMLInputElement, WheelEvent}
import com.raquo.laminar.api.L.Var
import squants.motion.KilometersPerHour
import squants.time.Seconds

case class ButtonBehaviors(model: Model) {

  def togglePauseMethod(e: dom.Event): Unit =
    e.target match {
      case elementClicked: HTMLInputElement => {
        println("paused status: " + model.paused.now()) // why is this fuggin true??
        model.togglePause()
        // Update button text via signal - we can't access .now() on Signal directly
        // The button will update reactively via Laminar bindings
      }
      case unrecognizedClickedElement => throw new RuntimeException("Must be an input element to toggle pausing. e.target: " + unrecognizedClickedElement)
    }


  // TODO make mousewheel behavior
  private val onMouseWheelUp: (WheelEvent) => Unit =
    (e) => println("mouse event! we should zoom in/out now!")

  private val resetToTrue: Var[Boolean] => Event => Unit =
    (theBool: Var[Boolean]) => (_: Event) => theBool.set(true)

  val toggleDisrupt =
    resetToTrue(model.disruptions.disruptLane)

  val toggleDisruptExisting =
    resetToTrue(model.disruptions.disruptLaneExisting)

  val initiateSceneReset =
    resetToTrue(model.resetScene)

  val initiateSceneSerialization =
    resetToTrue(model.serializeScene)

  val initiateSceneDeserialization: Event => Unit = {
    model.pause()
    resetToTrue(model.deserializeScene)
  }

  private def genericSlider: (Int => Unit) => Event => Unit =
    (theBehavior: Int => Unit) =>
      (e: Event) => {
        val value = e.target match {
          case inputElement: Input => inputElement.value.toInt
        }
        theBehavior(value)
      }

  val updateSlider: (Event) => Unit =
    genericSlider(
      (newTiming: Int) => model.carTiming.set(Seconds(newTiming) / 10)
    )

  val speedSliderUpdate: (Event) => Unit =
    genericSlider(
      (newSpeed: Int) => model.speed.set(KilometersPerHour(newSpeed))
    )

}
