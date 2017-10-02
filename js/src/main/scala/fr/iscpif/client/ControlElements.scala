package fr.iscpif.client

import scalatags.JsDom.all.{cls, div, id, input, max, min, onclick, oninput, step, tpe, value}
import org.scalajs.dom.Event
import org.scalajs.dom.html.{Div, Input}
import scalatags.JsDom.all._

import scaladget.tools.JsRxTags._

case class ControlElements(buttonBehaviors: ButtonBehaviors) {
  val buttonBaseClasses = "bttn-simple bttn-md lightly-padded"

  val buttonStyleClasses = buttonBaseClasses + " bttn-primary"
  val dangerButtonClasses = buttonBaseClasses + " bttn-danger"

  def buttonBill(styleClasses: String): (String, (Event) => Unit ) => Input =
    (content, behavior) =>
      input(
        tpe := "button",
        cls := styleClasses,
        value := content,
        onclick := behavior
      ).render

  val normalButton = buttonBill(buttonStyleClasses)
  val dangerButton = buttonBill(dangerButtonClasses)

  val buttons: Div = {

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

  val sliders = {
    div(
      cls := "col-md-6 text-center"
    )(
      button(buttonBehaviors.model.carTimingText),

      input(
        tpe := "range",
        min := 1,
        max := 5,
        value := 3,
        oninput := buttonBehaviors.updateSlider
      ),

      button(
      )(buttonBehaviors.model.carSpeedText),

      input(
        id := "speedSlider",
        tpe := "range",
        min := 20,
        max := 80,
        value := 65,
        step := 5,
        oninput := buttonBehaviors.speedSliderUpdate
      )
    )
  }

  def createLayout() = {
    val buttonPanel = div(
      id := "button-panel",
      cls := "row"
    )(buttons)


    val sliderPanel = div(
      id := "slider-panel",
      cls := "row"
    )(sliders)


    div(
      cls := "container"
    )(
      buttonPanel,
      sliderPanel
    ).render
  }
}
