package de.jnmeyr.ambiio

import cats.effect.concurrent.MVar
import cats.effect.{ConcurrentEffect, ContextShift, Resource, Sync}
import cats.implicits._
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.eclipse.paho.client.mqttv3.{MqttClient, MqttClientPersistence, MqttConnectOptions, MqttMessage}

trait Source[F[_]] {

  def getValues: F[Values]

}

object Source {

  private def unapply(bytes: Array[Byte]): Option[Values] = bytes match {
    case Array(0x75, 0x41) => Some(Values.Single(Pixel.black))
    case Array(0x73, 0x41, bytes@_*) =>
      bytes match {
        case Pixel(Vector(pixel)) => Some(Values.Single(pixel))
        case _ => None
      }
    case Array(0x73, 0x4F, bytes@_*) =>
      bytes match {
        case Pixel(pixels) => Some(Values.Multiple(pixels))
        case _ => None
      }
    case _ => None
  }

  class Telemetry[F[_] : Sync] private(client: MqttClient,
                                       topic: String,
                                       bytesVar: MVar[F, Array[Byte]]) extends Source[F] {

    override def getValues: F[Values] = for {
      bytes <- bytesVar.take
      valuesOpt = unapply(bytes)
      values <- if (valuesOpt.isDefined) Sync[F].delay { valuesOpt.get } else getValues
    } yield values

    private val close: F[Unit] = Sync[F].delay {
      client.unsubscribe(topic)
      client.disconnect()
      client.close()
    }

  }

  object Telemetry {

    private val persistence: MqttClientPersistence = new MemoryPersistence()

    def apply[F[_] : ConcurrentEffect](server: String,
                                       topic: String)
                                      (implicit contextShift: ContextShift[F]): Resource[F, Telemetry[F]] = {
      val acquire: F[Telemetry[F]] = Sync[F].map(MVar.empty[F, Array[Byte]]) { bytesVar =>
        val client = new MqttClient(server, MqttClient.generateClientId(), persistence)
        val options = new MqttConnectOptions()
        options.setCleanSession(true)
        client.connect(options)
        client.subscribe(topic, (_: String, message: MqttMessage) => {
          ConcurrentEffect[F].toIO(Sync[F].flatMap(bytesVar.tryTake) { _ =>
            bytesVar.put(message.getPayload)
          }).unsafeRunAsyncAndForget()
        })
        new Telemetry(client, topic, bytesVar)
      }

      def release(telemetry: Telemetry[F]): F[Unit] = telemetry.close

      Resource.make(acquire)(release)
    }

  }

}
