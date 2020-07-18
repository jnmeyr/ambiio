package de.jnmeyr

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.Try

package object ambiio {

  type Consume[F[_]] = F[Bridge.Values]

  type Consumer[F[_]] = Consume[F] => F[Unit]

  type Produce[F[_]] = Bridge.Values => F[Unit]

  type Producer[F[_]] = (Produce[F], F[Boolean]) => F[Unit]

  object Implicits {

    implicit class RichString(val string: String) extends AnyVal {

      def toFiniteDurationOpt: Option[FiniteDuration] = Try(Duration(string).asInstanceOf[FiniteDuration]).toOption

      def isFiniteDuration: Boolean = toFiniteDurationOpt.isDefined

      def toFiniteDuration: FiniteDuration = toFiniteDurationOpt.get

      def toPixelOpt: Option[Pixel] = Pixel.fromString(string)

      def isPixel: Boolean = toPixelOpt.isDefined

      def toPixel: Pixel = toPixelOpt.get

    }

  }

}
