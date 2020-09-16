package de.jnmeyr.ambiio

trait Ambiio {

  import cats.effect.{Concurrent, ConcurrentEffect, ContextShift, Timer}
  import cats.implicits._

  import scala.concurrent.duration._

  protected def program[F[_] : ConcurrentEffect](arguments: Arguments)
                                                (implicit contextShift: ContextShift[F],
                                                 timer: Timer[F]): F[Unit] = {
    def startController(arguments: Controller.Arguments,
                        bridge: Bridge[F, Values]): F[Controller[F]] = {
      Controller(arguments, bridge.timeout)
    }

    def startProducer(controller: Controller[F],
                      bridge: Bridge[F, Values]): F[Unit] = {
      def run(controller: Controller[F],
              command: Command,
              produce: Produce[F, Values]): F[Unit] = {
        val producer = Producer[F](command)
        for {
          producerFib <- Concurrent[F].start(producer(produce))
          command <- controller.getNextCommand
          _ <- producerFib.cancel
          _ <- run(controller, command, produce)
        } yield ()
      }

      for {
        command <- controller.getLastCommand
        produce <- bridge.produce
        _ <- run(controller, command, produce)
      } yield ()
    }

    def startConsumer(arguments: Consumer.Arguments,
                      bridge: Bridge[F, Values]): F[Unit] = {
      bridge.consume.flatMap { case (consume, stop) =>
        val consumer = Consumer[F](arguments)
        val restart = stop *> timer.sleep(1 seconds) *> startConsumer(arguments, bridge)
        consumer(consume, restart)
      }
    }

    for {
      bridge <- Bridge[F, Values]()
      controller <- startController(arguments.controller, bridge)
      consumerFibs <- arguments.consumers.map(arguments => Concurrent[F].start(startConsumer(arguments, bridge))).sequence
      _ <- startProducer(controller, bridge)
      _ <- consumerFibs.map(_.cancel).sequence_
    } yield ()
  }

}

object EffectAmbiio
  extends cats.effect.IOApp
    with Ambiio {

  import cats.effect.{ExitCode, IO}

  def run(args: List[String]): IO[ExitCode] = {
    Arguments(args).fold(IO.pure(ExitCode.Error)) { arguments =>
      program[IO](arguments).map(_ => ExitCode.Success)
    }
  }

}

object ZioAmbiio
  extends zio.interop.catz.CatsApp
    with Ambiio {

  import zio._
  import zio.interop.catz._

  override def run(args: List[String]): URIO[ZEnv, ExitCode] = {
    Arguments(args).fold[URIO[ZEnv, ExitCode]](ZIO.succeed(ExitCode.failure)) { arguments =>
      program(arguments).exitCode
    }
  }

}
