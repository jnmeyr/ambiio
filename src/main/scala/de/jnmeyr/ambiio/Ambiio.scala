package de.jnmeyr.ambiio

import de.jnmeyr.ambiio.Controller.Command
import cats.effect.concurrent.Ref
import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._

import scala.language.postfixOps

object Ambiio extends IOApp {

  def run(args: List[String]): IO[ExitCode] = Arguments(args).fold(IO.pure(ExitCode.Error)) { arguments =>
    val controller = Controller[IO](arguments.controller)
    val consumers = arguments.consumers.map(Consumer(_))

    def runOnce(producer: Producer[IO])
               (produce: Produce[IO]): IO[Unit] = producer(produce, IO.pure(true))

    def runForever(producer: Producer[IO])
                  (produce: Produce[IO]): IO[Unit] = for {
      untilRef <- Ref.of[IO, Boolean](false)
      producerFib <- producer(produce, untilRef.get).start
      command <- controller.get
      _ <- untilRef.set(true)
      _ <- producerFib.join
      _ <- command match {
        case Command.Frequencies(everyOpt, inOpt) =>
          runForever(Producer.frequencies(everyOpt, inOpt))(produce)
        case Command.Glow(inOpt) =>
          runForever(Producer.glow(inOpt))(produce)
        case Command.Loudness(everyOpt, inOpt) =>
          runForever(Producer.loudness(everyOpt, inOpt))(produce)
        case Command.Pulse(everyOpt, inOpt) =>
          runForever(Producer.pulse(everyOpt, inOpt))(produce)
        case Command.Pause =>
          runForever(Producer.pause)(produce)
        case Command.Stop | _ =>
          runOnce(Producer.pause)(produce)
      }
    } yield ()

    for {
      bridge <- Bridge.Limited()
      consumerFibs <- consumers.map(consumer => bridge.consume.flatMap(consumer).start).sequence
      _ <- bridge.produce.flatMap(runForever(Producer.glow(Some(Pixel.grey(0.1)))))
      _ <- consumerFibs.map(_.cancel).sequence_
    } yield ExitCode.Success
  }

}
