inThisBuild(Seq(
  organization := "com.github.cornerman",
  version      := "0.1.0-SNAPSHOT",

  scalaVersion := "2.12.4",
  crossScalaVersions := Seq("2.11.11", "2.12.4"),

  resolvers ++= (
    ("jitpack" at "https://jitpack.io") ::
    Nil
  )
))

lazy val commonSettings = Seq(
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
    "-Ypartial-unification" ::
    "-Yno-adapted-args" ::
    "-Ywarn-infer-any" ::
    "-Ywarn-value-discard" ::
    "-Ywarn-nullary-override" ::
    "-Ywarn-nullary-unit" ::
    "-Ywarn-unused" ::
    Nil,

  scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 12)) =>
        "-Ywarn-extra-implicit" ::
        Nil
      case _ =>
        Nil
    }
  }
)

enablePlugins(ScalaJSPlugin)

lazy val root = (project in file("."))
  .settings(commonSettings)
  .aggregate(slothJS, slothJVM, boopickleJS, boopickleJVM, circeJS, circeJVM, myceliumJS, myceliumJVM)

lazy val sloth = crossProject
  .settings(commonSettings)
  .settings(
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

lazy val boopickle = crossProject
  .dependsOn(sloth)
  .settings(commonSettings)
  .settings(
    name := "sloth-boopickle",
    libraryDependencies ++=
      Deps.boopickle.value ::
      Deps.scalaTest.value % Test ::
      Nil
  )

lazy val boopickleJS = boopickle.js
lazy val boopickleJVM = boopickle.jvm

lazy val circe = crossProject
  .dependsOn(sloth)
  .settings(commonSettings)
  .settings(
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

lazy val mycelium = crossProject
  .dependsOn(sloth)
  .dependsOn(boopickle % "test->compile")
  .settings(commonSettings)
  .settings(
    name := "sloth-mycelium",
    libraryDependencies ++=
      Deps.mycelium.value ::
      Deps.cats.kittens.value % Test ::
      Deps.scalaTest.value % Test ::
      Nil
  )

lazy val myceliumJS = mycelium.js
lazy val myceliumJVM = mycelium.jvm
