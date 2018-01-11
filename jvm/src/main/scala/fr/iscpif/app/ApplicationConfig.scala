package fr.iscpif.app

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory.parseString
import pureconfig.loadConfig

case class HowConfiguration(velocityUnit: String)

class ApplicationConfig() {
  val conf: Config = parseString("""
    {
      velocity-unit: "km/h"
    }
  """)

  val config: HowConfiguration = loadConfig[HowConfiguration](conf).right.get
}
object UsinIt {
  val config: HowConfiguration = new ApplicationConfig().config
}
