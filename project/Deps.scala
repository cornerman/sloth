import sbt._
import Keys._
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._

object Deps {
  import Def.{setting => dep}

  val scalaReflect = dep("org.scala-lang" % "scala-reflect")
  val scalaTest = dep("org.scalatest" %%% "scalatest" % "3.0.4")
  val shapeless = dep("com.chuusai" %%% "shapeless" % "2.3.3")
  val cats = dep("org.typelevel" %%% "cats-core" % "1.0.1")
  val kittens = dep("org.typelevel" %%% "kittens" % "1.0.0-RC2")
  val mycelium = dep("com.github.cornerman" % "mycelium" % "16ccd27")
  val chameleon = dep("com.github.cornerman" % "chameleon" % "54583b2")
  val boopickle = dep("io.suzaku" %%% "boopickle" % "1.2.6")
}
