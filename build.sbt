import java.io.File
import org.scalatra.sbt.ScalatraPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._

val Organization = "fr.iscpif"
val Name = "Traffice Simulator"
val Version = "0.1.0-SNAPSHOT"
val ScalaVersion = "2.12.3"
val scalatraVersion = "2.5.0"
val jettyVersion = "9.2.19.v20160908"
val json4sVersion = "3.5.2"
val scalatagsVersion = "0.6.5"
val autowireVersion = "0.2.6"
val upickleVersion = "0.4.4"
val rxVersion = "0.3.2"
val scaladgetVersion = "0.9.4"
val scalajsDomVersion = "0.9.3"
val jqueryVersion = "2.2.1"
val circeVersion = "0.8.0"

val Resolvers = Seq(
  Resolver.sonatypeRepo("snapshots"),
  Resolver.sonatypeRepo("releases"),
  "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"
)

lazy val shared = project.in(file("./shared")).settings(
  scalaVersion := ScalaVersion
)

val jqueryPath = s"META-INF/resources/webjars/jquery/$jqueryVersion/jquery.js"

lazy val client = project.in(file("client")) settings(
  version := Version,
  scalaVersion := ScalaVersion,
  resolvers in ThisBuild ++= Resolvers,
  skip in packageJSDependencies := false,
  jsDependencies += "org.webjars" % "d3js" % "3.5.12" / "d3.min.js",
  libraryDependencies ++= Seq(
    "com.lihaoyi" %%% "autowire" % autowireVersion,
    "com.lihaoyi" %%% "upickle" % upickleVersion,
    "com.lihaoyi" %%% "scalatags" % scalatagsVersion,
    "com.lihaoyi" %%% "scalarx" % rxVersion,
    "com.timushev" %%% "scalatags-rx" % "0.3.0",
    "com.github.japgolly.scalacss" %%% "core" % "0.5.3",
    "com.github.japgolly.scalacss" %%% "ext-scalatags" % "0.5.3",
    "fr.iscpif" %%% "scaladget" % scaladgetVersion,
    "org.scala-js" %%% "scalajs-dom" % scalajsDomVersion,
    "org.json4s" %% "json4s-jackson" % json4sVersion,
    "org.scalanlp" %% "breeze" % "0.13.1",

    // Native libraries are not included by default. add this if you want them (as of 0.7)
    // Native libraries greatly improve performance, but increase jar sizes.
    // It also packages various blas implementations, which have licenses that may or may not
    // be compatible with the Apache License. No GPL code, as best I know.
    "org.scalanlp" %% "breeze-natives" % "0.13.1",

//    "io.argonaut" %%% "argonaut" % "6.2",
//    "com.chuusai" %%% "shapeless" % "2.3.2",

//    "com.github.alexarchambault" %%% "argonaut-shapeless_6.2" % "1.2.0-M4",
    "com.typesafe.play" % "play-json_2.12" % "2.6.3",


// The visualization library is distributed separately as well.
    // It depends on LGPL code
    "org.scalanlp" %% "breeze-viz" % "0.13.1",
    "org.typelevel" %%% "cats" % "0.9.0",
    "org.scala-js" %%% "scalajs-dom" % "0.9.0",
    "org.typelevel"  %%% "squants"  % "1.2.0",
    "org.scalatest" %%% "scalatest" % "3.0.0" % "test",
    "org.scalatest" %% "scalatest" % "3.0.0" % "test",
    "com.lihaoyi" %%% "pprint" % "0.5.2",
    "org.scalacheck" %%% "scalacheck" % "1.13.4" % "test"
  ),
  libraryDependencies ++= Seq(
    "io.circe" %% "circe-core",
    "io.circe" %% "circe-generic",
    "io.circe" %% "circe-parser"
  ).map(_ % circeVersion)

) dependsOn (shared) enablePlugins (ScalaJSPlugin)

lazy val server = project.in(file("server")) settings(
  organization := Organization,
  name := Name,
  version := Version,
  scalaVersion := ScalaVersion,
  resolvers ++= Resolvers,
  libraryDependencies ++= Seq(
    "com.lihaoyi" %% "autowire" % autowireVersion,
    "com.lihaoyi" %% "upickle" % upickleVersion,
    "com.lihaoyi" %% "scalatags" % scalatagsVersion,
    "org.scalatra" %% "scalatra" % scalatraVersion,
    "ch.qos.logback" % "logback-classic" % "1.1.3" % "runtime",
    "javax.servlet" % "javax.servlet-api" % "3.1.0" % "provided",
    "org.eclipse.jetty" % "jetty-webapp" % jettyVersion,
    "org.eclipse.jetty" % "jetty-server" % jettyVersion
  )
) dependsOn (shared) enablePlugins (JettyPlugin)

lazy val go = taskKey[Unit]("go")

lazy val bootstrap = project.in(file("target/bootstrap")) settings(
  version := Version,
  scalaVersion := ScalaVersion,
  go := {
    val clientTarget = (fastOptJS in client in Compile).value
//    val clientTarget = (fullOptJS in client in Compile).value
    val clientResource = (resourceDirectory in client in Compile).value
    val serverTarget = (target in server in Compile).value

    copy(clientTarget, clientResource, new File(serverTarget, "webapp"))
  }
) dependsOn(client, server)

def copy(clientTarget: Attributed[File], resources: File, webappServerTarget: File) = {
  clientTarget.map { ct =>
//    val depName = ct.getName.replace("opt.js", "jsdeps.min.js")
    val depName = ct.getName.replace("fastopt.js", "jsdeps.js")
//    val depName = ct.getName
    recursiveCopy(new File(resources, "webapp"), webappServerTarget)
    recursiveCopy(ct, new File(webappServerTarget, "js/" + ct.getName))
    recursiveCopy(new File(ct.getParent, depName), new File(webappServerTarget, "js/" + depName))
  }
}

def recursiveCopy(from: File, to: File): Unit = {
  if (from.isDirectory) {
    to.mkdirs()
    for {
      f ← from.listFiles()
    } recursiveCopy(f, new File(to, f.getName))
  }
  else if (!to.exists() || from.lastModified() > to.lastModified) {
    println(s"Copy file $from to $to ")
    from.getParentFile.mkdirs
    IO.copyFile(from, to, preserveLastModified = true)
  }
}
