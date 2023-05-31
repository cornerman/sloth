// shadow sbt-scalajs' crossProject and CrossType from Scala.js 0.6.x
import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

inThisBuild(Seq(
  organization := "com.github.cornerman",

  scalaVersion := "2.12.17",
  crossScalaVersions := Seq("2.12.17", "2.13.10"),

  licenses := Seq("MIT License" -> url("https://opensource.org/licenses/MIT")),

  homepage := Some(url("https://github.com/cornerman/sloth")),

  scmInfo := Some(ScmInfo(
    url("https://github.com/cornerman/sloth"),
    "scm:git:git@github.com:cornerman/sloth.git",
    Some("scm:git:git@github.com:cornerman/sloth.git"))
  ),

  pomExtra :=
    <developers>
      <developer>
        <id>jkaroff</id>
        <name>Johannes Karoff</name>
        <url>https://github.com/cornerman</url>
      </developer>
    </developers>
))

lazy val commonSettings = Seq(
  libraryDependencies ++= (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((3, _)) => Seq.empty
    case _ => Seq(compilerPlugin("org.typelevel" % "kind-projector" % "0.13.2" cross CrossVersion.full))
  }),
)

lazy val jsSettings = Seq(
)

enablePlugins(ScalaJSPlugin)

lazy val types = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .settings(commonSettings)
  .settings(
    crossScalaVersions := Seq("2.12.17", "2.13.10", "3.3.0"),
    name := "sloth-types",
    libraryDependencies ++=
      Deps.cats.value ::
      Nil
  ).jsSettings(jsSettings)

lazy val sloth = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .dependsOn(types)
  .settings(commonSettings)
  .settings(
    name := "sloth",
    libraryDependencies ++=
      Deps.scalaReflect.value % scalaVersion.value % Provided ::
      Deps.cats.value ::
      Deps.chameleon.value ::

      Deps.kittens.value % Test ::
      Deps.circe.core.value % Test ::
      Deps.circe.generic.value % Test ::
      Deps.circe.parser.value % Test ::
      Deps.scalaTest.value % Test ::
      Nil
  ).jsSettings(jsSettings)
