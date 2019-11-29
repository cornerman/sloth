package test.sloth

import org.scalatest._
import sloth.ChecksumCalculator._
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.must.Matchers

class ChecksumSpec extends AsyncFreeSpec with Matchers {
  "same" in {
    trait Api {
      def pieksen: Boolean
    }
    object Other {
      trait Api {
        def pieksen: Boolean
      }
    }

    checksumOf[Api] mustEqual checksumOf[Other.Api]
  }

  "method order" in {
    trait Api {
      def pieksen: Boolean
      def hauen(feste: String): String
    }
    object Other {
      trait Api {
        def hauen(feste: String): String
        def pieksen: Boolean
      }
    }

    checksumOf[Api] mustEqual checksumOf[Other.Api]
  }

  "param oder in method" in {
    trait Api {
      def hauen(feste: String, nichtSoFeste: Int): String
    }
    object Other {
    trait Api {
      def hauen(nichtSoFeste: Int, feste: String): String
    }
    }

    checksumOf[Api] must not equal checksumOf[Other.Api]
  }

  "switch param name" in {
    trait Api {
      def abziehen(nett: Boolean): String
    }
    object Other {
    trait Api {
      def abziehen(boese: Boolean): String
    }
    }

    checksumOf[Api] must not equal checksumOf[Other.Api]
  }

  "switch param type" in {
    trait Api {
      def abziehen(nett: Boolean): String
    }
    object Other {
    trait Api {
      def abziehen(nett: Int): String
    }
    }

    checksumOf[Api] must not equal checksumOf[Other.Api]
  }

  "entirely different param" in {
    trait Api {
      def abziehen(nett: Boolean): String
    }
    object Other {
    trait Api {
      def abziehen(boese: Int): String
    }
    }

    checksumOf[Api] must not equal checksumOf[Other.Api]
  }

  "different result type of function" in {
    trait Api {
      def abziehen(nett: Boolean): String
    }
    object Other {
    trait Api {
      def abziehen(nett: Boolean): Int
    }
    }

    checksumOf[Api] must not equal checksumOf[Other.Api]
  }

  "different order of subclasses in type" in {
    pending // the checksum checks the fullname of a type. to run this test, replace fullName with name in typeHashCode

    sealed trait Tesa
    case class Film(meter: Int) extends Tesa
    case object Foto extends Tesa
    trait Api {
      def kleben(mit: Tesa): Int
    }
    object Other {
    sealed trait Tesa
    case object Foto extends Tesa
    case class Film(meter: Int) extends Tesa
    trait Api {
      def kleben(mit: Tesa): Int
    }
    }

    checksumOf[Api] mustEqual checksumOf[Other.Api]
  }

  "switch accessor name in type" in {
    case class Film(meter: Int)
    trait Api {
      def kleben(mit: Film): Int
    }
    object Other {
    case class Film(kilo: Int)
    trait Api {
      def kleben(mit: Film): Int
    }
    }

    checksumOf[Api] must not equal checksumOf[Other.Api]
  }

  "switch accessor type in type" in {
    case class Film(meter: Int)
    trait Api {
      def kleben(mit: Film): Int
    }
    object Other {
    case class Film(meter: Double)
    trait Api {
      def kleben(mit: Film): Int
    }
    }

    checksumOf[Api] must not equal checksumOf[Other.Api]
  }

  "different generic in param" in {
    case class Film[T](meter: T)
    trait Api {
      def kleben(mit: Film[Int]): Int
    }
    object Other {
    trait Api {
      def kleben(mit: Film[Double]): Int
    }
    }

    checksumOf[Api] must not equal checksumOf[Other.Api]
  }

  "different generic in type" in {
    case class Film[T](meter: T)
    trait Api {
      def kleben(mit: Film[Int]): Int
    }
    object Other {
    trait Api {
      def kleben(mit: Film[Double]): Int
    }
    }

    checksumOf[Api] must not equal checksumOf[Other.Api]
  }

  "generic api" in {
    trait Api[Result[_]] {
      def kleben(mit: Int): Result[Int]
    }

    checksumOf[Api[Option]] mustEqual checksumOf[Api[Option]]
    checksumOf[Api[Option]] must not equal checksumOf[Api[Function0]]
  }
}
