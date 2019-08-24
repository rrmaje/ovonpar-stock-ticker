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
import com.paritytrading.foundation.ASCII
import java.util.concurrent.atomic.AtomicInteger

case class ClientRequest[A](username: String, request: Request[A]) extends WrappedRequest(request)

@Singleton
class SystemKey @Inject() (config: play.api.Configuration) {
  val key = new SecretKey(Base62.createInstance().decode(config.get[String]("ost.key").getBytes))
}

object ClientAction {
  
 final val OST_KEY = "OST";
  
}


class ClientAction @Inject()(val parser: BodyParsers.Default, ostKey: SystemKey)(implicit val executionContext: ExecutionContext)
    extends ActionBuilder[ClientRequest, AnyContent]
    with ActionTransformer[Request, ClientRequest] {
  import ClientAction._
  
  val logger: Logger = Logger("application")
  def transform[A](request: Request[A]) = Future.successful {
    val token: String = request.headers.get(OST_KEY).getOrElse("")
    logger.info(OST_KEY+": " + token)
    val user = new String(ostKey.key.open(token.getBytes))
    logger.debug(s"Client request: $user")
    new ClientRequest(user, request)
  }
}

class AuthenticationController @Inject() (cc: ControllerComponents, ostKey: SystemKey)(implicit system: ActorSystem, mat: Materializer) extends AbstractController(cc) with LoginApi {

  import LoginApi._

  implicit def executionContext: ExecutionContextExecutor = system.dispatcher

  implicit val formatCredentials = Json.format[Credentials]
  implicit val formatApplyResetPass = Json.format[ApplyResetPass]

  val logger: Logger = Logger("application")

  def createLoginApi() = {
    val loginDb = system.actorOf(Props[LoginDb], "login-db")
    loginDb
  }

  def login = Action.async(parse.json) { request =>
    val credentialsJson = request.body.validate[Credentials]
    credentialsJson.fold(
      errors => {
        Future.successful(BadRequest(Json.obj("status" -> 400, "message" -> JsError.toJson(errors))))
      },
      credentials => {
        findUser(credentials.username, credentials.password).map(user => {
          user match {
            case User(a, b, d) => {
              val encoded = ostKey.key.seal(d.getBytes());
              logger.info("Response-OST: " + new String(encoded))
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
          user match {
            case User(a, b, d) => {
              val encoded = ostKey.key.seal(d.getBytes());
              logger.info("Response-OST: " + new String(encoded))
              Ok(Json.obj("status" -> 200, "message" -> Json.obj("id" -> a, "username" -> b, "token" -> new String(encoded))))
            }
            case UserExists(b) => BadRequest(Json.obj("status" -> 401, "message" -> "Login in use"))
            case _             => InternalServerError(Json.obj("status" -> 500, "message" -> "Operation failed"))
          }
        })

      })

  }

}

object LoginApi {

  case class Credentials(username: String, password: String)

  case class CreateUser(username: String, password: String)

  case class UserExists(username: String)

  case class User(id: Int, username: String, parityuser: String)

  case class ResetPass(username: String)

  case class ResetPassResponse(genhash: String)

  case class ApplyResetPass(username: String, password: String, genhash: String)

}

trait LoginApi {

  import LoginApi._

  def createLoginApi(): ActorRef

  implicit def executionContext: ExecutionContext
  implicit val timeout = Timeout(5 seconds)

  lazy val loginApi: ActorRef = createLoginApi()

  def findUser(username: String, password: String): Future[Any] = {
    (loginApi ? Credentials(username, password)).mapTo[Any]
  }

  def requestResetPass(username: String): Future[Any] = {
    (loginApi ? ResetPass(username)).mapTo[Any]
  }

  def applyResetPass(username: String, password: String, genhash: String): Future[Any] = {
    (loginApi ? ApplyResetPass(username, password, genhash)).mapTo[Any]
  }

  def createLogin(username: String, password: String): Future[Any] = {
    (loginApi ? CreateUser(username, password)).mapTo[Any]
  }

}

class LoginDb extends Actor with akka.actor.ActorLogging {

  import LoginApi._

  var users = Vector((1, "test@foo.com", "test", "10101010"), (2, "test2@foo.com", "test", "20202020"))
  var resets = Map[Int, (String, Long)]()
  var key = new AtomicInteger(100)

  def receive = {
    case fu: Credentials => {
      val userTuple = users.find(v => { v._2 == fu.username && v._3 == fu.password }).getOrElse(None)
      userTuple match {
        case (a: Int, b: String, c, d: String) => {
          sender ! User(a, b, d)
        }
        case _ => sender ! None
      }
    }
    case rp: ResetPass =>
      genhash(rp.username)
    case arp: ApplyResetPass =>
      resetPass(arp.username, arp.password, arp.genhash)
    case cu: CreateUser =>
      createUser(cu.username, cu.password)
  }

  def genhash(username: String) = {
    val userTuple = users.find(v => { v._2 == username }).getOrElse(None)
    userTuple match {
      case (a: Int, b: String, c, d: String) => {
        val random = randomCode();
        val expires = Calendar.getInstance.getTimeInMillis + 3600000
        resets = resets.updated(a, (random, expires))
        sender ! ResetPassResponse(random)
      }
      case _ => sender ! None
    }
  }

  def resetPass(username: String, password: String, genhash: String) = {

    var userTuple = users.find(v => { v._2 == username }).getOrElse(None)
    userTuple match {
      case (a: Int, b: String, c, d: String) => {
        val hashTuple = resets.get(a).getOrElse(None)
        hashTuple match {
          case (x, y: Long) => {
            val now = Calendar.getInstance.getTimeInMillis
            if (x == genhash && y >= now) {
              users = users.map(t => {
                t._1 match {
                  case a => (a, b, password, d)
                }
              })
              resets = resets.filterKeys(k => k != a)
              sender ! User(a, b, d)
            } else {
              sender ! None
            }
          }
          case _ => sender ! None
        }

      }
      case _ => sender ! None
    }
  }

  def createUser(username: String, password: String) {
    val userTuple = users.find(v => { v._2 == username }).getOrElse(None)
    userTuple match {
      case None => {
        val d = String.valueOf(generateParityID(username))
        val user = (key.incrementAndGet(), username, password, d)
        users = users :+ user
        sender ! User(user._1, user._2, user._4)
      }
      case (a: Int, b: String, c, d: String) => {
        sender ! UserExists(b)
      }
      case _ => sender ! None
    }
  }

  private def randomCode() = {
    val chars = ('a' to 'z') ++ ('A' to 'Z') ++ (0 to 9)
    val sb = new StringBuilder
    for (i <- 1 to 10) {
      val randomNum = util.Random.nextInt(chars.length)
      sb.append(chars(randomNum))
    }
    sb.toString
  }

  private def generateParityID(username: String) {
    val id = if (username.length() > 8) username.substring(0, 8) else username
    log.debug("New 8 char id:" + id)
    ASCII.packLong(id)
  }

}

