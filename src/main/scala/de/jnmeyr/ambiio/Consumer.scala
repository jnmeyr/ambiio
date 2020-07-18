package de.jnmeyr.ambiio

import java.net.{InetSocketAddress, SocketAddress}

import cats.effect.{IO, Timer}
import org.slf4j.LoggerFactory

import scala.concurrent.duration.{FiniteDuration, _}

object Consumer {

  private val logger = LoggerFactory.getLogger("Consumer")

  sealed trait Arguments {

    def pixels: Int

    def every: FiniteDuration

  }

  object Printer {

    case class Arguments(override val pixels: Int,
                         override val every: FiniteDuration = 0 millis) extends Consumer.Arguments

    def apply(arguments: Arguments)
             (implicit timer: Timer[IO]): Consumer[IO] = (consume: Consume[IO]) => {
      logger.info(s"Printer with $arguments")

      def run(print: Seq[Pixel] => IO[Unit]): IO[Unit] = for {
        values <- consume
        _ <- print(values.toPixels(arguments.pixels))
        _ <- IO.sleep(arguments.every)
        _ <- run(print)
      } yield ()

      run(pixels => IO.delay(println(Pixel.toString(pixels))))
    }

  }

  object Serial {

    case class Arguments(override val pixels: Int,
                         override val every: FiniteDuration = 25 millis,
                         name: String = "") extends Consumer.Arguments

    def apply(arguments: Arguments)
             (implicit timer: Timer[IO]): Consumer[IO] = (consume: Consume[IO]) => {
      logger.info(s"Serial with $arguments")

      def run(tape: Tape[IO]): IO[Unit] = for {
        values <- consume
        _ <- tape.set(values.toPixels(arguments.pixels))
        _ <- IO.sleep(arguments.every)
        _ <- run(tape)
      } yield ()

      Tape.Serial[IO](arguments.name).use(run)
    }

  }

  object Socket {

    case class Arguments(override val pixels: Int,
                         override val every: FiniteDuration = 25 millis,
                         host: String = "",
                         port: Int = 0) extends Consumer.Arguments {

      lazy val address: SocketAddress = new InetSocketAddress(host, port)

    }

    def apply(arguments: Arguments)
             (implicit timer: Timer[IO]): Consumer[IO] = (consume: Consume[IO]) => {
      logger.info(s"Socket with $arguments")

      def run(tape: Tape[IO]): IO[Unit] = for {
        values <- consume
        _ <- tape.set(values.toPixels(arguments.pixels))
        _ <- IO.sleep(arguments.every)
        _ <- run(tape)
      } yield ()

      Tape.Socket[IO](arguments.address).use(run)
    }

  }

  def apply(arguments: Arguments)
           (implicit timer: Timer[IO]): Consumer[IO] = arguments match {
    case printerArguments: Printer.Arguments => Printer(printerArguments)
    case serialArguments: Serial.Arguments => Serial(serialArguments)
    case socketArguments: Socket.Arguments => Socket(socketArguments)
  }

}
