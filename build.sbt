
val Organization = "fr.iscpif"
val Name = "Traffice Simulator"
val Version = "0.1.0-SNAPSHOT"
val ScalaVersion = "2.12.4"
val scalatraVersion = "2.6.2"
val jettyVersion = "9.4.8.v20171121"
val scalatagsVersion = "0.6.7"
val autowireVersion = "0.2.6"
val rxVersion = "0.3.2"
val scaladgetVersion = "0.9.5"
val scalajsDomVersion = "0.9.4"
val jqueryVersion = "2.2.1"
val circeVersion = "0.8.0"
val scalaCssVersion = "0.5.4"
val betterFilesVersion = "3.4.0"
val pprintVersion = "0.5.3"
val utestVersion = "0.6.3"
val playJsonVersion = "2.6.8"
val scalaCheckVersion = "1.13.5"
val breezeVersion = "0.13.2"
val scalaTestVersion = "3.0.4"
val squantsVersion = "1.3.0"
val servletApiVersion = "4.0.0"

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
  aggregate(fooJS, trafficJVM).
  settings(
    publish := {},
    publishLocal := {}
  ) enablePlugins (JettyPlugin)

lazy val traffic = CrossPlugin.autoImport.crossProject(JSPlatform, JVMPlatform).in(file(".")).
  settings(
    name := "traffic",
    scalaVersion := ScalaVersion,
    resolvers ++= Resolvers,
    version := "0.1-SNAPSHOT",
    testFrameworks += new TestFramework("utest.runner.Framework"),

    libraryDependencies ++= Seq(
      "com.lihaoyi" %%% "autowire" % autowireVersion,
      "com.lihaoyi" %%% "scalatags" % scalatagsVersion,
      "com.lihaoyi" %%% "scalarx" % rxVersion,
      "org.scala-js" % "scalajs-dom_sjs0.6_2.12" % scalajsDomVersion,
      "org.scalanlp" %% "breeze" % breezeVersion,
      "com.github.japgolly.scalacss" %%% "core" % scalaCssVersion,
      "com.github.japgolly.scalacss" %%% "ext-scalatags" % scalaCssVersion,
      "fr.iscpif" % "scaladget_sjs0.6_2.12" % scaladgetVersion,

      // Native libraries are not included by default. add this if you want them (as of 0.7)
      // Native libraries greatly improve performance, but increase jar sizes.
      // It also packages various blas implementations, which have licenses that may or may not
      // be compatible with the Apache License. No GPL code, as best I know.
      "org.scalanlp" %% "breeze-natives" % breezeVersion,

      "com.typesafe.play" %%% "play-json" % playJsonVersion,

    // The visualization library is distributed separately as well.
      // It depends on LGPL code
      "org.scalanlp" %% "breeze-viz" % breezeVersion,
      "org.typelevel" %%% "cats" % "0.9.0",
      "org.typelevel"  %%% "squants"  % squantsVersion,
      "org.scalatest" %%% "scalatest" % scalaTestVersion % "test",
      "com.lihaoyi" %%% "pprint" % pprintVersion,
      "org.scalacheck" %%% "scalacheck" % scalaCheckVersion % "test",
      "com.lihaoyi" %%% "utest" % utestVersion % "test"
    )
  ).
  jvmSettings(
    libraryDependencies ++= Seq(
      "org.scalatra" %% "scalatra" % scalatraVersion,
      "javax.servlet" % "javax.servlet-api" % servletApiVersion % "provided",
      "org.eclipse.jetty" % "jetty-webapp" % jettyVersion,
      "org.eclipse.jetty" % "jetty-server" % jettyVersion,
      "com.github.pathikrit" %% "better-files" % betterFilesVersion

    )
  ).
  jsSettings(
    skip in packageJSDependencies := false,
    jsDependencies += "org.webjars" % "d3js" % "3.5.12" / "d3.min.js"
  )

lazy val trafficJVM = traffic.jvm enablePlugins (JettyPlugin)
lazy val fooJS = traffic.js enablePlugins (ScalaJSPlugin)

lazy val bootstrap = project.in(file("target/bootstrap")) settings(
  version := Version,
  scalaVersion := ScalaVersion,
  go := {
    /*val clientTarget = (fullOptJS in fooJS in Compile).value*/
    val clientTarget = (fastOptJS in fooJS in Compile).value
    val clientResource = (resourceDirectory in fooJS in Compile).value
    val serverTarget = (target in trafficJVM in Compile).value

    copy(clientTarget, clientResource, new File(serverTarget, "webapp"))
  }
) dependsOn(fooJS, trafficJVM)


lazy val go = taskKey[Unit]("go")
