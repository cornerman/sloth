// shadow sbt-scalajs' crossProject and CrossType from Scala.js 0.6.x
import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

inThisBuild(Seq(
  organization := "com.github.cornerman",

  scalaVersion := "2.12.15",
  crossScalaVersions := Seq("2.12.15", "2.13.8"),

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
  addCompilerPlugin("org.typelevel" % "kind-projector" % "0.13.2" cross CrossVersion.full)
)

lazy val jsSettings = Seq(
    scalacOptions += {
      val githubRepo    = "cornerman/sloth"
      val local         = baseDirectory.value.toURI
      val subProjectDir = baseDirectory.value.getName
      val remote        = s"https://raw.githubusercontent.com/${githubRepo}/${git.gitHeadCommit.value.get}"
      s"-P:scalajs:mapSourceURI:$local->$remote/${subProjectDir}/"
    },
)

enablePlugins(ScalaJSPlugin)

lazy val root = (project in file("."))
  .settings(commonSettings)
  .aggregate(sloth.js, sloth.jvm, types.js, types.jvm)
  .settings(
    publish / skip := true
  )

lazy val types = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .settings(commonSettings)
  .settings(
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
