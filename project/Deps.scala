import sbt._
import Keys._
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._

object Deps {
  import Def.{setting => dep}

  val scalaReflect = dep("org.scala-lang" % "scala-reflect")
  val scalaTest = dep("org.scalatest" %%% "scalatest" % "3.0.8")
  val cats = dep("org.typelevel" %%% "cats-core" % "1.6.1")
  val kittens = dep("org.typelevel" %%% "kittens" % "1.2.1")
  val chameleon = dep("com.github.cornerman.chameleon" %%% "chameleon" % "49f343a")
  val boopickle = dep("io.suzaku" %%% "boopickle" % "1.3.1")
  val circe = new {
    private val version = "0.11.1"
    val core = dep("io.circe" %%% "circe-core" % version)
    val generic = dep("io.circe" %%% "circe-generic" % version)
    val parser = dep("io.circe" %%% "circe-parser" % version)
    val shapes = dep("io.circe" %%% "circe-shapes" % version)
  }
}
