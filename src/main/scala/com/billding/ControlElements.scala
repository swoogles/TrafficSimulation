package com.billding

import scalatags.JsDom.all.{
  cls => scalatagsCls,
  div,
  id,
  input,
  max,
  min,
  oninput,
  step,
  tpe,
  value
}
import org.scalajs.dom.html.Div
import org.scalajs.dom
import com.raquo.laminar.api.L.Signal
import com.raquo.airstream.ownership.Owner
import org.scalajs.dom.html.{Input => HtmlInput}
import OutterStyles.normalButton
import OutterStyles.dangerButton
import scalatags.JsDom.all._
import scalatags.JsDom.TypedTag

case class ControlElements(buttonBehaviors: ButtonBehaviors) {

  val sceneSelections: List[HtmlInput] =
    for (scene <- buttonBehaviors.model.preloadedScenes)
      yield {
        normalButton(scene.name, (e: dom.Event) => buttonBehaviors.model.loadNamedScene(scene.name))
      }

  val buttons: Div =
    div(
      scalatagsCls := "col-md-6 text-center"
    )(
      normalButton("Pause for Andrew", buttonBehaviors.togglePauseMethod),
      normalButton("Reset the scene!", buttonBehaviors.initiateSceneReset),
      /*
      normalButton("Save the scene",
                   buttonBehaviors.initiateSceneSerialization),
      normalButton("Load the scene",
                   buttonBehaviors.initiateSceneDeserialization),

       */
//      dangerButton("Disrupt the flow", buttonBehaviors.toggleDisrupt),
      dangerButton("Make 1 car brake", buttonBehaviors.toggleDisruptExisting),
      dangerButton("Force a lane change", buttonBehaviors.forceLaneChange)
    ).render

  /*
  Laminar only starts a `child.text <-- signal` binding when it mounts the element itself.
  These labels are handed to scalatags as raw DOM nodes, which Laminar never sees, so the
  binding stayed dormant and every slider sat under a blank pill. Subscribing directly with
  an owner of our own keeps the labels reactive without pulling the whole panel into Laminar.
   */
  implicit private val owner: Owner = new Owner {}

  private def reactiveLabel(text: Signal[String]): Div = {
    val label = div(scalatagsCls := "col-md-12 text-center")().render
    text.foreach { value =>
      label.textContent = value
    }
    label
  }

  val timingLabel: Div = reactiveLabel(buttonBehaviors.model.carTimingText)

  val speedLabel: Div = reactiveLabel(buttonBehaviors.model.carSpeedText)

  val eagernessLabel: Div = reactiveLabel(buttonBehaviors.model.laneChangeEagernessText)

  val politenessLabel: Div = reactiveLabel(buttonBehaviors.model.politenessText)

  // Use scalatags for sliders since they're not reactive
  val sliders: Div =
    div(
      scalatagsCls := "col-md-6 text-center"
    )(
      timingLabel,
      input(
        tpe := "range",
        min := 10,
        max := 50,
        value := 30,
        oninput := buttonBehaviors.updateSlider
      ),
      speedLabel,
      input(
        id := "speedSlider",
        tpe := "range",
        min := 20,
        max := 80,
        value := 65,
        step := 5,
        oninput := buttonBehaviors.speedSliderUpdate
      ),
      // How readily a driver takes a gap at all. Turn it up and the lanes churn.
      eagernessLabel,
      input(
        id := "eagernessSlider",
        tpe := "range",
        min := 0,
        max := 100,
        value := 50,
        step := 5,
        oninput := buttonBehaviors.eagernessSliderUpdate
      ),
      // How much a driver cares what its merge costs the car behind. Turn it down for waves.
      politenessLabel,
      input(
        id := "politenessSlider",
        tpe := "range",
        min := 0,
        max := 100,
        value := 20,
        step := 5,
        oninput := buttonBehaviors.politenessSliderUpdate
      )
    ).render

  def createLayout(): Div = {
    val buttonPanel = div(
      id := "button-panel",
      scalatagsCls := "row"
    )(buttons)

    val sliderPanel = div(
      id := "slider-panel",
      scalatagsCls := "row"
    )(sliders)

    val preloadedScenesPanel = div(
      id := "sample-scenes-panel",
      scalatagsCls := "row"
    )(sceneSelections)

    div(
      scalatagsCls := "container"
    )(
      buttonPanel,
      sliderPanel,
      preloadedScenesPanel
    ).render
  }
}
