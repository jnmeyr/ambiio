package de.jnmeyr.ambiio

import cats.effect.{ConcurrentEffect, ContextShift, Sync, Timer}
import cats.implicits._
import org.slf4j.LoggerFactory

import scala.concurrent.duration.{FiniteDuration, _}

object Producer {

  private val logger = LoggerFactory.getLogger("Producer")

  private def log[F[_] : Sync](result: Either[Throwable, Unit]): F[Unit] = Sync[F].delay {
    result.left.map { throwable =>
      logger.error(s"Producer crashed: $throwable")
    }
  }

  def frequencies[F[_] : Sync](everyOpt: Option[FiniteDuration],
                               inOpt: Option[Pixel])
                              (implicit timer: Timer[F]): Producer[F, Values] = produce => {
    logger.info(s"Frequencies${everyOpt.fold("")(every => s" every $every")}${inOpt.fold("")(in => s" in $in")}")

    val aggregator = Sampler.Frequencies.Aggregator.Exponential(4)

    Sampler.Audio[F]().use(sampler =>
      runEvery(everyOpt.getOrElse(10 millis)) {
        for {
          samples <- sampler.getSamples
          Sampler.Channels(left, right) = Sampler.getFrequencies(samples)(aggregator.aggregate)
          _ <- produce(Values.Multiple(left.reverse ++ right, inOpt))
        } yield ()
      }
    ).attempt.flatMap(log(_))
  }

  def glow[F[_]](inOpt: Option[Pixel]): Producer[F, Values] = produce => {
    logger.info(s"Glow${inOpt.fold("")(in => s" in $in")}")

    produce(Values.Single(1.0, inOpt))
  }

  def loudness[F[_] : Sync](everyOpt: Option[FiniteDuration],
                            inOpt: Option[Pixel])
                           (implicit timer: Timer[F]): Producer[F, Values] = produce => {
    logger.info(s"Loudness${everyOpt.fold("")(every => s" every $every")}${inOpt.fold("")(in => s" in $in")}")

    Sampler.Audio[F]().use(sampler =>
      runEvery(everyOpt.getOrElse(10 millis)) {
        for {
          samples <- sampler.getSamples
          Sampler.Channels(left, right) = Sampler.getLoudness(samples)
          _ <- produce(Values.Pair(left, right, inOpt))
        } yield ()
      }
    ).attempt.flatMap(log(_))
  }

  def pause[F[_]]: Producer[F, Values] = produce => {
    logger.info("Pause")

    produce(Values.Single(0.0, None))
  }

  def pulse[F[_] : Sync](everyOpt: Option[FiniteDuration],
                         inOpt: Option[Pixel])
                        (implicit timer: Timer[F]): Producer[F, Values] = produce => {
    logger.info(s"Pulse${everyOpt.fold("")(every => s" every $every")}${inOpt.fold("")(in => s" in $in")}")

    run {
      {
        val xs: List[Double] = Range(0, 100).map(_ / 100.0).toList
        val ys: List[Double] = xs.reverse
        val zs: List[Double] = xs ++ ys
        zs.map(z => produce(Values.Single(z, inOpt)))
      }.map(_ *> timer.sleep(everyOpt.getOrElse(25 millis))).sequence_
    }.attempt.flatMap(log(_))
  }

  def telemetry[F[_] : ConcurrentEffect](server: String,
                                         topic: String)
                                        (implicit contextShift: ContextShift[F]): Producer[F, Values] = produce => {
    logger.info(s"Telemetry of $topic from $server")

    Source.Telemetry(server, topic).use(telemetry =>
      run {
        telemetry.getValues.flatMap(produce)
      }
    ).attempt.flatMap(log(_))
  }

  def apply[F[_] : ConcurrentEffect](command: Command)
                                    (implicit contextShift: ContextShift[F],
                                     timer: Timer[F]): Producer[F, Values] = command match {
    case Command.Frequencies(everyOpt, inOpt) => Producer.frequencies(everyOpt, inOpt)
    case Command.Glow(inOpt) => Producer.glow(inOpt)
    case Command.Loudness(everyOpt, inOpt) => Producer.loudness(everyOpt, inOpt)
    case Command.Pause => Producer.pause
    case Command.Pulse(everyOpt, inOpt) => Producer.pulse(everyOpt, inOpt)
    case Command.Telemetry(server, topic) => Producer.telemetry(server, topic)
  }

}
