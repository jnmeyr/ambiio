package de.jnmeyr.ambiio

import cats.effect.IO
import de.jnmeyr.ambiio.Implicits.RichString
import io.circe.generic.auto._
import io.circe._
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.{EntityDecoder, EntityEncoder}

import scala.concurrent.duration.FiniteDuration
import scala.util.parsing.combinator.{PackratParsers, RegexParsers}
import scala.util.parsing.input.CharSequenceReader

sealed trait Command

object Command {

  case class Frequencies(everyOpt: Option[FiniteDuration],
                         inOpt: Option[Pixel])
    extends Command

  case class Glow(inOpt: Option[Pixel])
    extends Command

  case class Loudness(everyOpt: Option[FiniteDuration],
                      inOpt: Option[Pixel])
    extends Command

  case object Pause
    extends Command

  case class Pulse(everyOpt: Option[FiniteDuration],
                   inOpt: Option[Pixel])
    extends Command

  case class Telemetry(server: String,
                       topic: String)
    extends Command

  trait Parser {

    private object Parser
      extends RegexParsers
        with PackratParsers {

      override val skipWhitespace: Boolean = false

      private val every: Parser[FiniteDuration] = " every " ~> """[^\s]+""".r ^? { case string if string.isFiniteDuration => string.toFiniteDuration }
      private val in: Parser[Pixel] = " in " ~> """[^\s]+""".r ^? { case string if string.isPixel => string.toPixel }
      private val topic: Parser[String] = " of " ~> """[^\s]+""".r ^^ { s => s }
      private val server: Parser[String] = " from " ~> """[^\s]+""".r ^^ { s => s }

      private lazy val frequencies: PackratParser[Command] = "frequencies" ~> opt(every) ~ opt(in) ^^ { case everyOpt ~ inOpt => Frequencies(everyOpt, inOpt) }
      private lazy val glow: PackratParser[Command] = "glow" ~> opt(in) ^^ Glow
      private lazy val loudness: PackratParser[Command] = "loudness" ~> opt(every) ~ opt(in) ^^ { case everyOpt ~ inOpt => Loudness(everyOpt, inOpt) }
      private lazy val pause: PackratParser[Command] = "pause" ^^ { _ => Pause }
      private lazy val pulse: PackratParser[Command] = "pulse" ~> opt(every) ~ opt(in) ^^ { case everyOpt ~ inOpt => Pulse(everyOpt, inOpt) }
      private lazy val telemetry: PackratParser[Command] = "telemetry" ~> topic ~ server ^^ { case topic ~ server => Telemetry(server, topic) }

      private lazy val command: PackratParser[Command] = frequencies | glow | loudness | pause | pulse | telemetry

      def apply(string: String): Option[Command] = {
        parseAll(phrase(command), new PackratReader(new CharSequenceReader(string))) match {
          case Success(command, next) if next.atEnd => Some(command)
          case _ => None
        }
      }

    }

    def parse(string: String): Option[Command] = Parser(string)

  }

  trait Encoders {

    implicit val FiniteDurationEncoder: Encoder[FiniteDuration] = (finiteDuration: FiniteDuration) => {
      Json.fromString(finiteDuration.toString)
    }

    implicit val PixelEncoder: Encoder[Pixel] = (pixel: Pixel) => {
      Json.fromString(pixel.toHexString)
    }

    implicit val CommandEncoder: EntityEncoder[IO, Command] = jsonEncoderOf[IO, Command]

  }

  trait Decoders {

    implicit final val FiniteDurationDecoder: Decoder[FiniteDuration] = (hCursor: HCursor) => for {
      string <- hCursor.as[String]
      finiteDuration <- string.toFiniteDurationOpt.toRight(DecodingFailure("FiniteDuration", hCursor.history))
    } yield finiteDuration

    implicit final val PixelDecoder: Decoder[Pixel] = (hCursor: HCursor) => for {
      string <- hCursor.as[String]
      pixel <- string.toPixelOpt.toRight(DecodingFailure("Pixel", hCursor.history))
    } yield pixel

    implicit val CommandDecoder: EntityDecoder[IO, Command] = jsonOf[IO, Command]

  }

}
