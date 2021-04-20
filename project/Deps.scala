import sbt._
import Keys._
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._

object Deps {
  import Def.{setting => dep}

  val scalaReflect = dep("org.scala-lang" % "scala-reflect")
  val cats = dep("org.typelevel" %%% "cats-core" % "2.2.0")
  val chameleon = dep("com.github.cornerman" %%% "chameleon" % "0.3.0")

  val scalaTest = dep("org.scalatest" %%% "scalatest" % "3.2.8")
  val kittens = dep("org.typelevel" %%% "kittens" % "2.1.0")
  val circe = new {
    private val version = "0.13.0"
    val core = dep("io.circe" %%% "circe-core" % version)
    val generic = dep("io.circe" %%% "circe-generic" % version)
    val parser = dep("io.circe" %%% "circe-parser" % version)
    val shapes = dep("io.circe" %%% "circe-shapes" % version)
  }
}
