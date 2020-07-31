package de.jnmeyr.ambiio

import cats.effect.{Resource, Sync}
import cats.implicits._
import cats.{Functor, Id}
import javax.sound.sampled._
import org.apache.commons.collections4.queue.CircularFifoQueue
import org.apache.commons.math3.transform.{DftNormalization, FastFourierTransformer, TransformType}

import scala.jdk.CollectionConverters._

sealed trait Sampler[F[_]] {

  import Sampler._

  def getSamples: F[Channels[Vector, Int]]

}

object Sampler {

  import Ordering.Double.IeeeOrdering

  private val RATE = 44100f
  private val BYTES = 2
  private val BITS = 8 * BYTES
  private val CHANNELS = 2
  private val SIGNED = true
  private val BIG_ENDIAN: Boolean = true
  private val SAMPLES: Int = 1024
  private val LENGTH: Int = BYTES * CHANNELS * SAMPLES

  case class Channels[C[_] : Functor, T] private(left: C[T], right: C[T]) {

    def apply[R](f: C[T] => C[R]): Channels[C, R] = Channels[C, R](f(left), f(right))

    def map[R](f: T => R): Channels[C, R] = Channels[C, R](Functor[C].map(left)(f), Functor[C].map(right)(f))

    def left(f: T => T): Channels[C, T] = Channels[C, T](Functor[C].map(left)(f), right)

    def right(f: T => T): Channels[C, T] = Channels[C, T](left, Functor[C].map(right)(f))

    def partition[R](f: C[T] => R): (R, R) = (f(left), f(right))

  }

  object Channels {

    def apply[T](channels: Vector[T]): Channels[Vector, T] = {
      require(channels.length % 2 == 0)
      val left = Vector.range(0, channels.length, 2).map(channels)
      val right = Vector.range(1, channels.length, 2).map(channels)
      new Channels(left, right)
    }

    def apply[T](left: Vector[T], right: Vector[T]): Channels[Vector, T] = {
      require(left.length == right.length)
      new Channels(left, right)
    }

  }

  object Frequencies {

    private val transformer: FastFourierTransformer = new FastFourierTransformer(DftNormalization.STANDARD)

    def getFrequencies(samples: Vector[Int]): Array[Double] = {
      val frequencies = transformer.transform(samples.map(_.toDouble).toArray, TransformType.FORWARD).take(samples.length / 2).map(_.abs)
      val max = frequencies.max
      if (max > 0) frequencies.map(_ / max) else frequencies
    }

    sealed trait Aggregator {

      def aggregate(frequencies: Vector[Double]): Vector[Double]

    }

    object Aggregator {

      case class Linear(steps: Int) extends Aggregator {

        private val slide: Int = SAMPLES / CHANNELS / steps

        override def aggregate(frequencies: Vector[Double]): Vector[Double] = frequencies.sliding(slide, slide).map(_.max).toVector

      }

      case class Exponential(times: Int) extends Aggregator {

        private val slices: Vector[(Int, Int)] = {
          val buckets = Math.floor(Math.log10(SAMPLES / CHANNELS / times) / Math.log10(2.0)).toInt
          val size = Vector.range(0, buckets).flatMap(bucket => Range(0, times).map(_ => Math.pow(2, bucket).toInt))
          Vector.range(0, times * buckets).map(size.take(_).sum).zip(size).map { case (from, size) =>
            (from, from + size)
          }
        }

        override def aggregate(frequencies: Vector[Double]): Vector[Double] = slices.map { case (from, until) =>
          frequencies.slice(from, until).max
        }

      }

    }

  }

  object Loudness {

    private val MAXIMUM: Int = Math.pow(2, BITS - 1).toInt

    def getAverage(samples: Vector[Int]): Double = {
      Math.min(Math.max(samples.map(_.abs).sum.toDouble / (MAXIMUM * samples.length), 0), 1)
    }

    def getMaximum(samples: Vector[Int]): Double = {
      Math.min(Math.max(samples.map(_.abs).max.toDouble / MAXIMUM, 0), 1)
    }

  }

  def getFrequencies(samples: Channels[Vector, Int]): Channels[Vector, Double] = {
    val (leftLoudness, rightLoudness) = samples.partition(Loudness.getAverage)
    samples
      .apply(Frequencies.getFrequencies(_).toVector)
      .left(_ * leftLoudness)
      .right(_ * rightLoudness)
  }

  def getLoudness(samples: Channels[Vector, Int]): Channels[Id, Double] = {
    val (leftLoudness: Id[Double], rightLoudness: Id[Double]) = samples.partition(Loudness.getAverage)
    Channels(leftLoudness, rightLoudness)
  }

  class Audio[F[_] : Sync] private(targetDataLine: TargetDataLine,
                                   audioInputStream: AudioInputStream) extends Sampler[F] {

    import Audio._

    private val buffer: CircularFifoQueue[Byte] = new CircularFifoQueue[Byte](LENGTH)

    override val getSamples: F[Channels[Vector, Int]] = Sync[F].delay {
      audioInputStream.skip(audioInputStream.available() - LENGTH)
      var bytes = new Array[Byte](audioInputStream.available())
      audioInputStream.read(bytes)
      bytes.foreach(buffer.add)
      bytes = if (buffer.isAtFullCapacity) buffer.iterator().asScala.toArray else emptyBytes
      val samples = bytes.sliding(BYTES, BYTES).map(bytes => (bytes(0) << 8) + (bytes(1) << 0)).toVector
      Channels(samples)
    }

    private val close: F[Unit] = Sync[F].delay {
      audioInputStream.close()
      targetDataLine.stop()
      targetDataLine.close()
    }

  }

  object Audio {

    private val audioFormat: AudioFormat = new AudioFormat(RATE, BITS, CHANNELS, SIGNED, BIG_ENDIAN)
    private val emptyBytes: Array[Byte] = new Array[Byte](BYTES * CHANNELS * SAMPLES)

    def apply[F[_] : Sync](): Resource[F, Audio[F]] = {
      val acquire: F[Audio[F]] = Sync[F].delay {
        val dataLineInfo = new DataLine.Info(classOf[TargetDataLine], audioFormat)
        val targetDataLine = AudioSystem.getLine(dataLineInfo).asInstanceOf[TargetDataLine]
        val audioInputStream = new AudioInputStream(targetDataLine)
        targetDataLine.open(audioFormat)
        targetDataLine.start()
        new Audio[F](targetDataLine, audioInputStream)
      }

      def release(audio: Audio[F]): F[Unit] = audio.close

      Resource.make(acquire)(release)
    }

  }

}
