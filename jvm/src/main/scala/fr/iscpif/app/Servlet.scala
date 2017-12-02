package fr.iscpif.app

import org.scalatra._

import better.files.File
import play.api.libs.json.Json

import scalatags.Text.all._
import scalatags.Text.{all => tags}

class Servlet extends ScalatraServlet {
  val jsFolder = "./jvm/target/webapp/js/"
  val clientJsFull = "foo-opt.js"
  val clientJsFast = "foo-fastopt.js"
  val jsDepsFull = "foo-jsdeps.min.js"
  val jsDepsFast = "foo-jsdeps.js"
  val fastDev = File(jsFolder + clientJsFast).exists
  val clientJs = if (fastDev) clientJsFast else clientJsFull
  val jsDeps = if (fastDev) jsDepsFast else jsDepsFull

  val basePath = "shared"

  get("/") {
    contentType = "text/html"
    tags.html(
      tags.head(
        ExternalResources.localResources,
//        ExternalResources.externalResources,
        tags.meta(tags.httpEquiv := "Content-Type", tags.content := "text/html; charset=UTF-8"),
        tags.link(tags.rel := "stylesheet", tags.`type` := "text/css", href := "css/styleWUI.css"),

        tags.script(tags.`type` := "text/javascript", tags.src := "js/" + clientJs),
        tags.script(tags.`type` := "text/javascript", tags.src := "js/" + jsDeps)
      ),
      tags.body(tags.onload := "Client.run();")
    )
  }

  val file = File("/tmp/nflx")
  post("/writeScene") {
    file.write(request.body)
  }

  get("/loadScene") {
    Json.parse(file.contentAsString)
  }

}
