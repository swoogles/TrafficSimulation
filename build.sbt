
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

scalacOptions in ThisBuild ++= Seq(
  "-deprecation",                      // Emit warning and location for usages of deprecated APIs.
  "-encoding", "utf-8",                // Specify character encoding used by source files.
  "-explaintypes",                     // Explain type errors in more detail.
  "-feature",                          // Emit warning and location for usages of features that should be imported explicitly.
  /*"-language:existentials",            // Existential types (besides wildcard types) can be written and inferred*/
  /*"-language:experimental.macros",     // Allow macro definition (besides implementation and application)*/
  /*"-language:higherKinds",             // Allow higher-kinded types*/
  /*"-language:implicitConversions",     // Allow definition of implicit functions called views*/
  /*"-unchecked",                        // Enable additional warnings where generated code depends on assumptions.*/
  /*"-Xcheckinit",                       // Wrap field accessors to throw an exception on uninitialized access.*/
  /*"-Xfatal-warnings",                  // Fail the compilation if there are any warnings.*/
  /*"-Xfuture",                          // Turn on future language features.*/
  /*"-Xlint:adapted-args",               // Warn if an argument list is modified to match the receiver.*/
  /*"-Xlint:by-name-right-associative",  // By-name parameter of right associative operator.*/
  /*"-Xlint:constant",                   // Evaluation of a constant arithmetic expression results in an error.*/
  /*"-Xlint:delayedinit-select",         // Selecting member of DelayedInit.*/
  /*"-Xlint:doc-detached",               // A Scaladoc comment appears to be detached from its element.*/
  /*"-Xlint:inaccessible",               // Warn about inaccessible types in method signatures.*/
  /*"-Xlint:infer-any",                  // Warn when a type argument is inferred to be `Any`.*/
  /*"-Xlint:missing-interpolator",       // A string literal appears to be missing an interpolator id.*/
  /*"-Xlint:nullary-override",           // Warn when non-nullary `def f()' overrides nullary `def f'.*/
  /*"-Xlint:nullary-unit",               // Warn when nullary methods return Unit.*/
  /*"-Xlint:option-implicit",            // Option.apply used implicit view.*/
  /*"-Xlint:package-object-classes",     // Class or object defined in package object.*/
  /*"-Xlint:poly-implicit-overload",     // Parameterized overloaded implicit methods are not visible as view bounds.*/
  /*"-Xlint:private-shadow",             // A private field (or class parameter) shadows a superclass field.*/
  /*"-Xlint:stars-align",                // Pattern sequence wildcard must align with sequence component.*/
  /*"-Xlint:type-parameter-shadow",      // A local type parameter shadows a type already in scope.*/
  /*"-Xlint:unsound-match",              // Pattern match may not be typesafe.*/
  /*"-Yno-adapted-args",                 // Do not adapt an argument list (either by inserting () or creating a tuple) to match the receiver.*/
  /*"-Ypartial-unification",             // Enable partial unification in type constructor inference*/
  /*"-Ywarn-dead-code",                  // Warn when dead code is identified.*/
  "-Ywarn-extra-implicit",             // Warn when more than one implicit parameter section is defined.
  "-Ywarn-inaccessible",               // Warn about inaccessible types in method signatures.
  "-Ywarn-infer-any",                  // Warn when a type argument is inferred to be `Any`.
  /*"-Ywarn-nullary-override",           // Warn when non-nullary `def f()' overrides nullary `def f'.*/
  /*"-Ywarn-nullary-unit",               // Warn when nullary methods return Unit.*/
  /*"-Ywarn-numeric-widen",              // Warn when numerics are widened.*/
  "-Ywarn-unused:implicits",           // Warn if an implicit parameter is unused.
  "-Ywarn-unused:imports",             // Warn if an import selector is not referenced.
  "-Ywarn-unused:locals",              // Warn if a local definition is unused.
  "-Ywarn-unused:params",              // Warn if a value parameter is unused.
  "-Ywarn-unused:patvars",             // Warn if a variable bound in a pattern is unused.
  "-Ywarn-unused:privates",            // Warn if a private member is unused.
  "-Ywarn-value-discard"               // Warn when non-Unit expression results are unused.
)
import java.io.File

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
  aggregate(trafficJS, trafficJVM).
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
      "com.github.pureconfig" %%% "pureconfig" % "0.8.0",
      "com.github.pureconfig" %%% "pureconfig-squants" % "0.8.0",
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
lazy val trafficJS = traffic.js enablePlugins (ScalaJSPlugin)

lazy val bootstrap = project.in(file("target/bootstrap")) settings(
  version := Version,
  scalaVersion := ScalaVersion,
  go := {
    /*val clientTarget = (fullOptJS in trafficJS in Compile).value*/
    val clientTarget = (fastOptJS in trafficJS in Compile).value
    val clientResource = (resourceDirectory in trafficJS in Compile).value
    val serverTarget = (target in trafficJVM in Compile).value

    copy(clientTarget, clientResource, new File(serverTarget, "webapp"))
  }
) dependsOn(trafficJS, trafficJVM)


lazy val go = taskKey[Unit]("go")

