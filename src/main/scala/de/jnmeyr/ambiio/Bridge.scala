package de.jnmeyr.ambiio

import cats.effect.concurrent.{MVar, Ref}
import cats.effect.{Concurrent, ContextShift, Sync, Timer}
import cats.implicits._
import org.slf4j.LoggerFactory

import scala.concurrent.duration._

sealed trait Bridge[F[_], T] {

  def timeout: Timeout[F]

  def consume: F[(Consume[F, T], F[Unit])]

  def produce: F[Produce[F, T]]

}

object Bridge {

  private val logger = LoggerFactory.getLogger("Bridge")

  def worker[F[_] : Sync, T](lastValueOpt: Option[T] = None)
                            (fromVar: MVar[F, T],
                             toVar: MVar[F, T]): F[Unit] = for {
    value <- fromVar.take
    _ <- if (lastValueOpt.contains(value)) Sync[F].pure(()) else
      toVar.tryTake *> toVar.put(value)
    _ <- worker(Some(value))(fromVar, toVar)
  } yield ()

  private class Impl[F[_] : Concurrent, T](isTimeoutRef: Ref[F, Boolean],
                                           lastToVar: MVar[F, T],
                                           fromVarsRef: Ref[F, Vector[MVar[F, T]]])
                                          (implicit contextShift: ContextShift[F],
                                           timer: Timer[F])
    extends Bridge[F, T] {

    override def timeout: Timeout[F] = for {
      _ <- timer.sleep(10 minutes)
      isTimeout <- isTimeoutRef.get <* isTimeoutRef.set(true)
      _ <- if (isTimeout) Sync[F].delay(logger.warn("Bridge timed out")) else timeout
    } yield ()

    private def add(fromVar: MVar[F, T]): F[Unit] = fromVarsRef.update(fromVar +: _)

    private def remove(fromVar: MVar[F, T]): F[Unit] = fromVarsRef.update(_.filter(_ != fromVar))

    override def consume: F[(Consume[F, T], F[Unit])] = for {
      fromVar <- MVar.empty[F, T]
      toVar <- MVar.empty[F, T]
      fib <- Concurrent[F].start(worker()(fromVar, toVar))
      _ <- add(fromVar)
    } yield (toVar.take, remove(fromVar) *> fib.cancel)

    override val produce: F[Produce[F, T]] = Sync[F].delay { value =>
      for {
        lastToOpt <- lastToVar.tryTake <* lastToVar.put(value)
        _ <- if (lastToOpt.contains(value)) Sync[F].pure(()) else
          isTimeoutRef.set(false) *>
            fromVarsRef.get.flatMap(_.map(fromVar => fromVar.tryTake *> fromVar.put(value)).sequence_)
      } yield ()
    }

  }

  def apply[F[_] : Concurrent, T]()
                                 (implicit contextShift: ContextShift[F],
                                  timer: Timer[F]): F[Bridge[F, T]] = for {
    isTimeoutRef <- Ref.of[F, Boolean](true)
    lastToVar <- MVar.empty[F, T]
    fromVarsRef <- Ref.of[F, Vector[MVar[F, T]]](Vector.empty)
  } yield new Impl[F, T](isTimeoutRef, lastToVar, fromVarsRef)

}
