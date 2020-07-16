package de.jnmeyr.ambiio

import java.net.{DatagramPacket, DatagramSocket, SocketAddress}

import cats.effect.{Resource, Sync}
import org.openmuc.jrxtx.{SerialPort, SerialPortBuilder}

import scala.language.postfixOps

trait Tape[F[_]] {

  def unset: F[Unit]

  def set(pixel: Pixel): F[Unit]

  def set(pixels: Seq[Pixel]): F[Unit]

}

object Tape {

  private val UNSET = Array[Byte](0x75, 0x41)
  private val SET_PIXEL = Array[Byte](0x73, 0x41)
  private val SET_PIXELS = Array[Byte](0x73, 0x4F)

  class Printer[F[_] : Sync] private extends Tape[F] {

    private def format(pixel: Pixel): String = s"${pixel.red},${pixel.green},${pixel.blue}"

    override def unset: F[Unit] = Sync[F].delay(())

    override def set(pixel: Pixel): F[Unit] = Sync[F].delay(println(format(pixel)))

    override def set(pixels: Seq[Pixel]): F[Unit] = Sync[F].delay(println(pixels.map(format).mkString(" ")))

  }

  object Printer {

    def apply[F[_] : Sync](): Printer[F] = new Printer[F]()

  }

  class Serial[F[_] : Sync] private(port: SerialPort) extends Tape[F] {

    private def write(bytes: Array[Byte]): F[Unit] = Sync[F].delay {
      port.getOutputStream.write(bytes)
    }

    override val unset: F[Unit] = write(UNSET)

    override def set(pixel: Pixel): F[Unit] = write(SET_PIXEL ++ pixel.toBytes)

    override def set(pixels: Seq[Pixel]): F[Unit] = write(SET_PIXELS ++ pixels.flatMap(_.toBytes))

    private def close(): F[Unit] = Sync[F].delay {
      port.close()
    }

  }

  object Serial {

    def apply[F[_] : Sync](name: String): Resource[F, Serial[F]] = {
      val acquire: F[Serial[F]] = Sync[F].delay {
        val port = SerialPortBuilder
          .newBuilder(name)
          .setBaudRate(115200)
          .build()
        new Serial(port)
      }

      def release(serial: Serial[F]): F[Unit] = serial.close()

      Resource.make(acquire)(release)
    }

  }

  class Socket[F[_] : Sync] private(socket: DatagramSocket, address: SocketAddress) extends Tape[F] {

    private def send(buffer: Array[Byte]): F[Unit] = Sync[F].delay {
      socket.send(new DatagramPacket(buffer, buffer.length, address))
    }

    override val unset: F[Unit] = send(UNSET)

    override def set(pixel: Pixel): F[Unit] = send(SET_PIXEL ++ pixel.toBytes)

    override def set(pixels: Seq[Pixel]): F[Unit] = send(SET_PIXELS ++ pixels.flatMap(_.toBytes))

    private def close(): F[Unit] = Sync[F].delay {
      socket.close()
    }

  }

  object Socket {

    def apply[F[_] : Sync](address: SocketAddress): Resource[F, Socket[F]] = {
      val acquire: F[Socket[F]] = Sync[F].delay {
        val socket = new DatagramSocket()
        new Socket(socket, address)
      }

      def release(socket: Socket[F]): F[Unit] = socket.close()

      Resource.make(acquire)(release)
    }

  }

}
