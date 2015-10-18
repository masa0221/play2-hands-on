package controllers


import play.api.mvc._

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.db.slick._
import slick.driver.JdbcProfile
import models.Tables._
import javax.inject.Inject
import slick.driver.H2Driver.api._

import play.api.libs.json._
import play.api.libs.functional.syntax._

import scala.concurrent.Future

object JsonController {
  case class UserForm(id: Option[Long], name: String, companyId: Option[Int])

  // implicit val userFormWrites = Json.writes[UserForm]
  implicit val usersRowWritesWrites = (
    // functional.syntax._のおかげでこの書き方ができるぽい
    // __ は JsPathというライブラリ
    (__ \ "id").write[Long] and
    (__ \ "name").write[String] and
    (__ \ "companyId").writeNullable[Int]
  // unlift について
  // http://haskell.g.hatena.ne.jp/illillli/20080218/1203355886
  )(unlift(UsersRow.unapply))

  // implicit val userFormReads  = Json.reads[UserForm]
  implicit val userFormFormat = (
    (__ \ "id").readNullable[Long] and
    (__ \ "name").read[String] and
    (__ \ "companyId").readNullable[Int]
  )(UserForm)
}
class JsonController @Inject()(val dbConfigProvider: DatabaseConfigProvider) extends Controller
  with HasDatabaseConfigProvider[JdbcProfile]{

  import JsonController._

  /**
   * 一覧表示
   */
  def list = Action.async { implicit rs =>
    db.run(Users.sortBy(t => t.id).result).map { users =>
      Ok(Json.obj("users" -> users))
    }
  }

  /**
   * ユーザ登録
   */
  // parse は BodyParsers : リクエストボディの処理方法を決める
  def create = Action.async(parse.json) { implicit rs =>
    rs.body.validate[UserForm].map { form =>
      // OK
      val user = UsersRow(0, form.name, form.companyId)
      db.run(Users += user).map { _ =>
        Ok(Json.obj("result" -> "success"))
      }
    }.recoverTotal( e =>
      // NG
      Future {
        BadRequest(Json.obj("result" -> "failure", "error" -> JsError.toJson(e)))
      }
    )
  }

  /**
   * ユーザ更新
   */
  def update = Action.async(parse.json) { implicit rs =>
    rs.body.validate[UserForm].map { form =>
      val user = UsersRow(form.id.get, form.name, form.companyId)
      db.run(Users.filter(u => u.id === user.id).update(user)).map { _ =>
        Ok(Json.obj("result" -> "success"))
      }
    }.recoverTotal { e =>
      Future {
        BadRequest(Json.obj("result" -> "failure", "error" -> JsError.toJson(e)))
      }
    }
  }

  /**
   * ユーザ削除
   */
  def remove(id: Long) = Action.async { implicit rs =>
    db.run(Users.filter(u => u.id === id.bind).delete).map { _ =>
      Ok(Json.obj("result" -> "success"))
    }
  }
}
