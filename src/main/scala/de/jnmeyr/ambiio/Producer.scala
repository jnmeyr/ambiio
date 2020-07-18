package de.jnmeyr.ambiio

import cats.effect.{IO, Timer}
import cats.implicits._
import org.slf4j.LoggerFactory

import scala.concurrent.duration.{FiniteDuration, _}

object Producer {

  private val logger = LoggerFactory.getLogger("Producer")

  private def runEvery(every: FiniteDuration)
                      (runs: List[IO[Unit]])
                      (implicit timer: Timer[IO]): IO[Unit] = runs.map(_ *> IO.sleep(every)).sequence_

  private def runUntil(until: IO[Boolean])
                      (run: IO[Unit]): IO[Unit] = for {
    _ <- run
    done <- until
    _ <- if (done) IO.pure(()) else runUntil(until)(run)
  } yield ()

  private def runEveryUntil(every: FiniteDuration,
                            until: IO[Boolean])
                           (run: IO[Unit])
                           (implicit timer: Timer[IO]): IO[Unit] = for {
    _ <- run
    _ <- IO.sleep(every)
    done <- until
    _ <- if (done) IO.pure(()) else runEveryUntil(every, until)(run)
  } yield ()

  def frequencies(everyOpt: Option[FiniteDuration] = None,
                  inOpt: Option[Pixel])
                 (implicit timer: Timer[IO]): Producer[IO] = (produce: Produce[IO], until: IO[Boolean]) => {
    logger.info(s"Frequencies${everyOpt.fold("")(every => s" every $every")}${inOpt.fold("")(in => s" in $in")}")

    val aggregator = Sampler.Frequencies.Aggregator.Exponential(4)

    def run(sampler: Sampler[IO]): IO[Unit] = runEveryUntil(everyOpt.getOrElse(10 milliseconds), until)(for {
      samples <- sampler.getSamples
      frequencies = Sampler.getFrequencies(samples).apply(aggregator.aggregate)
      _ <- produce(Bridge.Values.Multiple(frequencies.mono, inOpt))
    } yield ())

    Sampler.Audio[IO]().use(run)
  }

  def glow(inOpt: Option[Pixel]): Producer[IO] = (produce: Produce[IO], _) => {
    logger.info(s"Glow${inOpt.fold("")(in => s" in $in")}")

    produce(Bridge.Values.Single(1.0, inOpt))
  }

  def loudness(everyOpt: Option[FiniteDuration] = None,
               inOpt: Option[Pixel])
              (implicit timer: Timer[IO]): Producer[IO] = (produce: Produce[IO], until: IO[Boolean]) => {
    logger.info(s"Loudness${everyOpt.fold("")(every => s" every $every")}${inOpt.fold("")(in => s" in $in")}")

    def run(sampler: Sampler[IO]): IO[Unit] = runEveryUntil(everyOpt.getOrElse(10 milliseconds), until)(for {
      samples <- sampler.getSamples
      Sampler.Channels(Seq(left), Seq(right)) = Sampler.getLoudness(samples)
      _ <- produce(Bridge.Values.Pair(left, right, inOpt))
    } yield ())

    Sampler.Audio[IO]().use(run)
  }

  val pause: Producer[IO] = (produce: Produce[IO], _) => {
    logger.info("Pause")

    produce(Bridge.Values.Single(0.0))
  }

  def pulse(everyOpt: Option[FiniteDuration] = None,
            inOpt: Option[Pixel])
           (implicit timer: Timer[IO]): Producer[IO] = (produce: Produce[IO], until: IO[Boolean]) => {
    logger.info(s"Pulse${everyOpt.fold("")(every => s" every $every")}${inOpt.fold("")(in => s" in $in")}")

    val runs: List[IO[Unit]] = {
      val xs: List[Double] = Range(0, 100).map(_ / 100.0).toList
      val ys: List[Double] = xs.reverse
      val zs: List[Double] = xs ++ ys
      zs.map(z => produce(Bridge.Values.Single(z, inOpt)))
    }

    runUntil(until)(runEvery(everyOpt.getOrElse(25 milliseconds))(runs))
  }

}
