package de.jnmeyr.ambiio

import cats.effect.{ContextShift, IO}
import cats.effect.concurrent.MVar
import cats.implicits._

sealed trait Bridge {

  def consume: IO[Consume[IO]]

  def produce: IO[Produce[IO]]

}

object Bridge {

  def worker(lastOpt: Option[Values] = None)
            (from: MVar[IO, Values],
             to: MVar[IO, Values]): IO[Unit] = for {
    values <- from.take
    _ <- if (lastOpt.contains(values)) IO.pure(()) else to.put(values)
    _ <- worker(Some(values))(from, to)
  } yield ()


  class Limited private(from: MVar[IO, Values])
                       (implicit contextShift: ContextShift[IO]) extends Bridge {

    override def consume: IO[Consume[IO]] = for {
      to <- MVar.empty[IO, Values]
      _ <- worker()(from, to).start
    } yield to.take

    override val produce: IO[Produce[IO]] = IO.delay { values: Values =>
      from.tryTake >> from.put(values)
    }

  }

  object Limited {

    def apply()
             (implicit contextShift: ContextShift[IO]): IO[Limited] = MVar.empty[IO, Values].map(new Limited(_))

  }

  sealed trait Values {

    def toPixels(pixels: Int): Seq[Pixel]

  }

  object Values {

    case class Single(pixel: Pixel) extends Values {

      override def toPixels(pixels: Int): Seq[Pixel] = {
        Range(0, pixels).map(_ => pixel)
      }

    }

    object Single {

      def apply(value: Double,
                inOpt: Option[Pixel] = None): Single = new Single(inOpt.getOrElse(Pixel.white) * value)

    }

    case class Pair(left: Pixel,
                    right: Pixel) extends Values {

      override def toPixels(pixels: Int): Seq[Pixel] = {
        Range(0, pixels / 2).map(_ => left) ++ Range(0, pixels / 2).map(_ => right)
      }

    }

    object Pair {

      def apply(left: Double,
                right: Double,
                inOpt: Option[Pixel]): Pair = new Pair(inOpt.getOrElse(Pixel.white) * left, inOpt.getOrElse(Pixel.white) * right)

    }

    case class Multiple(pixels: Seq[Pixel]) extends Values {

      override def toPixels(pixels: Int): Seq[Pixel] = {
        def decreased: Seq[Pixel] = {
          import Pixel.orderingByBrightness

          val of = (this.pixels.size / pixels.toFloat).toInt
          val by = this.pixels.size - (of * pixels)
          this.pixels.slice(by / 2, this.pixels.size - by / 2).sliding(of, of).map(_.max).toSeq
        }

        def increased: Seq[Pixel] = {
          val by = pixels - this.pixels.size
          val (start, end) = Range(0, by).map(_ => Pixel.black).splitAt(by / 2)
          start ++ this.pixels ++ end
        }

        if (this.pixels.size > pixels) decreased
        else if (this.pixels.size < pixels) increased
        else this.pixels
      }

    }

    object Multiple {

      def apply(values: Seq[Double],
                inOpt: Option[Pixel]): Multiple = new Multiple(values.map(inOpt.getOrElse(Pixel.white) * _))

    }

  }

}
