
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
val scaladgetVersion = "0.9.5"
val scalajsDomVersion = "0.9.3"
val jqueryVersion = "2.2.1"
val circeVersion = "0.8.0"

import java.io.File
import org.scalatra.sbt.ScalatraPlugin

/*import sbtcrossproject.{crossProject, CrossType}*/


val Resolvers = Seq(
  Resolver.sonatypeRepo("snapshots"),
  Resolver.sonatypeRepo("releases"),
  "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"
)

val jqueryPath = s"META-INF/resources/webjars/jquery/$jqueryVersion/jquery.js"

def copy(clientTarget: Attributed[File], resources: File, webappServerTarget: File) = {
  clientTarget.map { ct =>
    // TODO Do both these replacements unconditionally in a safe way.
//    val depName = ct.getName.replace("opt.js", "jsdeps.min.js")
    val depName = ct.getName.replace("fastopt.js", "jsdeps.js")
    /*val depName = ct.getName.replace("opt.js", "jsdeps.js")*/
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

lazy val root = project.in(file(".")).
  aggregate(fooJS, fooJVM).
  settings(
    publish := {},
    publishLocal := {}
  ) enablePlugins (JettyPlugin)

lazy val foo = CrossPlugin.autoImport.crossProject(JSPlatform, JVMPlatform).in(file(".")).
  settings(
    name := "foo",
    scalaVersion := ScalaVersion,
    resolvers ++= Resolvers,
    version := "0.1-SNAPSHOT",
    testFrameworks += new TestFramework("utest.runner.Framework"),

    libraryDependencies ++= Seq(
      "com.lihaoyi" %%% "autowire" % autowireVersion,
      "com.lihaoyi" %%% "upickle" % upickleVersion,
      "com.lihaoyi" %%% "scalatags" % scalatagsVersion,
      "com.lihaoyi" %%% "scalarx" % rxVersion,
      /*"com.timushev" %%% "scalatags-rx" % "0.3.0",*/
      /*"org.scala-js" %%% "scalajs-dom" % scalajsDomVersion,*/
      "com.timushev" % "scalatags-rx_sjs0.6_2.12" % "0.3.0",
      "org.scala-js" % "scalajs-dom_sjs0.6_2.12" % scalajsDomVersion,
      "org.json4s" %% "json4s-jackson" % json4sVersion,
      "org.scalanlp" %% "breeze" % "0.13.1",
      "com.github.japgolly.scalacss" %%% "core" % "0.5.3",
      "com.github.japgolly.scalacss" %%% "ext-scalatags" % "0.5.3",
      /*"fr.iscpif" %%% "scaladget" % scaladgetVersion,*/
      "fr.iscpif" % "scaladget_sjs0.6_2.12" % scaladgetVersion,

      // Native libraries are not included by default. add this if you want them (as of 0.7)
      // Native libraries greatly improve performance, but increase jar sizes.
      // It also packages various blas implementations, which have licenses that may or may not
      // be compatible with the Apache License. No GPL code, as best I know.
      "org.scalanlp" %% "breeze-natives" % "0.13.1",

      "com.typesafe.play" %%% "play-json" % "2.6.3",

    // The visualization library is distributed separately as well.
      // It depends on LGPL code
      "org.scalanlp" %% "breeze-viz" % "0.13.1",
      "org.typelevel" %%% "cats" % "0.9.0",
      "org.typelevel"  %%% "squants"  % "1.2.0",
      "org.scalatest" %%% "scalatest" % "3.0.0" % "test",
      "org.scalatest" %% "scalatest" % "3.0.0" % "test",
      "com.lihaoyi" %%% "pprint" % "0.5.2",
      "org.scalacheck" %%% "scalacheck" % "1.13.4" % "test",
      "com.lihaoyi" %%% "utest" % "0.5.3" % "test"
    )
  ).
  jvmSettings(
    libraryDependencies ++= Seq(
      "org.scalatra" %% "scalatra" % scalatraVersion,
      "javax.servlet" % "javax.servlet-api" % "3.1.0" % "provided",
      "org.eclipse.jetty" % "jetty-webapp" % jettyVersion,
      "org.eclipse.jetty" % "jetty-server" % jettyVersion,
      "com.github.pathikrit" %% "better-files" % "3.3.1"

    )
  ).
  jsSettings(
    skip in packageJSDependencies := false,
    jsDependencies += "org.webjars" % "d3js" % "3.5.12" / "d3.min.js"
  )

lazy val fooJVM = foo.jvm enablePlugins (JettyPlugin)
lazy val fooJS = foo.js enablePlugins (ScalaJSPlugin)

lazy val bootstrap = project.in(file("target/bootstrap")) settings(
  version := Version,
  scalaVersion := ScalaVersion,
  go := {
    val clientTarget = (fullOptJS in fooJS in Compile).value
    /*val clientTarget = (fastOptJS in fooJS in Compile).value*/
    val clientResource = (resourceDirectory in fooJS in Compile).value
    val serverTarget = (target in fooJVM in Compile).value

    copy(clientTarget, clientResource, new File(serverTarget, "webapp"))
  }
) dependsOn(fooJS, fooJVM)


lazy val go = taskKey[Unit]("go")
