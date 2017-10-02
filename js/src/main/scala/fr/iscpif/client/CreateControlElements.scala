package fr.iscpif.client

import scalatags.JsDom.all.{cls, div, id, input, max, min, onclick, oninput, step, tpe, value}
import org.scalajs.dom.Event
import org.scalajs.dom.html.{Div, Input}
import org.scalajs.dom.raw.{HTMLElement}
import scalatags.JsDom.all._

import scaladget.tools.JsRxTags._

case class CreateControlElements(buttonBehaviors: ButtonBehaviors) {
  def createButtons(): Div = {
    val buttonBaseClasses = "bttn-simple bttn-md lightly-padded"

    val buttonStyleClasses = buttonBaseClasses + " bttn-primary"
    val dangerButtonClasses = buttonBaseClasses + " bttn-danger"

    def button(styleClasses: String): (String, (Event) => Unit ) => Input =
      (content, behavior) =>
        input(
          tpe := "button",
          cls := styleClasses,
          value := content,
          onclick := behavior
        ).render

    val normalButton = button(buttonStyleClasses)
    val dangerButton = button(dangerButtonClasses)

    div(
      cls := "col-md-6 text-center"
    )(
      normalButton("Pause", buttonBehaviors.togglePause),
      normalButton("Reset the scene!", buttonBehaviors.initiateSceneReset),
      normalButton("Serialize the scene", buttonBehaviors.initiateSceneSerialization),
      normalButton("Deserialize the scene", buttonBehaviors.initiateSceneDeserialization),
      dangerButton("Disrupt the flow", buttonBehaviors.toggleDisrupt),
      dangerButton("Disrupt the flow Existing", buttonBehaviors.toggleDisruptExisting)
    ).render
  }

  def createSliders(element: HTMLElement) = {
    val columnDiv = div(
      cls := "col-md-6 text-center"
    )(
      button(buttonBehaviors.model.carTimingText).render,

      input(
        tpe := "range",
        min := 1,
        max := 5,
        value := 3,
        oninput := buttonBehaviors.updateSlider
      ).render,

      button(
      )(buttonBehaviors.model.carSpeedText).render,

      input(
        id := "speedSlider",
        tpe := "range",
        min := 20,
        max := 80,
        value := 65,
        step := 5,
        oninput := buttonBehaviors.speedSliderUpdate
      ).render
    ).render
    element.appendChild(columnDiv)
  }

  def createLayout(element: HTMLElement) = {
    val allControls = div(
      cls := "container"
    ).render

    val buttonPanel = div(
      id := "button-panel",
      cls := "row"
    ).render
    buttonPanel.appendChild(createButtons())
    allControls.appendChild(buttonPanel)
    val sliderPanel = div(
      id := "slider-panel",
      cls := "row"
    ).render
    createSliders(sliderPanel)
    allControls.appendChild(sliderPanel)
    element.appendChild(allControls)
  }
}
