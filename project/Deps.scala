import sbt._
import Keys._
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._

object Deps {
  import Def.{setting => dep}

  val scalaReflect = dep("org.scala-lang" % "scala-reflect")
  val scalaTest = dep("org.scalatest" %%% "scalatest" % "3.0.4")
  val cats = dep("org.typelevel" %%% "cats-core" % "1.0.1")
  val kittens = dep("org.typelevel" %%% "kittens" % "1.0.0-RC2")
  val chameleon = dep("com.github.cornerman.chameleon" %%% "chameleon" % "64cb53f")
  val boopickle = dep("io.suzaku" %%% "boopickle" % "1.2.6")
  val circe = new {
    private val version = "0.9.1"
    val core = dep("io.circe" %%% "circe-core" % version)
    val generic = dep("io.circe" %%% "circe-generic" % version)
    val parser = dep("io.circe" %%% "circe-parser" % version)
    val shapes = dep("io.circe" %%% "circe-shapes" % version)
  }
}
