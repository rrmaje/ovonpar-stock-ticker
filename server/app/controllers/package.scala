package controllers

import play.api.libs.json.Json
import play.api.mvc._
import javax.inject.Inject

import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import play.api.Logger
import play.api.Logging
import play.api.libs.json.JsError
import crypto.SecretKey
import javax.inject.Singleton
import io.seruco.encoding.base62.Base62
import java.util.Calendar
import akka.actor.Actor
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.actor.Props
import akka.actor.ActorRef
import akka.util.Timeout
import scala.concurrent.duration._
import akka.pattern.ask
import scala.concurrent.ExecutionContextExecutor
import java.util.concurrent.atomic.AtomicInteger
import play.api.i18n.MessagesApi
import play.api.i18n.Langs
import play.api.http.FileMimeTypes
import play.api.db.DBApi
import play.api.db.Database
import model.UserRepository



case class ClientRequest[A](username: String, request: Request[A]) extends WrappedRequest(request)

@Singleton
class SystemKey @Inject() (config: play.api.Configuration) {
  val key = new SecretKey(Base62.createInstance().decode(config.get[String]("ost.key").getBytes))
}

object ClientAction {

  final val OST_KEY = "OST";

}

class ClientAction @Inject() (val parser: BodyParsers.Default, ostKey: SystemKey)(implicit val executionContext: ExecutionContext)
  extends ActionBuilder[ClientRequest, AnyContent]
  with ActionTransformer[Request, ClientRequest] {
  import ClientAction._

  val logger: Logger = Logger("application")
  def transform[A](request: Request[A]) = Future.successful {
    val token: String = request.headers.get(OST_KEY).getOrElse("")
    logger.debug(OST_KEY + ": " + token)
    val user = new String(ostKey.key.open(token.getBytes))
    logger.debug(s"Client request: $user")
    new ClientRequest(user, request)
  }
}


case class AppControllerComponents @Inject() (
  actionBuilder:    DefaultActionBuilder,
  parsers:          PlayBodyParsers,
  messagesApi:      MessagesApi,
  langs:            Langs,
  fileMimeTypes:    FileMimeTypes,
  executionContext: scala.concurrent.ExecutionContext,
  ostKey:           SystemKey,
  userRepo:         UserRepository)
  extends ControllerComponents {

}

class AppBaseController @Inject() (cc: AppControllerComponents)(implicit system: ActorSystem, mat: Materializer)
  extends BaseController {
  override protected def controllerComponents: ControllerComponents = cc

  implicit def executionContext: ExecutionContextExecutor = system.dispatcher

}

class LoginController @Inject() (cc: AppControllerComponents)(implicit system: ActorSystem, mat: Materializer) extends AppBaseController(cc) with LoginApi {

  import LoginApi._

  implicit val formatCredentials = Json.format[Credentials]
  implicit val formatApplyResetPass = Json.format[ApplyResetPass]

  val logger: Logger = Logger("application")

  def createLoginApi() = {
    system.actorOf(Props(new LoginDb(cc.userRepo)), "login-db")
  }

  def login = Action.async(parse.json) { request =>
    val credentialsJson = request.body.validate[Credentials]
    credentialsJson.fold(
      errors => {
        Future.successful(BadRequest(Json.obj("status" -> 400, "message" -> JsError.toJson(errors))))
      },
      credentials => {
        findUser(credentials.username, credentials.password).map(user => {
          println(">>"+user)
          val usr = user.getOrElse(None)
          usr match {
            case User(a, b, d) => {
              val encoded = cc.ostKey.key.seal(d.getBytes());
              logger.debug("Response-OST: " + new String(encoded))
              Ok(Json.obj("status" -> 200, "message" -> Json.obj("id" -> a, "username" -> b, "token" -> new String(encoded))))
            }
            case _ => BadRequest(Json.obj("status" -> 401, "message" -> "Incorrect credentials"))
          }
        })

      })

  }

  def genhash = Action.async(parse.json) { request =>
    (request.body \ "username").asOpt[String].map { name =>

      requestResetPass(name).map(response => {
        response match {
          case ResetPassResponse(code) => {
            Ok(Json.obj("status" -> 200, "message" -> Json.obj("username" -> name, "genhash" -> code)))
          }
          case _ => BadRequest(Json.obj("status" -> 400, "message" -> "Incorrect params"))
        }
      })
    }.getOrElse {
      Future.successful(BadRequest(Json.obj("status" -> 400, "message" -> "Incorrect params")))
    }

  }

  def resetPass = Action.async(parse.json) { request =>
    val resetPassJson = request.body.validate[ApplyResetPass]
    resetPassJson.fold(
      errors => {
        Future.successful(BadRequest(Json.obj("status" -> 400, "message" -> JsError.toJson(errors))))
      },
      reset => {
        applyResetPass(reset.username, reset.password, reset.genhash).map(user => {
          user match {
            case User(a, b, d) => {
              Ok(Json.obj("status" -> 200, "message" -> Json.obj("id" -> a, "username" -> b)))
            }
            case _ => BadRequest(Json.obj("status" -> 401, "message" -> "Operation failed"))
          }
        })

      })

  }

  def newLogin = Action.async(parse.json) { request =>
    val credentialsJson = request.body.validate[Credentials]
    credentialsJson.fold(
      errors => {
        Future.successful(BadRequest(Json.obj("status" -> 400, "message" -> JsError.toJson(errors))))
      },
      credentials => {
        createLogin(credentials.username, credentials.password).map(user => {
          println(">>"+user)
          user match {
            case User(a, b, d) => {
              val encoded = cc.ostKey.key.seal(d.getBytes());
              logger.debug("Response-OST: " + new String(encoded))
              Ok(Json.obj("status" -> 200, "message" -> Json.obj("id" -> a, "username" -> b, "token" -> new String(encoded))))
            }
            case UserExists(b) => BadRequest(Json.obj("status" -> 401, "message" -> "Login in use"))
            case _             => InternalServerError(Json.obj("status" -> 500, "message" -> "Operation failed"))
          }
        })

      })

  }

}



