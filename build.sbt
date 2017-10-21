import Dependencies._

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "com.github.cornerman",
      scalaVersion := "2.12.3",
      version      := "0.1.0-SNAPSHOT"
    )),
    name := "apitrait",
    libraryDependencies ++=
      shapeless ::
      scalaReflect % scalaVersion.value ::
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
