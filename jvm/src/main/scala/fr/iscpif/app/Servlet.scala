package fr.iscpif.app

import java.io.{FileInputStream, FileOutputStream, ObjectInputStream, ObjectOutputStream}

import org.scalatra._

import scala.concurrent.ExecutionContext.Implicits.global
import upickle.default
import autowire._
import com.billding.traffic.SceneImpl
import play.api.libs.json.{JsObject, JsResult, Json}
import shared._
import upickle._

import scala.concurrent.duration._
import scala.concurrent.Await
import scalatags.Text
import scalatags.Text.all._
import scalatags.Text.{all => tags}

object AutowireServer extends autowire.Server[String, upickle.default.Reader, upickle.default.Writer] {
  def read[Result: upickle.default.Reader](p: String) = upickle.default.read[Result](p)
  def write[Result: upickle.default.Writer](r: Result) = upickle.default.write(r)
}

object ApiImpl extends shared.Api {
}

case class LocalAndCdnResources(
                               localRef: String,
                               cdnRef: String
                               )

object ExternalResources {

  val bootstrapMin = LocalAndCdnResources(
    "css/bootstrap.min.css",
    "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/css/bootstrap.min.css"
  )

  val bttn = LocalAndCdnResources(
    "css/bttn.min.css",
    "https://cdnjs.com/libraries/bttn.css" // TODO This isn't the file I expect...
  )

  val allResources = List(
    bootstrapMin,
    bttn
  )

  val mkStyleSheet =
    (ref: String) => tags.link(tags.rel := "stylesheet", tags.`type` := "text/css", href := ref)

  val localResources =
    allResources.map(_.localRef)
      .map(mkStyleSheet)

  val externalResources =
    allResources.map(_.cdnRef)
      .map(mkStyleSheet)

}

class Servlet extends ScalatraServlet {
  import java.nio.file.{Paths, Files}

  val currentDirectory = new java.io.File(".").getCanonicalPath

  val jsFolder = "./jvm/target/webapp/js/"
  val clientJsFull = "foo-opt.js"
  val clientJsFast = "foo-fastopt.js"
  val jsDepsFull = "foo-jsdeps.min.js"
  val jsDepsFast = "foo-jsdeps.js"
  val fastDev = Files.exists(Paths.get(jsFolder + clientJsFast))
  val clientJs = if (fastDev) clientJsFast else clientJsFull
  val jsDeps = if (fastDev) jsDepsFast else jsDepsFull

  println("opt.js exists: " + Files.exists(Paths.get(jsFolder + "foo-opt.js")))
  println("fastopt.js exists: " + Files.exists(Paths.get(jsFolder + "foo-fastopt.js")))

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

  post("/writeScene") {

    val oos = new ObjectOutputStream(new FileOutputStream("/tmp/nflx"))
    oos.writeObject(request.body)
    oos.close()
  }

  get("/loadScene") {
    val ois = new ObjectInputStream(new FileInputStream("/tmp/nflx"))
    val newString = ois.readObject.asInstanceOf[String]
    Json.parse(newString)
  }
  post(s"/$basePath/*") {
    Await.result(AutowireServer.route[shared.Api](ApiImpl)(
      autowire.Core.Request(Seq(basePath) ++ multiParams("splat").head.split("/"),
        upickle.default.read[Map[String, String]](request.body))
    ), Duration.Inf)
  }

}
