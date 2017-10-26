import Dependencies._

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "com.github.cornerman",
      scalaVersion := "2.12.3",
      version      := "0.1.0-SNAPSHOT"
    )),
    name := "sloth",
    libraryDependencies ++=
      shapeless ::
      scalaReflect % scalaVersion.value ::
      boopickle % Test ::
      circe.core % Test ::
      circe.parser % Test ::
      circe.generic % Test ::
      circe.shapes % Test ::
      scalaTest % Test ::
      Nil,
    scalacOptions ++=
      "-encoding" :: "UTF-8" ::
      "-unchecked" ::
      "-deprecation" ::
      "-explaintypes" ::
      "-feature" ::
      "-language:_" ::
      "-Xlint:_" ::
      "-Ywarn-unused" ::
      Nil
  )
