package de.jnmeyr.ambiio

import java.net.{DatagramPacket, DatagramSocket, SocketAddress}

import cats.effect.{Resource, Sync}
import gnu.io.CommPortIdentifier
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.eclipse.paho.client.mqttv3.{MqttClient, MqttClientPersistence, MqttConnectOptions}
import org.openmuc.jrxtx.{SerialPort, SerialPortBuilder}

trait Tape[F[_]] {

  def unset: F[Unit]

  def set(pixel: Pixel): F[Unit]

  def set(pixels: Vector[Pixel]): F[Unit]

}

object Tape {

  private val UNSET = Array[Byte](0x75, 0x41)
  private val SET_PIXEL = Array[Byte](0x73, 0x41)
  private val SET_PIXELS = Array[Byte](0x73, 0x4F)

  class Serial[F[_] : Sync] private(port: SerialPort) extends Tape[F] {

    private def write(bytes: Array[Byte]): F[Unit] = Sync[F].delay {
      port.getOutputStream.write(bytes)
    }

    override val unset: F[Unit] = write(UNSET)

    override def set(pixel: Pixel): F[Unit] = write(SET_PIXEL ++ pixel.toBytes)

    override def set(pixels: Vector[Pixel]): F[Unit] = write(SET_PIXELS ++ pixels.flatMap(_.toBytes))

    private val close: F[Unit] = Sync[F].delay {
      port.close()
    }

  }

  object Serial {

    def apply[F[_] : Sync](name: String): Resource[F, Serial[F]] = {
      val acquire: F[Serial[F]] = Sync[F].delay {
        val ports = CommPortIdentifier.getPortIdentifiers
        while (ports.hasMoreElements) {
          ports.nextElement()
        }
        val port = SerialPortBuilder
          .newBuilder(name)
          .setBaudRate(115200)
          .build()
        new Serial(port)
      }

      def release(serial: Serial[F]): F[Unit] = serial.close

      Resource.make(acquire)(release)
    }

  }

  class Socket[F[_] : Sync] private(socket: DatagramSocket, address: SocketAddress) extends Tape[F] {

    private def send(bytes: Array[Byte]): F[Unit] = Sync[F].delay {
      socket.send(new DatagramPacket(bytes, bytes.length, address))
    }

    override val unset: F[Unit] = send(UNSET)

    override def set(pixel: Pixel): F[Unit] = send(SET_PIXEL ++ pixel.toBytes)

    override def set(pixels: Vector[Pixel]): F[Unit] = send(SET_PIXELS ++ pixels.flatMap(_.toBytes))

    private val close: F[Unit] = Sync[F].delay {
      socket.close()
    }

  }

  object Socket {

    def apply[F[_] : Sync](address: SocketAddress): Resource[F, Socket[F]] = {
      val acquire: F[Socket[F]] = Sync[F].delay {
        val socket = new DatagramSocket()
        new Socket(socket, address)
      }

      def release(socket: Socket[F]): F[Unit] = socket.close

      Resource.make(acquire)(release)
    }

  }

  class Telemetry[F[_] : Sync] private(client: MqttClient,
                                       topic: String) extends Tape[F] {

    private def publish(bytes: Array[Byte]): F[Unit] = Sync[F].delay {
      client.publish(topic, bytes, 0, false)
    }

    override val unset: F[Unit] = publish(UNSET)

    override def set(pixel: Pixel): F[Unit] = publish(SET_PIXEL ++ pixel.toBytes)

    override def set(pixels: Vector[Pixel]): F[Unit] = publish(SET_PIXELS ++ pixels.flatMap(_.toBytes))

    private val close: F[Unit] = Sync[F].delay {
      client.disconnect()
      client.close()
    }

  }

  object Telemetry {

    private val persistence: MqttClientPersistence = new MemoryPersistence()

    def apply[F[_] : Sync](server: String, topic: String): Resource[F, Telemetry[F]] = {
      val acquire: F[Telemetry[F]] = Sync[F].delay {
        val client = new MqttClient(server, MqttClient.generateClientId(), persistence)
        val options = new MqttConnectOptions()
        options.setCleanSession(true)
        client.connect(options)
        new Telemetry(client, topic)
      }

      def release(telemetry: Telemetry[F]): F[Unit] = telemetry.close

      Resource.make(acquire)(release)
    }

  }

}
