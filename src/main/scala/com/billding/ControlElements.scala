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
import com.raquo.laminar.api.L.{
  div => ldiv,
  button => lbutton,
  span,
  Observer,
  cls => laminarCls,
  Signal,
  child
}
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.scalajs.dom.html.{Div => HtmlDiv, Button => HtmlButton, Input => HtmlInput}
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
      dangerButton("Make 1 car brake", buttonBehaviors.toggleDisruptExisting)
    ).render

  // Create Laminar components for reactive text display
  val timingButton: ReactiveHtmlElement[HtmlButton] = lbutton(
    laminarCls := "col-md-6 text-center",
    child.text <-- buttonBehaviors.model.carTimingText
  )

  val speedButton: ReactiveHtmlElement[HtmlButton] = lbutton(
    laminarCls := "col-md-6 text-center",
    child.text <-- buttonBehaviors.model.carSpeedText
  )

  // Use scalatags for sliders since they're not reactive
  val sliders: Div =
    div(
      scalatagsCls := "col-md-6 text-center"
    )(
      timingButton.ref, // Convert Laminar element to DOM node
      input(
        tpe := "range",
        min := 10,
        max := 50,
        value := 30,
        oninput := buttonBehaviors.updateSlider
      ),
      speedButton.ref,
      input(
        id := "speedSlider",
        tpe := "range",
        min := 20,
        max := 80,
        value := 65,
        step := 5,
        oninput := buttonBehaviors.speedSliderUpdate
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
