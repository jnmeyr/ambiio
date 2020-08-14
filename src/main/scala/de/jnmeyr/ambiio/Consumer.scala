package de.jnmeyr.ambiio

import java.net.{InetSocketAddress, SocketAddress}

import cats.effect.{Sync, Timer}
import cats.implicits._
import org.slf4j.LoggerFactory

import scala.concurrent.duration.{FiniteDuration, _}

object Consumer {

  private val logger = LoggerFactory.getLogger("Consumer")

  sealed trait Arguments {

    def pixels: Int

  }

  private def log[F[_] : Sync](arguments: Arguments,
                               result: Either[Throwable, Unit]): F[Unit] = Sync[F].delay {
    result.left.map { throwable =>
      logger.error(s"Consumer with $arguments crashed: $throwable")
    }
  }

  object Printer {

    case class Arguments(override val pixels: Int)
      extends Consumer.Arguments

    def apply[F[_] : Sync](arguments: Arguments): Consumer[F, Values] = (consume: F[Values], stop) => {
      logger.info(s"Printer with $arguments started")

      run {
        for {
          values <- consume
        } yield {
          val pixels = values.toPixels(arguments.pixels)
          println(pixels.map(_.toHexString).mkString(" "))
        }
      }.attempt.flatMap(log(arguments, _) *> stop)
    }

  }

  object Serial {

    case class Arguments(override val pixels: Int,
                         every: FiniteDuration = 25 millis,
                         name: String = "")
      extends Consumer.Arguments

    def apply[F[_] : Sync](arguments: Arguments)
                          (implicit timer: Timer[F]): Consumer[F, Values] = (consume: F[Values], stop) => {
      logger.info(s"Serial with $arguments started")

      Tape.Serial[F](arguments.name).use(tape =>
        runEvery(arguments.every) {
          for {
            values <- consume
            pixels = values.toPixels(arguments.pixels)
            _ <- tape.set(pixels)
          } yield ()
        }
      ).attempt.flatMap(log(arguments, _) *> stop)
    }

  }

  object Socket {

    case class Arguments(override val pixels: Int,
                         every: FiniteDuration = 25 millis,
                         host: String = "",
                         port: Int = 0)
      extends Consumer.Arguments {

      lazy val address: SocketAddress = new InetSocketAddress(host, port)

    }

    def apply[F[_] : Sync](arguments: Arguments)
                          (implicit timer: Timer[F]): Consumer[F, Values] = (consume: F[Values], stop) => {
      logger.info(s"Socket with $arguments started")

      Tape.Socket[F](arguments.address).use(tape =>
        runEvery(arguments.every) {
          for {
            values <- consume
            pixels = values.toPixels(arguments.pixels)
            _ <- tape.set(pixels)
          } yield ()
        }
      ).attempt.flatMap(log(arguments, _) *> stop)
    }

  }

  object Telemetry {

    case class Arguments(override val pixels: Int,
                         server: String = "tcp://localhost:1883",
                         topic: String = "ambiio")
      extends Consumer.Arguments

    def apply[F[_] : Sync](arguments: Arguments): Consumer[F, Values] = (consume: F[Values], stop) => {
      logger.info(s"Telemetry with $arguments started")

      Tape.Telemetry[F](arguments.server, arguments.topic).use(tape =>
        run {
          for {
            values <- consume
            pixels = values.toPixels(arguments.pixels)
            _ <- tape.set(pixels)
          } yield ()
        }
      ).attempt.flatMap(log(arguments, _) *> stop)
    }

  }

  def apply[F[_] : Sync](arguments: Arguments)
                        (implicit timer: Timer[F]): Consumer[F, Values] = arguments match {
    case printerArguments: Printer.Arguments => Printer(printerArguments)
    case serialArguments: Serial.Arguments => Serial(serialArguments)
    case socketArguments: Socket.Arguments => Socket(socketArguments)
    case telemetryArguments: Telemetry.Arguments => Telemetry(telemetryArguments)
  }

}
