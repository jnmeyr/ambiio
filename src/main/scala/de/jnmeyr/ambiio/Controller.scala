package de.jnmeyr.ambiio

import java.util.concurrent.TimeUnit

import cats.effect.{Resource, Sync}

import scala.concurrent.duration._
import scala.io.Source
import scala.language.postfixOps
import scala.util.parsing.combinator.{PackratParsers, RegexParsers}
import scala.util.parsing.input.CharSequenceReader

sealed trait Controller[F[_]] {

  import Controller._

  def get: F[Command]

}

object Controller {

  sealed trait Arguments

  sealed trait Command

  object Command {

    case class Frequencies(everyOpt: Option[FiniteDuration],
                           inOpt: Option[Pixel]) extends Command

    case class Glow(inOpt: Option[Pixel]) extends Command

    case class Loudness(everyOpt: Option[FiniteDuration],
                        inOpt: Option[Pixel]) extends Command

    case object Pause extends Command

    case class Pulse(everyOpt: Option[FiniteDuration],
                     inOpt: Option[Pixel]) extends Command

    case object Stop extends Command

    trait Parser {

      private object Parser extends RegexParsers with PackratParsers {

        override val skipWhitespace: Boolean = false

        private val every: Parser[FiniteDuration] = """ every """.r ~> """\d*""".r ^? { case string if string.toIntOption.isDefined => Duration(string.toInt, TimeUnit.MILLISECONDS) }

        private val in: Parser[Pixel] = {
          val color: Parser[Int] = """\d+""".r ^^ (_.toInt)
          """ in """.r ~> ((color <~ """ """.r) ~ (color <~ """ """.r) ~ color) ^^ { case red ~ green ~ blue => Pixel(red, green, blue) }
        }

        private lazy val frequencies: PackratParser[Command] = """frequencies""".r ~> opt(every) ~ opt(in) ^^ { case everyOpt ~ inOpt => Frequencies(everyOpt, inOpt) }
        private lazy val glow: PackratParser[Command] = """glow""".r ~> opt(in) ^^ Glow
        private lazy val loudness: PackratParser[Command] = """loudness""".r ~> opt(every) ~ opt(in) ^^ { case everyOpt ~ inOpt => Loudness(everyOpt, inOpt) }
        private lazy val pause: PackratParser[Command] = """pause""".r ^^ { _ => Pause }
        private lazy val pulse: PackratParser[Command] = """pulse""".r ~> opt(every) ~ opt(in) ^^ { case everyOpt ~ inOpt => Pulse(everyOpt, inOpt) }
        private lazy val stop: PackratParser[Command] = """stop""".r ^^ (_ => Stop)

        private lazy val command: PackratParser[Command] = frequencies | glow | loudness | pause | pulse | stop

        def apply(string: String): Option[Command] = {
          parseAll(phrase(command), new PackratReader(new CharSequenceReader(string))) match {
            case Success(command, next) if next.atEnd => Some(command)
            case _ => None
          }
        }

      }

      def parse(string: String): Option[Command] = Parser(string)

    }

  }

  class Pipe[F[_] : Sync] private(arguments: Pipe.Arguments) extends Controller[F] with Command.Parser {

    private def read(path: String): F[Iterable[String]] = {
      Resource.fromAutoCloseable(
        Sync[F].delay(Source.fromFile(path))
      ).use(source =>
        Sync[F].delay(source.getLines.toSeq)
      )
    }

    override val get: F[Command] = Sync[F].flatMap(read(arguments.path))(lines =>
      lines.flatMap(parse).lastOption match {
        case Some(value) => Sync[F].pure(value)
        case None => get
      }
    )

  }

  object Pipe {

    case class Arguments(path: String) extends Controller.Arguments

    def apply[F[_] : Sync](arguments: Arguments): Pipe[F] = new Pipe[F](arguments)

  }

  def apply[F[_] : Sync](arguments: Arguments): Controller[F] = {
    arguments match {
      case pipe: Pipe.Arguments => Pipe[F](pipe)
    }
  }

}
