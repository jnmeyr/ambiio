package de.jnmeyr.ambiio

import java.util.concurrent.Executors

import cats.data.NonEmptyList
import cats.effect.concurrent.MVar
import cats.effect.{ContextShift, IO, Resource, Sync, _}
import cats.implicits._
import de.jnmeyr.ambiio.Controller._
import de.jnmeyr.ambiio.Implicits.RichString
import io.circe._
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s.CacheDirective.`no-cache`
import org.http4s._
import org.http4s.circe._
import org.http4s.headers.{`Cache-Control`, `Location`}
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._
import scala.io.Source
import scala.util.parsing.combinator.{PackratParsers, RegexParsers}
import scala.util.parsing.input.CharSequenceReader

abstract class Controller[F[_] : Sync](lastCommandVar: MVar[F, Command]) {

  protected def getNextCommand: F[Command]

  private def setLastCommand(command: Command): F[Unit] = lastCommandVar.tryTake *> lastCommandVar.put(command)

  final def apply(): F[Command] = for {
    command <- getNextCommand
    _ <- setLastCommand(command)
  } yield command

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

    case class Telemetry(server: String,
                         topic: String) extends Command

    trait Parser {

      private object Parser extends RegexParsers with PackratParsers {

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

  class Forever[F[_] : Sync](arguments: Arguments)
                            (nextCommandVar: MVar[F, Command],
                             lastCommandVar: MVar[F, Command]) extends Controller[F](lastCommandVar) {

    logger.info(s"Forever with $arguments")

    override protected def getNextCommand: F[Command] = nextCommandVar.take

  }

  object Forever {

    case class Arguments(command: Command) extends Controller.Arguments

    def apply[F[_] : Concurrent](arguments: Arguments)
                                (implicit contextShift: ContextShift[F]): F[Controller[F]] = for {
      nextCommandVar <- MVar.of[F, Command](arguments.command)
      lastCommandVar <- MVar.empty[F, Command]
    } yield new Forever[F](arguments)(nextCommandVar, lastCommandVar)

  }

  class Http[F[_] : Sync] private(arguments: Arguments)
                                 (nextCommandVar: MVar[F, Command],
                                  lastCommandVar: MVar[F, Command]) extends Controller[F](lastCommandVar) {

    logger.info(s"Http with $arguments")

    override protected def getNextCommand: F[Command] = nextCommandVar.take

  }

  object Http extends Command.Encoders with Command.Decoders {

    case class Arguments(host: String = "localhost", port: Int = 8080) extends Controller.Arguments

    def apply(arguments: Arguments)
             (implicit contextShift: ContextShift[IO],
              timer: Timer[IO]): IO[Controller[IO]] = {
      import org.http4s.dsl.io._

      val blocker = Blocker.liftExecutorService(Executors.newFixedThreadPool(4))

      def server(get: IO[Command], set: Command => IO[Unit]): IO[Unit] = {
        val root: PartialFunction[Request[IO], IO[Response[IO]]] = {
          case GET -> Root =>
            MovedPermanently()
              .map(_.putHeaders(`Location`(Uri(path = "/index.html"))))
        }
        val command: PartialFunction[Request[IO], IO[Response[IO]]] = {
          case GET -> Root / "command" =>
            get.flatMap(command => Ok(command.asJson))
          case request@POST -> Root / "command" =>
            request.as[Command].flatMap(set) *> Ok()
        }
        val resource: PartialFunction[Request[IO], IO[Response[IO]]] = {
          case request =>
            StaticFile
              .fromResource[IO](request.pathInfo, blocker, request.some)
              .map(_.putHeaders(`Cache-Control`(NonEmptyList.of(`no-cache`()))))
              .getOrElseF(NotFound())
        }
        val app = HttpRoutes.of[IO](root orElse command orElse resource).orNotFound

        BlazeServerBuilder[IO](global)
          .bindHttp(host = arguments.host, port = arguments.port)
          .withHttpApp(app)
          .serve.compile.drain
      }

      for {
        nextCommandVar <- MVar.empty[IO, Command]
        lastCommandVar <- MVar.empty[IO, Command]
        _ <- server(lastCommandVar.read, nextCommandVar.put).start
      } yield new Http[IO](arguments)(nextCommandVar, lastCommandVar)
    }

  }

  class Pipe[F[_] : Sync] private(arguments: Pipe.Arguments)
                                 (lastCommandVar: MVar[F, Command]) extends Controller[F](lastCommandVar) with Command.Parser {

    logger.info(s"Pipe with $arguments")

    private def read(path: String): F[Iterable[String]] = {
      Resource.fromAutoCloseable(
        Sync[F].delay(Source.fromFile(path))
      ).use(source =>
        Sync[F].delay(source.getLines().iterator.to(Iterable))
      )
    }

    override protected val getNextCommand: F[Command] = Sync[F].flatMap(read(arguments.path))(lines =>
      lines.flatMap(parse).lastOption match {
        case Some(command) => Sync[F].pure(command)
        case None => getNextCommand
      }
    )

  }

  object Pipe {

    case class Arguments(path: String) extends Controller.Arguments

    def apply[F[_] : Concurrent](arguments: Arguments): F[Controller[F]] = for {
      lastCommandVar <- MVar.empty[F, Command]
    } yield new Pipe[F](arguments)(lastCommandVar)

  }

  def apply(arguments: Arguments)
           (implicit contextShift: ContextShift[IO],
            timer: Timer[IO]): IO[Controller[IO]] = arguments match {
    case foreverArguments: Forever.Arguments => Forever[IO](foreverArguments)
    case httpArguments: Http.Arguments => Http(httpArguments)
    case pipeArguments: Pipe.Arguments => Pipe[IO](pipeArguments)
  }

}
