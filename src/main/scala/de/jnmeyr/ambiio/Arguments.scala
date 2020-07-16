package de.jnmeyr.ambiio

import scopt.{OParser, OParserBuilder}

import scala.concurrent.duration._
import scala.language.postfixOps

case class Arguments(controller: Controller.Arguments = Controller.Pipe.Arguments("/tmp/ambiio"),
                     consumers: List[Consumer.Arguments] = List.empty[Consumer.Arguments])

object Arguments {

  private val builder: OParserBuilder[Arguments] = OParser.builder[Arguments]

  private val parser: OParser[Unit, Arguments] = {
    import builder._

    OParser.sequence(
      programName("ambiio"),
      head("Ambiio", "0.1"),

      opt[String]("controller.pipe.path")
        .optional()
        .action((path, arguments) => arguments.copy(controller = Controller.Pipe.Arguments(path)))
        .text("Path of the pipe controller file"),

      opt[Int]("consumer.printer.pixels")
        .optional()
        .action((pixels, arguments) => arguments.copy(consumers = Consumer.Printer.Arguments(pixels) +: arguments.consumers))
        .text("Number of pixels of the next printer consumer"),
      opt[Duration]("consumer.printer.every")
        .optional()
        .action((every, arguments) => arguments.copy(consumers = arguments.consumers match {
          case (consumer: Consumer.Printer.Arguments) :: consumers => consumer.copy(every = every.toMillis millis) +: consumers
          case _ => arguments.consumers
        }))
        .text("Frequency of the current printer consumer in milliseconds"),

      opt[Int]("consumer.serial.pixels")
        .optional()
        .action((pixels, arguments) => arguments.copy(consumers = Consumer.Serial.Arguments(pixels) +: arguments.consumers))
        .text("Number of pixels of the next serial consumer"),
      opt[Duration]("consumer.serial.every")
        .optional()
        .action((every, arguments) => arguments.copy(consumers = arguments.consumers match {
          case (consumer: Consumer.Serial.Arguments) :: consumers => consumer.copy(every = every.toMillis millis) +: consumers
          case _ => arguments.consumers
        }))
        .text("Frequency of the current serial consumer in milliseconds"),
      opt[String]("consumer.serial.name")
        .optional()
        .action((name, arguments) => arguments.copy(consumers = arguments.consumers match {
          case (consumer: Consumer.Serial.Arguments) :: consumers => consumer.copy(name = name) +: consumers
          case _ => arguments.consumers
        }))
        .text("Name of the current serial consumer"),

      opt[Int]("consumer.socket.pixels")
        .optional()
        .action((pixels, arguments) => arguments.copy(consumers = Consumer.Socket.Arguments(pixels) +: arguments.consumers))
        .text("Number of pixels of the next socket consumer"),
      opt[Duration]("consumer.socket.every")
        .optional()
        .action((every, arguments) => arguments.copy(consumers = arguments.consumers match {
          case (consumer: Consumer.Socket.Arguments) :: consumers => consumer.copy(every = every.toMillis millis) +: consumers
          case _ => arguments.consumers
        }))
        .text("Frequency of the current socket consumer in milliseconds"),
      opt[String]("consumer.socket.host")
        .optional()
        .action((host, arguments) => arguments.copy(consumers = arguments.consumers match {
          case (consumer: Consumer.Socket.Arguments) :: consumers => consumer.copy(host = host) +: consumers
          case _ => arguments.consumers
        }))
        .text("Host of the current socket consumer"),
      opt[Int]("consumer.socket.port")
        .optional()
        .action((port, arguments) => arguments.copy(consumers = arguments.consumers match {
          case (consumer: Consumer.Socket.Arguments) :: consumers => consumer.copy(port = port) +: consumers
          case _ => arguments.consumers
        }))
        .text("Port of the current socket consumer")
    )
  }

  def apply(args: List[String]): Option[Arguments] = OParser.parse(parser, args, Arguments())

}
