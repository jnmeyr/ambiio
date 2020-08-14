package de.jnmeyr.ambiio

import de.jnmeyr.ambiio.Controller.Http
import scopt.{OParser, OParserBuilder}

import scala.concurrent.duration._

case class Arguments(controller: Controller.Arguments = Controller.Forever.Arguments(Command.Pause),
                     consumers: List[Consumer.Arguments] = List.empty[Consumer.Arguments])

object Arguments
  extends Command.Parser {

  private val builder: OParserBuilder[Arguments] = OParser.builder[Arguments]

  private val parser: OParser[Unit, Arguments] = {
    import builder._

    OParser.sequence(
      programName("ambiio"),
      head("Ambiio", "0.4"),

      opt[String]("controller.forever.command")
        .optional()
        .action((command, arguments) => parse(command) match {
          case Some(command) => arguments.copy(controller = Controller.Forever.Arguments(command))
          case None => arguments
        })
        .text("Command of the forever controller"),

      opt[String]("controller.http.host")
        .optional()
        .action((host, arguments) => arguments.copy(controller = arguments.controller match {
          case controller: Http.Arguments => controller.copy(host = host)
          case _ => Controller.Http.Arguments(host = host)
        }))
        .text("Host of the http controller"),
      opt[Int]("controller.http.port")
        .optional()
        .action((port, arguments) => arguments.copy(controller = arguments.controller match {
          case controller: Http.Arguments => controller.copy(port = port)
          case _ => Controller.Http.Arguments(port = port)
        }))
        .text("Port of the http controller"),

      opt[String]("controller.pipe.path")
        .optional()
        .action((path, arguments) => arguments.copy(controller = Controller.Pipe.Arguments(path)))
        .text("Path of the pipe controller file"),

      opt[Int]("consumer.printer.pixels")
        .optional()
        .action((pixels, arguments) => arguments.copy(consumers = Consumer.Printer.Arguments(pixels) +: arguments.consumers))
        .text("Number of pixels of the next printer consumer"),

      opt[Int]("consumer.serial.pixels")
        .optional()
        .action((pixels, arguments) => arguments.copy(consumers = Consumer.Serial.Arguments(pixels) +: arguments.consumers))
        .text("Number of pixels of the next serial consumer"),
      opt[Duration]("consumer.serial.every")
        .optional()
        .action((every, arguments) => arguments.copy(consumers = arguments.consumers match {
          case (consumer: Consumer.Serial.Arguments) :: consumers => consumer.copy(every = every.asInstanceOf[FiniteDuration]) +: consumers
          case _ => arguments.consumers
        }))
        .text("Frequency of the current serial consumer"),
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
          case (consumer: Consumer.Socket.Arguments) :: consumers => consumer.copy(every = every.asInstanceOf[FiniteDuration]) +: consumers
          case _ => arguments.consumers
        }))
        .text("Frequency of the current socket consumer"),
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
        .text("Port of the current socket consumer"),

      opt[Int]("consumer.telemetry.pixels")
        .optional()
        .action((pixels, arguments) => arguments.copy(consumers = Consumer.Telemetry.Arguments(pixels) +: arguments.consumers))
        .text("Number of pixels of the next telemetry consumer"),
      opt[String]("consumer.telemetry.server")
        .optional()
        .action((server, arguments) => arguments.copy(consumers = arguments.consumers match {
          case (consumer: Consumer.Telemetry.Arguments) :: consumers => consumer.copy(server = server) +: consumers
          case _ => arguments.consumers
        }))
        .text("Server of the current telemetry consumer"),
      opt[String]("consumer.telemetry.topic")
        .optional()
        .action((topic, arguments) => arguments.copy(consumers = arguments.consumers match {
          case (consumer: Consumer.Telemetry.Arguments) :: consumers => consumer.copy(topic = topic) +: consumers
          case _ => arguments.consumers
        }))
        .text("Topic of the current telemetry consumer")
    )
  }

  def apply(args: List[String]): Option[Arguments] = OParser.parse(parser, args, Arguments())

}
