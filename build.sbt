Global / onChangedBuildSource := IgnoreSourceChanges

inThisBuild(Seq(
  organization := "com.github.cornerman",

  crossScalaVersions := Seq("2.13.17", "3.3.0"),
  scalaVersion := crossScalaVersions.value.head,

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
    case _ => Seq(compilerPlugin("org.typelevel" % "kind-projector" % "0.13.3" cross CrossVersion.full))
  }),
)

lazy val jsSettings = Seq(
  scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((3, _)) => Seq("-scalajs")
    case _ => Seq.empty
  }),
)

enablePlugins(ScalaJSPlugin)

lazy val types = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .settings(commonSettings)
  .settings(
    crossScalaVersions := Seq("2.12.20", "2.13.17", "3.3.0"),
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
    libraryDependencies ++= (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((3, _)) => Seq.empty
      case _ => Seq(Deps.scalaReflect.value % scalaVersion.value % Provided)
    }),
    libraryDependencies ++=
      Deps.cats.value ::
      Deps.chameleon.value ::

      Deps.zioJson.value % Test ::
      Deps.circe.core.value % Test ::
      Deps.circe.generic.value % Test ::
      Deps.circe.parser.value % Test ::
      Deps.scalaTest.value % Test ::
      Nil
  ).jsSettings(jsSettings)

lazy val jsdomClient = project
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(sloth.js)
  .settings(commonSettings)
  .settings(jsSettings)
  .settings(
    name := "sloth-jsdom-client",
    libraryDependencies ++=
      Deps.scalaJsDom.value ::
      Deps.catsEffect.value ::
      Nil
  )

lazy val http4sClient = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .dependsOn(sloth)
  .settings(commonSettings)
  .settings(
    name := "sloth-http4s-client",
    libraryDependencies ++=
      Deps.http4s.core.value ::
      Deps.http4s.client.value ::
      Nil
  ).jsSettings(jsSettings)

lazy val http4sServer = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .dependsOn(sloth)
  .settings(commonSettings)
  .settings(
    name := "sloth-http4s-server",
    libraryDependencies ++=
      Deps.http4s.core.value ::
      Deps.http4s.dsl.value ::
      Nil
  ).jsSettings(jsSettings)
