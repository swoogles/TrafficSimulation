package fr.iscpif.client

import scalacss.internal.Macros.Color
import scalacss.internal.mutable.StyleSheet


import scalacss.ScalatagsCss._

package object client {
  val CssSettings = scalacss.devOrProdDefaults
}

import fr.iscpif.client._

import scalacss.DevDefaults._


object OutterStyles {

  object TrafficStyles extends StyleSheet.Inline {
    import dsl._

    val blue: Color = c"#0000FF"
    val green = c"#00FF00"

    val standardButton = style(
      addClassNames("bttn-simple", "bttn-md"), // Bootstrap classes
      textAlign.center                     // Optional customisation
    )
  }

}
