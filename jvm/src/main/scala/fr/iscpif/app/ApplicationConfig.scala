import com.typesafe.config.ConfigFactory.parseString
import pureconfig.loadConfig
import pureconfig.module.squants._

case class HowConfiguration(velocityUnit: String)

class ApplicationConfig() {
  val conf = parseString("""
    {
      velocity-unit: "km/h"
    }
  """)

  // conf: com.typesafe.config.Config = Config(SimpleConfigObject({"far":"42.195 km","hot":"56.7° C"}))

  val config = loadConfig[HowConfiguration](conf).right.get
  println("config: " + config)
  println("config velocity unit: " + config.velocityUnit)

//  println("test velocity: " + Velocity("0 " + config.velocityUnit))

}
object UsinIt {
  val config = new ApplicationConfig().config
}
