package com.billding

import com.raquo.laminar.api.L._

/**
  * The controls under the road.
  *
  * Everything here is arranged around one rule: the road stays on screen. A panel of ten
  * scene buttons and four labelled sliders is most of a phone, and a control you cannot see
  * the effect of is a control you have to operate twice - once to change it and once to find
  * out what it did. So the settings are readings until you touch one, and only one of them
  * opens at a time, in a strip below a row that never changes height.
  *
  * The whole thing is a Laminar tree with no imperative updating in it: labels follow the
  * model's signals, the open panel is a Var that the markup reads, and the events go to the
  * observers in [[ButtonBehaviors]]. It is mounted with `render`, which is what makes those
  * bindings live - handing raw nodes to something else is what forced the old panel to
  * subscribe by hand and left its labels blank.
  */
case class ControlElements(buttonBehaviors: ButtonBehaviors) {

  private val model = buttonBehaviors.model

  /** A number you can change while the traffic runs, and what it currently reads. */
  private case class Dial(
    name: String,
    reading: Signal[String],
    lowest: Int,
    highest: Int,
    step: Int,
    position: Signal[Int],
    set: Observer[Int]
  )

  private val speed = Dial(
    "Speed",
    model.carSpeedText,
    20,
    80,
    5,
    model.speed.signal.map(_.toKilometersPerHour.round.toInt),
    buttonBehaviors.setSpeed
  )

  private val eagerness = Dial(
    "Eagerness",
    model.laneChangeEagernessText,
    0,
    100,
    5,
    model.laneChangeEagerness.signal.map(e => (e * 100).round.toInt),
    buttonBehaviors.setEagerness
  )

  private val politeness = Dial(
    "Politeness",
    model.politenessText,
    0,
    100,
    5,
    model.politeness.signal.map(p => (p * 100).round.toInt),
    buttonBehaviors.setPoliteness
  )

  private val carTiming = Dial(
    "New cars",
    model.carTimingText,
    10,
    50,
    1,
    model.carTiming.signal.map(t => (t.toSeconds * 10).round.toInt),
    buttonBehaviors.setCarTiming
  )

  /** What is currently expanded, if anything. Only ever one thing. */
  sealed private trait Panel
  private case class Adjusting(dial: Dial) extends Panel
  private case object ChoosingScene extends Panel

  private val openPanel: Var[Option[Panel]] = Var(None)

  private def toggle(panel: Panel): Observer[Any] =
    Observer(_ => openPanel.update(current => if (current.contains(panel)) None else Some(panel)))

  /**
    * A ring has nowhere for new cars to arrive from, so it has no arrival timing to set.
    *
    * Taken off the scene, which changes sixty times a second, so it is reduced to the only
    * question being asked of it before anything is allowed to depend on it - otherwise the
    * controls would be rebuilt on every tick of the simulation.
    */
  private val trafficArrives: Signal[Boolean] =
    model.sceneVar.signal.map(_.sourceTiming.isDefined).distinct

  val layout: HtmlElement =
    div(
      cls := "controls",
      // Nothing that would let a stale panel outlive the scene it belonged to.
      trafficArrives.changes --> Observer[Any](_ => openPanel.set(None)),
      div(
        cls := "control-row",
        action(child.text <-- model.pauseText, buttonBehaviors.togglePause),
        action("Reset", buttonBehaviors.resetScene),
        action("Brake a car", buttonBehaviors.brakeOneCar, cls := "disruptive"),
        action("Force a lane change", buttonBehaviors.forceLaneChange, cls := "disruptive"),
        action("Scenes", toggle(ChoosingScene), cls.toggle("open") <-- isOpen(ChoosingScene))
      ),
      div(
        cls := "control-row",
        readout(speed),
        readout(eagerness),
        readout(politeness),
        child.maybe <-- trafficArrives.map(if (_) Some(readout(carTiming)) else None)
      ),
      child.maybe <-- openPanel.signal.map {
        case Some(Adjusting(dial)) => Some(slider(dial))
        case _                     => None
      },
      child.maybe <-- openPanel.signal.map {
        case Some(ChoosingScene) => Some(scenePicker)
        case _                   => None
      }
    )

  private def isOpen(panel: Panel): Signal[Boolean] =
    openPanel.signal.map(_.contains(panel))

  private def action(label: Modifier[HtmlElement],
                     clicked: Observer[Any],
                     extra: Modifier[HtmlElement]*): HtmlElement =
    button(cls := "action", label, extra, onClick --> clicked)

  /**
    * A setting at rest: its name, and what it says.
    *
    * This is the whole control until you touch it. Reading a value takes a glance, and a
    * glance is all a setting deserves while you are watching the traffic rather than it.
    */
  private def readout(dial: Dial): HtmlElement =
    button(
      cls := "readout",
      cls.toggle("open") <-- isOpen(Adjusting(dial)),
      onClick --> toggle(Adjusting(dial)),
      span(cls := "name", dial.name),
      span(cls := "value", child.text <-- dial.reading)
    )

  /**
    * The one open setting, in a strip of its own beneath the row.
    *
    * Below rather than over, because the point of opening it is to watch what it does to the
    * road, and the road is above. Dragging this never moves anything above it.
    */
  private def slider(dial: Dial): HtmlElement =
    div(
      cls := "dial",
      label(cls := "name", dial.name),
      input(
        tpe := "range",
        minAttr := dial.lowest.toString,
        maxAttr := dial.highest.toString,
        stepAttr := dial.step.toString,
        controlled(
          value <-- dial.position.map(_.toString),
          onInput.mapToValue.map(_.toInt) --> dial.set
        )
      ),
      span(cls := "value", child.text <-- dial.reading)
    )

  private def scenePicker: HtmlElement =
    div(
      cls := "scene-picker",
      model.preloadedScenes.map { scene =>
        button(
          cls := "scene",
          cls.toggle("current") <-- model.currentSceneName.signal.map(_.contains(scene.name)),
          scene.name,
          onClick.mapTo(scene.name) --> buttonBehaviors.loadScene,
          onClick --> Observer[Any](_ => openPanel.set(None))
        )
      }
    )
}
