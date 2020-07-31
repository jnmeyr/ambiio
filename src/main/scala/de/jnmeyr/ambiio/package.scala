package de.jnmeyr

import cats.effect.{IO, Sync, Timer}
import cats.implicits._

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.Try

package object ambiio {

  type Consume[F[_], T] = F[T]

  type Consumer[F[_], T] = (Consume[F, T], F[Unit]) => F[Unit]

  type Produce[F[_], T] = T => F[Unit]

  type Producer[F[_], T] = Produce[F, T] => F[Unit]

  def run[F[_] : Sync](f: F[Unit]): F[Unit] = for {
    _ <- f
    _ <- run(f)
  } yield ()

  def runEvery[F[_] : Sync](every: FiniteDuration)
                           (f: F[Unit])
                           (implicit timer: Timer[F]): F[Unit] = for {
    _ <- f
    _ <- timer.sleep(every)
    _ <- runEvery(every)(f)
  } yield ()

  object Implicits {

    implicit class RichString(val string: String) extends AnyVal {

      def toFiniteDurationOpt: Option[FiniteDuration] = Try(Duration(string).asInstanceOf[FiniteDuration]).toOption

      def isFiniteDuration: Boolean = toFiniteDurationOpt.isDefined

      def toFiniteDuration: FiniteDuration = toFiniteDurationOpt.getOrElse(throw new IllegalArgumentException(string))

      def toPixelOpt: Option[Pixel] = Pixel.unapply(string)

      def isPixel: Boolean = toPixelOpt.isDefined

      def toPixel: Pixel = toPixelOpt.getOrElse(throw new IllegalArgumentException(string))

    }

  }

}
