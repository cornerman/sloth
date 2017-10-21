import sbt._
import Keys._

object Dependencies {
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.0.3"
  lazy val shapeless = "com.chuusai" %% "shapeless" % "2.3.2"
  lazy val scalaReflect = "org.scala-lang" % "scala-reflect"
}
