package model

import java.util.Date
import javax.inject.Inject

import anorm.SqlParser.{ get, scalar }
import anorm._
import play.api.db.DBApi

import scala.concurrent.Future

case class User(
  id:         Option[Long] = None,
  username:   String,
  password:   String,
  parityuser: String)

object User {
  implicit def toParameters: ToParameterList[User] =
    Macro.toParameters[User]
}

@javax.inject.Singleton
class UserRepository @Inject() (dbapi: DBApi)(implicit ec: DatabaseExecutionContext) {

  private val db = dbapi.database("default")

  private val simple = {
    get[Option[Long]]("parity_user.id") ~
      get[String]("parity_user.username") ~
      get[String]("parity_user.password") ~
      get[String]("parity_user.parityuser") map {
        case id ~ username ~ password ~ parityuser =>
          User(id, username, password, parityuser)
      }
  }

  def insert(user: User): Future[Option[Long]] = Future {
    db.withConnection { implicit connection =>
     SQL("""
        insert into parity_user(username,password,parityuser) values (
          {username}, {password}, {parityuser}
        )
      """).bind(user).executeInsert()
    }
  }(ec)
  
  def findByUsername(username: String): Future[Option[User]] = Future {
    db.withConnection { implicit connection =>
      SQL"select * from parity_user where username = $username".as(simple.singleOpt)
    }
  }(ec)
  
  def findByParityUser(username: String): Future[Option[User]] = Future {
    db.withConnection { implicit connection =>
      SQL"select * from parity_user where parityuser = $username".as(simple.singleOpt)
    }
  }(ec)
  
  def findByUsernameAndPasword(username: String, password: String): Future[Option[User]] = Future {
    db.withConnection { implicit connection =>
      SQL"select * from parity_user where username = $username and password = $password".as(simple.singleOpt)
    }
  }(ec)
  
  def update(id: Long, user:User) = Future {
    db.withConnection { implicit connection =>
      SQL("""
        update parity_user set username = {username}, password = {password}, 
          parityuser = {parityuser}
        where id = {id}
      """).bind(user.copy(id = Some(id)/* ensure */)).executeUpdate()
      // case class binding using ToParameterList,
      // note using SQL(..) but not SQL.. interpolation
    }
  }(ec)
  


}