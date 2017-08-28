package fr.iscpif.app

import org.scalatra._
import scala.concurrent.ExecutionContext.Implicits.global
import upickle.default
import autowire._
import shared._
import upickle._
import scala.concurrent.duration._
import scala.concurrent.Await
import scalatags.Text.all._
import scalatags.Text.{all => tags}

object AutowireServer extends autowire.Server[String, upickle.default.Reader, upickle.default.Writer] {
  def read[Result: upickle.default.Reader](p: String) = upickle.default.read[Result](p)
  def write[Result: upickle.default.Writer](r: Result) = upickle.default.write(r)
}

object ApiImpl extends shared.Api {
}

class Servlet extends ScalatraServlet {

  val basePath = "shared"

  get("/") {
    contentType = "text/html"

    tags.html(

      tags.head(
        tags.meta(tags.httpEquiv := "Content-Type", tags.content := "text/html; charset=UTF-8"),
        tags.link(tags.rel := "stylesheet", tags.`type` := "text/css", href := "css/styleWUI.css"),
//        tags.link(tags.rel := "stylesheet", tags.`type` := "text/css", href := "https://cdnjs.com/libraries/bttn.css"),
        tags.link(tags.rel := "stylesheet", tags.`type` := "text/css", href := "css/bttn.min.css"),
        tags.link(tags.rel := "stylesheet", tags.`type` := "text/css", href := "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/css/bootstrap.min.css"),
//        tags.link(tags.rel := "stylesheet", tags.`type` := "text/css")(OutterStyles.TrafficStyles.render[String]),
        tags.script(tags.`type` := "text/javascript", tags.src := "js/client-opt.js"),
        tags.script(tags.`type` := "text/javascript", tags.src := "js/client-jsdeps.min.js")
//        tags.script(tags.`type` := "text/javascript", tags.src := "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/js/bootstrap.min.js")

        /*
        <!-- Latest compiled and minified CSS -->
<link rel="stylesheet"
href="https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/css/bootstrap.min.css"
integrity="sha384-BVYiiSIFeK1dGmJRAkycuHAHRg32OmUcww7on3RYdg4Va+PmSTsz/K68vbdEjh4u"
crossorigin="anonymous">

<!-- Optional theme -->
<link rel="stylesheet"
href="https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/css/bootstrap-theme.min.css"
integrity="sha384-rHyoN1iRsVXV4nD0JutlnGaslCJuC7uwjduW9SVrLvRYooPp2bWYgmgJQIXwl/Sp"
crossorigin="anonymous">

<!-- Latest compiled and minified JavaScript -->
<script src="https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/js/bootstrap.min.js"
integrity="sha384-Tc5IQib027qvyjSMfHjOMaLkfuWVxZxUPnCJA7l2mCWNIpG9mGCD8wGNIcPD7Txa"
crossorigin="anonymous"></script>
         */
      ),
      tags.body(tags.onload := "Client().run();")
    )
  }

  post(s"/$basePath/*") {
    Await.result(AutowireServer.route[shared.Api](ApiImpl)(
      autowire.Core.Request(Seq(basePath) ++ multiParams("splat").head.split("/"),
        upickle.default.read[Map[String, String]](request.body))
    ), Duration.Inf)
  }

}
