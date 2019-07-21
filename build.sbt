organization in Global := "com.github.cornerman"
version in Global := "0.1.0"

lazy val commonSettings = Seq(
  scalaVersion := "2.12.8",
  crossScalaVersions := Seq("2.11.12", "2.12.8"),
  publishTo := sonatypePublishTo.value,

  scalacOptions ++=
    "-encoding" :: "UTF-8" ::
    "-unchecked" ::
    "-deprecation" ::
    "-explaintypes" ::
    "-feature" ::
    "-language:_" ::
    "-Xfuture" ::
    "-Xlint" ::
    "-Ypartial-unification" ::
    "-Yno-adapted-args" ::
    "-Ywarn-infer-any" ::
    "-Ywarn-value-discard" ::
    "-Ywarn-nullary-override" ::
    "-Ywarn-nullary-unit" ::
    Nil,

  scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 12)) =>
        "-Ywarn-extra-implicit" ::
        Nil
      case _ =>
        Nil
    }
  },

  addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3")
)

enablePlugins(ScalaJSPlugin)

lazy val root = (project in file("."))
  .settings(commonSettings)
  .aggregate(slothJS, slothJVM)
  .settings(
    skip in publish := true
  )

lazy val sloth = crossProject.crossType(CrossType.Pure)
  .settings(commonSettings)
  .settings(
    name := "sloth",
    libraryDependencies ++=
      Deps.scalaReflect.value % scalaVersion.value % Provided ::
      Deps.cats.value ::
      Deps.chameleon.value ::

      Deps.kittens.value % Test ::
      Deps.boopickle.value % Test ::
      Deps.circe.core.value % Test ::
      Deps.circe.generic.value % Test ::
      Deps.circe.parser.value % Test ::
      Deps.scalaTest.value % Test ::
      Nil
  )

lazy val slothJS = sloth.js
lazy val slothJVM = sloth.jvm


pomExtra in Global := {
  <url>https://github.com/cornerman/sloth</url>
  <licenses>
    <license>
      <name>The MIT License (MIT)</name>
      <url>http://opensource.org/licenses/MIT</url>
    </license>
  </licenses>
  <scm>
    <url>https://github.com/cornerman/sloth</url>
    <connection>scm:git:git@github.com:cornerman/sloth.git</connection>
  </scm>
  <developers>
    <developer>
      <id>jkaroff</id>
      <name>Johannes Karoff</name>
      <url>https://github.com/cornerman</url>
    </developer>
  </developers>
}
