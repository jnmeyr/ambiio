package de.jnmeyr.ambiio

import cats.effect.{Resource, Sync}
import javax.sound.sampled._
import org.apache.commons.collections4.queue.CircularFifoQueue
import org.apache.commons.math3.transform.{DftNormalization, FastFourierTransformer, TransformType}

import scala.jdk.CollectionConverters._
import scala.language.postfixOps

sealed trait Sampler[F[_]] {

  import Sampler._

  def getSamples: F[Channels[Int]]

}

object Sampler {

  private val RATE = 44100
  private val BYTES = 2
  private val BITS = 8 * BYTES
  private val CHANNELS = 2
  private val SIGNED = true
  private val BIG_ENDIAN: Boolean = true
  private val SAMPLES: Int = 1024
  private val LENGTH: Int = BYTES * CHANNELS * SAMPLES

  case class Channels[A] private(left: Seq[A], right: Seq[A]) {

    lazy val mono: Seq[A] = left.reverse ++ right

    def apply[B](f: Seq[A] => Seq[B]): Channels[B] = Channels[B](f(left), f(right))

    def map[B](f: A => B): Channels[B] = Channels[B](left.map(f), right.map(f))

    def left(f: A => A): Channels[A] = Channels[A](left.map(f), right)

    def right(f: A => A): Channels[A] = Channels[A](left, right.map(f))

    def partition[B](f: Seq[A] => B): (B, B) = (f(left), f(right))

  }

  object Channels {

    def apply[A](channels: Seq[A]): Channels[A] = {
      require(channels.length % 2 == 0)
      val left = Range(0, channels.length, 2).map(channels)
      val right = Range(1, channels.length, 2).map(channels)
      new Channels(left, right)
    }

    def apply[A](left: Seq[A], right: Seq[A]): Channels[A] = {
      require(left.length == right.length)
      new Channels(left, right)
    }

  }

  object Frequencies {

    private val transformer: FastFourierTransformer = new FastFourierTransformer(DftNormalization.STANDARD)

    def getFrequencies(samples: Seq[Int]): Array[Double] = {
      val frequencies = transformer.transform(samples.map(_.toDouble).toArray, TransformType.FORWARD).take(samples.length / 2).map(_.abs)
      val max = frequencies.max
      if (max > 0) frequencies.map(_ / max) else frequencies
    }

    sealed trait Aggregator {

      def aggregate(frequencies: Seq[Double]): Seq[Double]

    }

    object Aggregator {

      case class Linear(steps: Int) extends Aggregator {

        private val slide: Int = SAMPLES / CHANNELS / steps

        override def aggregate(frequencies: Seq[Double]): Seq[Double] = frequencies.sliding(slide, slide).map(_.max).toSeq

      }

      case class Exponential(times: Int) extends Aggregator {

        private val slices: Seq[(Int, Int)] = {
          val buckets = Math.floor(Math.log10(SAMPLES / CHANNELS / times) / Math.log10(2.0)).toInt
          val size = Range(0, buckets).flatMap(bucket => Range(0, times).map(_ => Math.pow(2, bucket).toInt))
          Range(0, times * buckets).map(size.take(_).sum).zip(size).map {
            case (from, size) =>
              (from, from + size)
          }
        }

        override def aggregate(frequencies: Seq[Double]): Seq[Double] = slices.map {
          case (from, until) =>
            frequencies.slice(from, until).max
        }

      }

    }

  }

  object Loudness {

    private val MAXIMUM: Int = Math.pow(2, BITS - 1).toInt

    def getAverage(samples: Seq[Int]): Double = {
      Math.min(Math.max(samples.map(_.abs).sum.toDouble / (MAXIMUM * samples.length), 0), 1)
    }

    def getMaximum(samples: Seq[Int]): Double = {
      Math.min(Math.max(samples.map(_.abs).max.toDouble / MAXIMUM, 0), 1)
    }

  }

  def getFrequencies(samples: Channels[Int]): Channels[Double] = {
    val (leftLoudness, rightLoudness) = samples.partition(Loudness.getAverage)
    samples
      .apply(Frequencies.getFrequencies)
      .left(_ * leftLoudness)
      .right(_ * rightLoudness)
  }

  def getLoudness(samples: Channels[Int]): Channels[Double] = {
    val (leftLoudness, rightLoudness) = samples.partition(Loudness.getAverage)
    Channels(Seq(leftLoudness), Seq(rightLoudness))
  }

  class Audio[F[_] : Sync] private(targetDataLine: TargetDataLine,
                                   audioInputStream: AudioInputStream) extends Sampler[F] {

    import Audio._

    private val buffer: CircularFifoQueue[Byte] = new CircularFifoQueue[Byte](LENGTH)

    override val getSamples: F[Channels[Int]] = Sync[F].delay {
      audioInputStream.skip(audioInputStream.available() - LENGTH)
      var bytes = new Array[Byte](audioInputStream.available())
      audioInputStream.read(bytes)
      bytes.foreach(buffer.add)
      bytes = if (buffer.isAtFullCapacity) buffer.iterator().asScala.toArray else emptyBytes
      val samples = bytes.sliding(BYTES, BYTES).map(bytes => (bytes(0) << 8) + (bytes(1) << 0)).toSeq
      Channels(samples)
    }

    private def close(): F[Unit] = Sync[F].delay {
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

      def release(audio: Audio[F]): F[Unit] = audio.close()

      Resource.make(acquire)(release)
    }

  }

}
