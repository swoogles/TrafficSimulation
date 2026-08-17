package com.billding

import com.billding.uimodules.Model
import com.raquo.laminar.api.L.{Observer, Var}
import squants.motion.KilometersPerHour
import squants.time.Seconds

/**
  * What the controls do, as somewhere for an event to go rather than as a handler.
  *
  * Laminar wires an event stream to an Observer, so a control is `onClick --> behaviour` and
  * nothing in the markup needs to know what the behaviour is made of. Keeping them here also
  * keeps the units in one place: the page deals in whole numbers because that is what a range
  * input is, and this is where those become speeds, times and fractions.
  */
case class ButtonBehaviors(model: Model) {

  val togglePause: Observer[Any] = Observer(_ => model.togglePause())

  val resetScene: Observer[Any] = asks(model.resetScene)

  val brakeOneCar: Observer[Any] = asks(model.disruptions.disruptLaneExisting)

  val forceLaneChange: Observer[Any] = asks(model.disruptions.forceLaneChange)

  val loadScene: Observer[String] = Observer(model.loadNamedScene)

  /** Kilometres per hour, straight off the slider. */
  val setSpeed: Observer[Int] = Observer(value => model.speed.set(KilometersPerHour(value)))

  /** Tenths of a second between arriving cars, which is finer than a whole second. */
  val setCarTiming: Observer[Int] = Observer(value => model.carTiming.set(Seconds(value) / 10))

  /** Both lane-change dials run 0 to 100 on the page and 0 to 1 in the model. */
  val setEagerness: Observer[Int] =
    Observer(value => model.laneChangeEagerness.set(value / 100.0))

  val setPoliteness: Observer[Int] = Observer(value => model.politeness.set(value / 100.0))

  /**
    * Some disruptions are requests rather than actions: the flag is raised here and lowered
    * by whichever tick gets around to honouring it, because the scene it applies to only
    * exists inside the simulation loop.
    */
  private def asks(request: Var[Boolean]): Observer[Any] = Observer(_ => request.set(true))
}
