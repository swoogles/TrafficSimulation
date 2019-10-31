addSbtPlugin("org.scala-js"       % "sbt-scalajs"              % "0.6.29")
addSbtPlugin("org.portable-scala" % "sbt-crossproject"         % "0.3.0")  // (1)
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "0.3.0")  // (2)
//addSbtPlugin("ch.epfl.scala" % "sbt-scalajs-bundler" % "0.15.0")
/*addSbtPlugin("org.scala-native"   % "sbt-scala-native"         % "0.3.3")  // (3)*/
/*addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.3.3" exclude("org.scala-native", "sbt-crossproject"))*/

// Having this plugin automatically shift function calls so that 
// parameters are on separate lines helps demonstrate an important point.
// Having many paramaters is a cousin to Cyclomatic Complexity.
// When it's nesting/loops, it's easily recognized as a (potential) problem,
// but for some reason this is seen as less complex when dealing with parameters.
addSbtPlugin("com.geirsson" % "sbt-scalafmt" % "1.4.0")


// Auto push after I commit, when on a certain branch?
// If $beginningOfCommit isUnchanged,
//  -apppend, then --force-push
// If this only happens on certain, known-to-be-volatile branches, I can feel okay about it.
