package test3

import chameleon.*
import chameleon.ext.zioJson.given
import sloth.*
import zio.json.*

import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.must.Matchers

import scala.concurrent.Future

trait Api {
  def single(page: Int): Future[String]
  def list(page: Int, limit: Int): Future[String]
}

class Sloth3Spec extends AsyncFreeSpec with Matchers {

  "compile client" in {
    val client = Client[String, Future](null)
    client.wire[Api]

    succeed
  }

  "compile router" in {
    val router = Router[String, Future].route[Api](null)

    succeed
  }
}
