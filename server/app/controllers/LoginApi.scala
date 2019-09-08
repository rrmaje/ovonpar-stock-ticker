package controllers

import akka.actor.ActorRef
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import akka.util.Timeout
import akka.pattern.ask

object LoginApi {

  case class Credentials(username: String, password: String)

  case class CreateUser(username: String, password: String)

  case class UserExists(username: String)

  case class User(id: Long, username: String, parityuser: String)

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

  def findUser(username: String, password: String): Future[Option[User]] = {
    (loginApi ? Credentials(username, password)).mapTo[Option[User]]
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