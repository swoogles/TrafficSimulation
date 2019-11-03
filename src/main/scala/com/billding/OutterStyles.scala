package com.billding

import scalacss.DevDefaults._
import scalacss.internal.Macros.Color
import scalacss.internal.mutable.StyleSheet

import scalatags.JsDom.all.{cls, input, onclick, tpe, value}
import org.scalajs.dom.Event
import org.scalajs.dom.html.Input
import scalatags.JsDom.all._
import scalacss.ScalatagsCss._

object OutterStyles {
  object TrafficStyles extends StyleSheet.Inline {
    import dsl._

    val blue: Color = c"#0000FF"
    val green = c"#00FF00"

    val standardButton = style(
      addClassNames("bttn-simple", "bttn-lg", "lightly-padded"), // Bootstrap classes
      textAlign.center // Optional customisation
    )
  }

  import TrafficStyles.standardButton
  def buttonBill(styleClasses: String): (String, (Event) => Unit) => Input =
    (content, behavior) =>
      input(
        tpe := "button",
        cls := styleClasses,
        value := content,
        onclick := behavior
      )(standardButton).render

  val normalButton: (String, Event => Unit) => Input = buttonBill(
    "bttn-primary")
  val dangerButton: (String, Event => Unit) => Input = buttonBill("bttn-danger")

}
