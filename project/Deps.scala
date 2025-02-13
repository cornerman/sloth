import sbt._
import Keys._
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._

object Deps {
  import Def.{setting => dep}

  val scalaTest = dep("org.scalatest" %%% "scalatest" % "3.2.19")

  val scalaReflect = dep("org.scala-lang" % "scala-reflect")
  val cats = dep("org.typelevel" %%% "cats-core" % "2.13.0")
  val catsEffect = dep("org.typelevel" %%% "cats-effect" % "3.5.7")
  val chameleon = dep("com.github.cornerman" %%% "chameleon" % "0.4.1")

  val zioJson = dep("dev.zio" %%% "zio-json" % "0.7.19")

  val circe = new {
    private val version = "0.14.1"
    val core = dep("io.circe" %%% "circe-core" % version)
    val generic = dep("io.circe" %%% "circe-generic" % version)
    val parser = dep("io.circe" %%% "circe-parser" % version)
    val shapes = dep("io.circe" %%% "circe-shapes" % version)
  }

  val http4s = new {
    private val version = "0.23.24"
    val core = dep("org.http4s" %%% "http4s-core" % version)
    val dsl = dep("org.http4s" %%% "http4s-dsl" % version)
    val client = dep("org.http4s" %%% "http4s-client" % version)
  }

  val scalaJsDom = dep("org.scala-js" %%% "scalajs-dom" % "2.8.0")
}
