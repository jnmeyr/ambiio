package de.jnmeyr.ambiio

import cats.effect.concurrent.{MVar, Ref}
import cats.effect.{Concurrent, ContextShift, Sync}
import cats.implicits._

sealed trait Bridge[F[_], T] {

  def consume: F[(Consume[F, T], F[Unit])]

  def produce: F[Produce[F, T]]

}

object Bridge {

  def worker[F[_] : Sync, T](lastValueOpt: Option[T] = None)
                            (fromVar: MVar[F, T],
                             toVar: MVar[F, T]): F[Unit] = for {
    value <- fromVar.take
    _ <- if (lastValueOpt.contains(value)) Sync[F].pure(()) else toVar.tryTake *> toVar.put(value)
    _ <- worker(Some(value))(fromVar, toVar)
  } yield ()

  class Limited[F[_] : Concurrent, T] private(fromVarsRef: Ref[F, Vector[MVar[F, T]]])
                                             (implicit contextShift: ContextShift[F]) extends Bridge[F, T] {

    def add(fromVar: MVar[F, T]): F[Unit] = fromVarsRef.update(fromVar +: _)

    def remove(fromVar: MVar[F, T]): F[Unit] = fromVarsRef.update(_.filter(_ != fromVar))

    override def consume: F[(Consume[F, T], F[Unit])] = for {
      fromVar <- MVar.empty[F, T]
      toVar <- MVar.empty[F, T]
      fib <- Concurrent[F].start(worker()(fromVar, toVar))
      _ <- add(fromVar)
    } yield (toVar.take, remove(fromVar) *> fib.cancel)

    override val produce: F[Produce[F, T]] = Sync[F].delay { value =>
      fromVarsRef.get.flatMap(_.map(fromVar => fromVar.tryTake *> fromVar.put(value)).sequence_)
    }

  }

  object Limited {

    def apply[F[_] : Concurrent, T](implicit contextShift: ContextShift[F]): F[Limited[F, T]] = {
      Ref.of[F, Vector[MVar[F, T]]](Vector.empty).map(new Limited(_))
    }

  }

}
