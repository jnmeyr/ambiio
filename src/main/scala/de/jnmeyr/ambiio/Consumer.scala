package de.jnmeyr.ambiio

import java.net.{InetSocketAddress, SocketAddress}

import cats.effect.{IO, Timer}

import scala.concurrent.duration.{FiniteDuration, _}
import scala.language.postfixOps

object Consumer {

  sealed trait Arguments {

    def pixels: Int

    def every: FiniteDuration

  }

  object Printer {

    case class Arguments(override val pixels: Int,
                         override val every: FiniteDuration = 0 millis) extends Consumer.Arguments

    def apply(printer: Printer.Arguments)
             (implicit timer: Timer[IO]): Consumer[IO] = (consume: Consume[IO]) => {
      def run(tape: Tape[IO]): IO[Unit] = for {
        values <- consume
        _ <- tape.set(values.toPixels(printer.pixels))
        _ <- IO.sleep(printer.every)
        _ <- run(tape)
      } yield ()

      run(Tape.Printer())
    }

  }

  object Serial {

    case class Arguments(override val pixels: Int,
                         override val every: FiniteDuration = 25 millis,
                         name: String = "") extends Consumer.Arguments

    def apply(serial: Serial.Arguments)
             (implicit timer: Timer[IO]): Consumer[IO] = (consume: Consume[IO]) => {
      def run(tape: Tape[IO]): IO[Unit] = for {
        values <- consume
        _ <- tape.set(values.toPixels(serial.pixels))
        _ <- IO.sleep(serial.every)
        _ <- run(tape)
      } yield ()

      Tape.Serial[IO](serial.name).use(run)
    }

  }

  object Socket {

    case class Arguments(override val pixels: Int,
                         override val every: FiniteDuration = 25 millis,
                         host: String = "",
                         port: Int = 0) extends Consumer.Arguments {

      lazy val address: SocketAddress = new InetSocketAddress(host, port)

    }

    def apply(socket: Socket.Arguments)
             (implicit timer: Timer[IO]): Consumer[IO] = (consume: Consume[IO]) => {
      def run(tape: Tape[IO]): IO[Unit] = for {
        values <- consume
        _ <- tape.set(values.toPixels(socket.pixels))
        _ <- IO.sleep(socket.every)
        _ <- run(tape)
      } yield ()

      Tape.Socket[IO](socket.address).use(run)
    }

  }

  def apply(arguments: Arguments)
           (implicit timer: Timer[IO]): Consumer[IO] = {
    arguments match {
      case printer: Printer.Arguments => Printer(printer)
      case serial: Serial.Arguments => Serial(serial)
      case socket: Socket.Arguments => Socket(socket)
    }
  }

}
