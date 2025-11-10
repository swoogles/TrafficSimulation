
// ScalaJS
//scalaJSUseMainModuleInitializer := true // this is an application with a main method
//mainClass in Compile := Some("hello.Hello3") // must be Hello3 for this tutorial
enablePlugins(ScalaJSPlugin)
// ScalaJS 1.x: Test / jsEnv := new org.scalajs.jsenv.jsdomnodejs.JSDOMNodeJSEnv
//requireJsDomEnv in Test := true

val Organization = "com.billding"
val Name = "Traffice Simulator"
val Version = "0.2.0-SNAPSHOT"
val ScalaVer = "2.13.15"
val scalatagsVersion = "0.12.0"
val laminarVersion = "16.0.0"
val airstreamVersion = "16.0.0"
val scalajsDomVersion = "2.8.0"
val scalaCssVersion = "1.0.0"
val pprintVersion = "0.8.1"
val playJsonVersion = "2.10.5"
val scalaCheckVersion = "1.17.1"
val scalaTestVersion = "3.2.19"
val squantsVersion = "1.8.3"

ThisBuild / scalacOptions ++= Seq(
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
  "-Xlint:inaccessible",               // Warn about inaccessible types in method signatures.
  "-Xlint:infer-any",                  // Warn when a type argument is inferred to be `Any`.
  "-Wunused:implicits",                 // Warn if an implicit parameter is unused.
  "-Wunused:imports",                  // Warn if an import selector is not referenced.
  "-Wunused:locals",                    // Warn if a local definition is unused.
  "-Wunused:params",                    // Warn if a value parameter is unused.
  "-Wunused:patvars",                   // Warn if a variable bound in a pattern is unused.
  "-Wunused:privates",                  // Warn if a private member is unused.
  "-Wvalue-discard"                     // Warn when non-Unit expression results are unused.
)
import java.io.File

name := "traffic"
ThisBuild / scalaVersion := ScalaVer
resolvers ++= Resolver.sonatypeOssRepos("snapshots") ++ Resolver.sonatypeOssRepos("releases")
version := Version
//      testFrameworks += new TestFramework("utest.runner.Framework"),

libraryDependencies ++= Seq(
  "com.lihaoyi" %%% "scalatags" % scalatagsVersion,
  "com.raquo" %%% "laminar" % laminarVersion,
  "com.raquo" %%% "airstream" % airstreamVersion,
  "org.scala-js" %%% "scalajs-dom" % scalajsDomVersion,
  "com.github.japgolly.scalacss" %%% "core" % scalaCssVersion,
  "com.github.japgolly.scalacss" %%% "ext-scalatags" % scalaCssVersion,

  "com.typesafe.play" %%% "play-json" % playJsonVersion,

  "org.typelevel" %%% "cats-core" % "2.10.0",
  "org.typelevel" %%% "squants" % squantsVersion,
  "org.scalatest" %%% "scalatest" % scalaTestVersion % "test",
  "com.lihaoyi" %%% "pprint" % pprintVersion,
  "org.scalacheck" %%% "scalacheck" % scalaCheckVersion % "test",
)

// ScalaJS 1.x: JS dependencies are handled via npm/webpack or via jsDependencies (deprecated)
// For now, we'll comment this out - d3js can be added via npm if needed
// jsDependencies += "org.webjars" % "d3js" % "3.5.12" / "d3.min.js"


