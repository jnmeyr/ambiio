package de.jnmeyr.ambiio

import java.util.concurrent.Executors

import cats.data.NonEmptyList
import cats.effect.concurrent.{MVar, Ref}
import cats.effect.{ContextShift, Resource, Sync, _}
import cats.implicits._
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s.CacheDirective.`no-cache`
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.{`Cache-Control`, `Location`}
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.global
import scala.io.Source

abstract class Controller[F[_] : Sync](protected val lastCommandRef: Ref[F, Command]) {

  final def getLastCommand: F[Command] = lastCommandRef.get

  private def setLastCommand(command: Command): F[Unit] = lastCommandRef.set(command)

  protected def getNextCommandImpl: F[Command]

  final def getNextCommand: F[Command] = for {
    command <- getNextCommandImpl
    _ <- setLastCommand(command)
  } yield command

}

object Controller {

  private val logger = LoggerFactory.getLogger("Controller")

  sealed trait Arguments

  class Forever[F[_] : Sync](arguments: Arguments)
                            (nextCommandVar: MVar[F, Command],
                             lastCommandRef: Ref[F, Command])
    extends Controller[F](lastCommandRef) {

    logger.info(s"Forever with $arguments")

    override protected def getNextCommandImpl: F[Command] = nextCommandVar.take

  }

  object Forever {

    case class Arguments(command: Command)
      extends Controller.Arguments

    def apply[F[_] : Concurrent](arguments: Arguments)
                                (implicit contextShift: ContextShift[F]): F[Controller[F]] = for {
      nextCommandVar <- MVar.of[F, Command](arguments.command)
      lastCommandRef <- Ref.of[F, Command](Command.Pause)
    } yield new Forever[F](arguments)(nextCommandVar, lastCommandRef)

  }

  class Http[F[_] : Sync] private(arguments: Arguments)
                                 (nextCommandVar: MVar[F, Command],
                                  lastCommandRef: Ref[F, Command])
    extends Controller[F](lastCommandRef) {

    logger.info(s"Http with $arguments")

    override protected def getNextCommandImpl: F[Command] = nextCommandVar.take

  }

  object Http
    extends Command.Encoders
      with Command.Decoders {

    case class Arguments(host: String = "localhost",
                         port: Int = 8080)
      extends Controller.Arguments

    def apply[F[_] : ConcurrentEffect](arguments: Arguments)
                                      (implicit contextShift: ContextShift[F],
                                       timer: Timer[F]): F[Controller[F]] = {
      val dsl = Http4sDsl[F]
      import dsl._

      val blocker = Blocker.liftExecutorService(Executors.newFixedThreadPool(4))

      def server(get: F[Command], set: Command => F[Unit]): F[Unit] = {
        val root: PartialFunction[Request[F], F[Response[F]]] = {
          case GET -> Root =>
            MovedPermanently()
              .map(_.putHeaders(`Location`(Uri(path = "/index.html"))))
        }
        val command: PartialFunction[Request[F], F[Response[F]]] = {
          case GET -> Root / "command" =>
            get.flatMap(command => Ok(command.asJson))
          case request@POST -> Root / "command" =>
            request.as[Command].flatMap(set) *> Ok()
        }
        val resource: PartialFunction[Request[F], F[Response[F]]] = {
          case request =>
            StaticFile
              .fromResource[F](request.pathInfo, blocker, request.some)
              .map(_.putHeaders(`Cache-Control`(NonEmptyList.of(`no-cache`()))))
              .getOrElseF(NotFound())
        }
        val app = HttpRoutes.of[F](root orElse command orElse resource).orNotFound

        BlazeServerBuilder[F](global)
          .bindHttp(host = arguments.host, port = arguments.port)
          .withHttpApp(app)
          .serve.compile.drain
      }

      for {
        nextCommandVar <- MVar.empty[F, Command]
        lastCommandRef <- Ref.of[F, Command](Command.Pause)
        _ <- Concurrent[F].start(server(lastCommandRef.get, nextCommandVar.put))
      } yield new Http[F](arguments)(nextCommandVar, lastCommandRef)
    }

  }

  class Pipe[F[_] : Sync] private(arguments: Pipe.Arguments)
                                 (lastCommandRef: Ref[F, Command])
    extends Controller[F](lastCommandRef)
      with Command.Parser {

    logger.info(s"Pipe with $arguments")

    private def read(path: String): F[Iterable[String]] = {
      Resource.fromAutoCloseable(
        Sync[F].delay(Source.fromFile(path))
      ).use(source =>
        Sync[F].delay(source.getLines().iterator.to(Iterable))
      )
    }

    override protected val getNextCommandImpl: F[Command] = Sync[F].flatMap(read(arguments.path))(lines =>
      lines.flatMap(parse).lastOption match {
        case Some(command) => Sync[F].pure(command)
        case None => getNextCommandImpl
      }
    )

  }

  object Pipe {

    case class Arguments(path: String)
      extends Controller.Arguments

    def apply[F[_] : Concurrent](arguments: Arguments): F[Controller[F]] = for {
      lastCommandRef <- Ref.of[F, Command](Command.Pause)
    } yield new Pipe[F](arguments)(lastCommandRef)

  }

  object WithTimeout {

    def apply[F[_] : Concurrent](timeout: Timeout[F])
                                (controller: Controller[F]): Controller[F] = new Controller[F](controller.lastCommandRef) {

      private def nextCommandOpt(lastCommand: Command): Option[Command] = lastCommand match {
        case _: Command.Frequencies | _: Command.Loudness => Some(Command.Pause)
        case _ => None
      }

      override protected def getNextCommandImpl: F[Command] =
        Concurrent[F].race(
          controller.getNextCommandImpl,
          for {
            _ <- timeout
            lastCommand <- controller.getLastCommand
            nextCommand <- nextCommandOpt(lastCommand).fold[F[Command]](Async[F].never)(Sync[F].pure)
          } yield nextCommand
        ).map(_.fold(identity, identity))

    }

  }

  def apply[F[_] : ConcurrentEffect](arguments: Arguments,
                                     timeout: Timeout[F])
                                    (implicit contextShift: ContextShift[F],
                                     timer: Timer[F]): F[Controller[F]] = arguments match {
    case foreverArguments: Forever.Arguments => Forever[F](foreverArguments)
    case httpArguments: Http.Arguments => Http[F](httpArguments).map(WithTimeout(timeout))
    case pipeArguments: Pipe.Arguments => Pipe[F](pipeArguments).map(WithTimeout(timeout))
  }

}
