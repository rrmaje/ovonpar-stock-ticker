package controllers

import model.UserRepository
import akka.actor.Actor
import java.util.Calendar
import akka.pattern.pipe
import scala.util.Failure
import scala.util.Success
import scala.concurrent.Future

case class EmailByParityUser(parityuser: String)

class LoginDb(userRepo: UserRepository) extends Actor with akka.actor.ActorLogging {

  import LoginApi._
  import scala.concurrent.ExecutionContext.Implicits.global

  //resets should be stored in distributed cache or Database to allow for multiple application instances
  var resets = Map[Long, (String, Long)]()

  def receive = {
    case fu: Credentials => {

      userRepo.findByUsernameAndPasword(fu.username, fu.password)
        .map(result => {

          val userTuple = result.getOrElse(None)
          userTuple match {
            case u: model.User => {
              Some(User(u.id.get, u.username, u.parityuser))
            }
            case _ => None
          }
        }) pipeTo sender
    }
    case rp: ResetPass =>
      genhash(rp.username)
    case arp: ApplyResetPass =>
      resetPass(arp.username, arp.password, arp.genhash)
    case cu: CreateUser =>
      createUser(cu.username, cu.password)
    case e: EmailByParityUser =>
      emailByParityUser(e.parityuser)
  }

  def genhash(username: String) = {

    userRepo.findByUsername(username).map(result => {
      val userTuple = result.getOrElse(None)
      userTuple match {
        case u: model.User => {
          val random = randomCode();
          val expires = Calendar.getInstance.getTimeInMillis + 3600000
          resets = resets.updated(u.id.get, (random, expires))
          ResetPassResponse(random)
        }
        case _ => None
      }
    }) pipeTo sender
  }

  def resetPass(username: String, password: String, genhash: String) = {

    userRepo.findByUsername(username).map(result => {
      val userTuple = result.getOrElse(None)
      userTuple match {
        case u: model.User => {
          val id = u.id.get
          val hashTuple = resets.get(id).getOrElse(None)
          hashTuple match {
            case (x, y: Long) => {
              val now = Calendar.getInstance.getTimeInMillis
              if (x == genhash && y >= now) {

                userRepo.update(id, model.User(Some(id), u.username, password, u.parityuser))
                resets = resets.filterKeys(k => k != id)
                User(u.id.get, u.username, u.parityuser)
              } else {
                None
              }
            }
            case _ => None
          }

        }
        case _ => None
      }
    }) pipeTo sender
  }

  def createUser(username: String, password: String) {

    userRepo.findByUsername(username).map(result => {
      val userTuple = result.getOrElse(None)
      userTuple match {

        case u: model.User => {
          UserExists(u.username)
        }

        case _ => None
      }
    }).flatMap(f =>
      f match {
        case None => {
          val d = generateParityID()

          val future = userRepo.insert(model.User(Some(0), username, password, d)).map(result => {

            val u = result.getOrElse(None)

            u match {
              case id: Long =>
                User(id, username, d)
              case None => log.error(s"Failed to create parity user: $d")
            }
          })
          future

        }
        case ue: UserExists => Future { ue }
      }) pipeTo sender
  }

  def emailByParityUser(username: String) {
    userRepo.findByParityUser(username).map(result => {
      val userTuple = result.getOrElse(None)
      userTuple match {
        case u: model.User => {
          u.username
        }
        case _ => None
      }
    }) pipeTo sender
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

  private def generateParityID(): String = {

    val sb = new StringBuilder()
    val r = new scala.util.Random()
    val chars = ('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')
    for (i <- 1 to 8) {
      sb.append(chars(r.nextInt(chars.length)))
    }
    log.debug("New 8 bytes id:" + sb.toString)
    sb.toString

  }

}



