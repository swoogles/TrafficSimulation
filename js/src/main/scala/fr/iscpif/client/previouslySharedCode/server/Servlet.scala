package fr.iscpif.client.previouslySharedCode.server

//import org.scalatra._
//import better.files.File

import scalatags.Text.all._
import scalatags.Text.{all => tags}

class Servlet {
  val projectName = "traffic"
  val jsFolder = "./jvm/target/webapp/js/"
  val clientJsFull = s"$projectName-opt.js"
  val clientJsFast = s"$projectName-fastopt.js"
  val jsDepsFull = s"$projectName-jsdeps.min.js"
  val jsDepsFast = s"$projectName-jsdeps.js"
  val fastDev = ??? // Should be a better file via: File(jsFolder + clientJsFast).exists
  val clientJs = if (fastDev) clientJsFast else clientJsFull
  val jsDeps = if (fastDev) jsDepsFast else jsDepsFull

  val basePath = "shared"

  val mainPage =
    tags.html(
      tags.head(
        ExternalResources.localResources,
//        ExternalResources.externalResources,
        tags.meta(tags.httpEquiv := "Content-Type",
                  tags.content := "text/html; charset=UTF-8"),
        tags.link(tags.rel := "stylesheet",
                  tags.`type` := "text/css",
                  href := "css/styleWUI.css"),
        tags.script(tags.`type` := "text/javascript",
                    tags.src := "js/" + clientJs),
        tags.script(tags.`type` := "text/javascript",
                    tags.src := "js/" + jsDeps)
      ),
      tags.body(tags.onload := "Client.run();")
    )

  /*
  val file = File("/tmp/nflx")
  post("/writeScene") {
    file.write(request.body)
  }

  get("/loadScene") {
    Json.parse(file.contentAsString)
  }

   */

}
