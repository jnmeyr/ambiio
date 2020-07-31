package de.jnmeyr.ambiio

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import de.jnmeyr.ambiio.Controller.Command

import scala.concurrent.duration._

object Ambiio extends IOApp {

  def run(args: List[String]): IO[ExitCode] = {

    def startProducer(controller: Controller[IO],
                      bridge: Bridge[IO, Values]): IO[Unit] = {
      def run(controller: Controller[IO],
              command: Command,
              produce: Produce[IO, Values]): IO[Unit] = {
        val producer = Producer[IO](command)
        for {
          producerFib <- producer(produce).start
          command <- controller()
          _ <- producerFib.cancel
          _ <- run(controller, command, produce)
        } yield ()
      }

      for {
        command <- controller()
        produce <- bridge.produce
        _ <- run(controller, command, produce)
      } yield ()
    }

    def startConsumer(arguments: Consumer.Arguments,
                      bridge: Bridge[IO, Values]): IO[Unit] = {
      bridge.consume.flatMap { case (consume, stop) =>
        val consumer = Consumer[IO](arguments)
        val restart = stop *> timer.sleep(1 seconds) *> startConsumer(arguments, bridge)
        consumer(consume, restart)
      }
    }

    Arguments(args).fold(IO.pure(ExitCode.Error)) { arguments =>
      for {
        controller <- Controller(arguments.controller)
        bridge <- Bridge.Limited[IO, Values]
        consumerFibs <- arguments.consumers.map(startConsumer(_, bridge).start).sequence
        _ <- startProducer(controller, bridge)
        _ <- consumerFibs.map(_.cancel).sequence_
      } yield ExitCode.Success
    }
  }

}
