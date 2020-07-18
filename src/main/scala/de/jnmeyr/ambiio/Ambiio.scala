package de.jnmeyr.ambiio

import de.jnmeyr.ambiio.Controller.Command
import cats.effect.concurrent.Ref
import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._

object Ambiio extends IOApp {

  private def runOnce(producer: Producer[IO])
                     (produce: Produce[IO]): IO[Unit] = producer(produce, IO.pure(true))

  private def runForever(producer: Producer[IO])
                        (control: IO[Command],
                         produce: Produce[IO]): IO[Unit] = for {
    untilRef <- Ref.of[IO, Boolean](false)
    producerFib <- producer(produce, untilRef.get).start
    command <- control
    _ <- untilRef.set(true)
    _ <- producerFib.join
    _ <- command match {
      case Command.Frequencies(everyOpt, inOpt) =>
        runForever(Producer.frequencies(everyOpt, inOpt))(control, produce)
      case Command.Glow(inOpt) =>
        runForever(Producer.glow(inOpt))(control, produce)
      case Command.Loudness(everyOpt, inOpt) =>
        runForever(Producer.loudness(everyOpt, inOpt))(control, produce)
      case Command.Pulse(everyOpt, inOpt) =>
        runForever(Producer.pulse(everyOpt, inOpt))(control, produce)
      case Command.Pause =>
        runForever(Producer.pause)(control, produce)
      case Command.Stop =>
        runOnce(Producer.pause)(produce)
    }
  } yield ()

  def run(args: List[String]): IO[ExitCode] = Arguments(args).fold(IO.pure(ExitCode.Error)) { arguments =>
    for {
      controller <- Controller(arguments.controller)
      bridge <- Bridge.Limited()
      consumerFibs <- arguments.consumers.map(Consumer(_)).map(consumer => bridge.consume.flatMap(consumer).start).sequence
      _ <- bridge.produce.flatMap(runForever(Producer.glow(Some(Pixel.grey(0.1))))(controller.get, _))
      _ <- consumerFibs.map(_.cancel).sequence_
    } yield ExitCode.Success
  }

}
