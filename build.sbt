inThisBuild(Seq(
  organization := "com.github.cornerman",
  scalaVersion := "2.12.4",
  crossScalaVersions := Seq("2.11.11", "2.12.4"),
  version      := "0.1.0-SNAPSHOT",
  scalacOptions ++=
    "-encoding" :: "UTF-8" ::
    "-unchecked" ::
    "-deprecation" ::
    "-explaintypes" ::
    "-feature" ::
    "-language:_" ::
    "-Xcheckinit" ::
    "-Xfuture" ::
    "-Xlint" ::
    "-Yno-adapted-args" ::
    "-Ywarn-dead-code" ::
    "-Ywarn-extra-implicit" ::
    "-Ywarn-unused" ::
    "-Ywarn-infer-any" ::
    "-Ywarn-nullary-override" ::
    "-Ywarn-nullary-unit" ::
    Nil
))

resolvers += Resolver.sonatypeRepo("releases")
addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.4")
enablePlugins(ScalaJSPlugin)

lazy val root = (project in file(".")).
  aggregate(slothJS, slothJVM, boopickleJS, boopickleJVM, circeJS, circeJVM)

lazy val sloth = crossProject.
  settings(
    name := "sloth",
    libraryDependencies ++=
      Deps.scalaReflect.value % scalaVersion.value ::
      Deps.shapeless.value ::
      Deps.cats.core.value ::
      Deps.cats.kittens.value % Test ::
      Deps.scalaTest.value % Test ::
      Nil
  )

lazy val slothJS = sloth.js
lazy val slothJVM = sloth.jvm

lazy val boopickle = crossProject.
  dependsOn(sloth).
  settings(
    name := "sloth-boopickle",
    libraryDependencies ++=
      Deps.boopickle.value ::
      Deps.scalaTest.value % Test ::
      Nil
  )

lazy val boopickleJS = boopickle.js
lazy val boopickleJVM = boopickle.jvm

lazy val circe = crossProject.
  dependsOn(sloth).
  settings(
    name := "sloth-circe",
    libraryDependencies ++=
      Deps.circe.core.value ::
      Deps.circe.parser.value ::
      Deps.circe.generic.value ::
      Deps.circe.shapes.value ::
      Deps.scalaTest.value % Test ::
      Nil
  )

lazy val circeJS = circe.js
lazy val circeJVM = circe.jvm
