import sbt._
import Keys._

object Dependencies {
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.0.3"
  lazy val shapeless = "com.chuusai" %% "shapeless" % "2.3.2"
  lazy val scalaReflect = "org.scala-lang" % "scala-reflect"
  lazy val boopickle = "io.suzaku" %% "boopickle" % "1.2.6"
  lazy val circe = new {
    private val version = "0.8.0"
    val core = "io.circe" %% "circe-core" % version
    val generic = "io.circe" %% "circe-generic" % version
    val parser = "io.circe" %% "circe-parser" % version
    val shapes = "io.circe" %% "circe-shapes" % version
  }
}
