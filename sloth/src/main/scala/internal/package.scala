package sloth

package object internal {
  def eitherSeq[A, B](list: List[Either[A, B]]): Either[List[A], List[B]] = list.partition(_.isLeft) match {
    case (Nil, rights) => Right(for (Right(i) <- rights) yield i)
    case (lefts, _)    => Left(for (Left(s) <- lefts) yield s)
  }
}
