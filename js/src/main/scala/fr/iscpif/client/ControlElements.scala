package fr.iscpif.client

import scalatags.JsDom.all.{cls, div, id, input, max, min, oninput, step, tpe, value}
import org.scalajs.dom.html.Div

import scalatags.JsDom.all._
import scaladget.tools.JsRxTags._
import OutterStyles.normalButton
import OutterStyles.dangerButton
import org.scalajs.dom

case class ControlElements(buttonBehaviors: ButtonBehaviors) {

  val sceneSelections = for ( scene <- buttonBehaviors.model.preloadedScenes ) yield {
    normalButton(
    scene.name,
      (e: dom.Event) => buttonBehaviors.model.loadNamedScene(scene.name))

  }

  val buttons: Div =
    div(
      cls := "col-md-6 text-center"
    )(
      normalButton("Pause", buttonBehaviors.togglePause),
      normalButton("Reset the scene!", buttonBehaviors.initiateSceneReset),
      normalButton("Save the scene",
                   buttonBehaviors.initiateSceneSerialization),
      normalButton("Load the scene",
                   buttonBehaviors.initiateSceneDeserialization),
      dangerButton("Disrupt the flow", buttonBehaviors.toggleDisrupt),
      dangerButton("Disrupt the flow Existing",
                   buttonBehaviors.toggleDisruptExisting)
    ).render

  val sliders =
    div(
      cls := "col-md-6 text-center"
    )(
      button(buttonBehaviors.model.carTimingText),
      input(
        tpe := "range",
        min := 10,
        max := 50,
        value := 30,
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

  def createLayout(): Div = {
    val buttonPanel = div(
      id := "button-panel",
      cls := "row"
    )(buttons)

    val sliderPanel = div(
      id := "slider-panel",
      cls := "row"
    )(sliders)

    val preloadedScenesPanel = div(
      id := "slider-panel",
      cls := "row"
    )(sceneSelections)


    div(
      cls := "container"
    )(
      buttonPanel,
      sliderPanel,
      preloadedScenesPanel
    ).render
  }
}
