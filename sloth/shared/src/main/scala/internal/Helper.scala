package sloth.helper

object EitherHelper {
  def sequence[A,B](list: List[Either[A,B]]) = list.partition(_.isLeft) match {
    case (Nil,  ints) => Right(for(Right(i) <- ints) yield i)
    case (strings, _) => Left(for(Left(s) <- strings) yield s)
  }
}
