package de.jnmeyr.ambiio

import cats.effect.concurrent.MVar
import cats.effect.{ContextShift, IO, Resource, Sync, _}
import de.jnmeyr.ambiio.Implicits.RichString
import org.http4s.dsl.impl.OptionalQueryParamDecoderMatcher
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s._
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._
import scala.io.Source
import scala.util.parsing.combinator.{PackratParsers, RegexParsers}
import scala.util.parsing.input.CharSequenceReader

sealed trait Controller[F[_]] {

  import Controller._

  def get: F[Command]

}

object Controller {

  private val logger = LoggerFactory.getLogger("Controller")

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

        private val every: Parser[FiniteDuration] = """ every """.r ~> """[^\s]+""".r ^? { case string if string.isFiniteDuration => string.toFiniteDuration }
        private val in: Parser[Pixel] = """ in """.r ~> """[^\s]+""".r ^? { case string if string.isPixel => string.toPixel }

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

    logger.info(s"Pipe with $arguments")

    private def read(path: String): F[Iterable[String]] = {
      Resource.fromAutoCloseable(
        Sync[F].delay(Source.fromFile(path))
      ).use(source =>
        Sync[F].delay(source.getLines.toSeq)
      )
    }

    override val get: F[Command] = Sync[F].flatMap(read(arguments.path))(lines =>
      lines.flatMap(parse).lastOption match {
        case Some(command) => Sync[F].pure(command)
        case None => get
      }
    )

  }

  object Pipe {

    case class Arguments(path: String) extends Controller.Arguments

    def apply[F[_] : Sync](arguments: Arguments): Pipe[F] = new Pipe[F](arguments)

  }

  class Http[F[_]] private(arguments: Arguments)
                          (override val get: F[Command]) extends Controller[F] {

    logger.info(s"Http with $arguments")

  }

  object Http extends Command.Parser {

    case class Arguments(host: String = "localhost", port: Int = 8080) extends Controller.Arguments

    implicit val finiteDurationDecoder: QueryParamDecoder[FiniteDuration] = QueryParamDecoder[String].map(_.toFiniteDuration)
    implicit val pixelDecoder: QueryParamDecoder[Pixel] = QueryParamDecoder[String].map(_.toPixel)
    implicit val commandDecoder: EntityDecoder[IO, Command] = EntityDecoder[IO, String].map(parse(_).get)

    object EveryOptMatcher extends OptionalQueryParamDecoderMatcher[FiniteDuration]("every")
    object InOptMatcher extends OptionalQueryParamDecoderMatcher[Pixel]("in")

    def apply(arguments: Arguments)
             (implicit contextShift: ContextShift[IO],
              timer: Timer[IO]): IO[Http[IO]] = {

      import org.http4s.dsl.io._

      def server(set: Command => IO[Unit]): IO[Unit] = {
        val frequencies: PartialFunction[Request[IO], IO[Response[IO]]] = {
          case GET -> Root / "frequencies" :? EveryOptMatcher(everyOpt) +& InOptMatcher(inOpt) =>
            set(Command.Frequencies(everyOpt, inOpt)) *> Ok()
        }
        val glow: PartialFunction[Request[IO], IO[Response[IO]]] = {
          case GET -> Root / "glow" :? InOptMatcher(inOpt) =>
            set(Command.Glow(inOpt)) *> Ok()
        }
        val loudness: PartialFunction[Request[IO], IO[Response[IO]]] = {
          case GET -> Root / "loudness" :? EveryOptMatcher(everyOpt) +& InOptMatcher(inOpt) =>
            set(Command.Loudness(everyOpt, inOpt)) *> Ok()
        }
        val pause: PartialFunction[Request[IO], IO[Response[IO]]] = {
          case GET -> Root / "pause" =>
            set(Command.Pause) *> Ok()
        }
        val pulse: PartialFunction[Request[IO], IO[Response[IO]]] = {
          case GET -> Root / "pulse" :? EveryOptMatcher(everyOpt) +& InOptMatcher(inOpt) =>
            set(Command.Pulse(everyOpt, inOpt)) *> Ok()
        }
        val stop: PartialFunction[Request[IO], IO[Response[IO]]] = {
          case GET -> Root / "stop" =>
            set(Command.Stop) *> Ok()
        }
        val command: PartialFunction[Request[IO], IO[Response[IO]]] = {
          case request @ POST -> Root / "command" =>
            request.as[Command].flatMap(set) *> Ok()
        }
        val app = HttpRoutes.of[IO](frequencies orElse glow orElse loudness orElse pause orElse pulse orElse stop orElse command).orNotFound

        BlazeServerBuilder[IO](global)
          .bindHttp(host = arguments.host, port = arguments.port)
          .withHttpApp(app)
          .serve.compile.drain
      }

      for {
        command <- MVar.empty[IO, Command]
        _ <- server(command.put).start
      } yield new Http[IO](arguments)(command.take)
    }
  }

  def apply(arguments: Arguments)
           (implicit contextShift: ContextShift[IO],
            timer: Timer[IO]): IO[Controller[IO]] = arguments match {
    case pipeArguments: Pipe.Arguments => IO.delay(Pipe[IO](pipeArguments))
    case httpArguments: Http.Arguments => Http(httpArguments)
  }

}
