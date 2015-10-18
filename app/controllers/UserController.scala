package controllers

import javax.inject.Inject

import models.Tables._
import play.api.data.Forms._
import play.api.data._
import play.api.db.slick._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc._
import slick.driver.H2Driver.api._
import slick.driver.JdbcProfile

import scala.concurrent.Future

object UserController {
  case class UserForm(id: Option[Long], name: String, companyId: Option[Int])

  val userForm = Form(
    mapping(
      "id"        -> optional(longNumber),
      "name"      -> nonEmptyText(maxLength = 20),
      "companyId" -> optional(number)
    )(UserForm.apply)(UserForm.unapply)
  )
}

class UserController @Inject()(val dbConfigProvider: DatabaseConfigProvider,
                               val messagesApi: MessagesApi) extends Controller
  with HasDatabaseConfigProvider[JdbcProfile] with I18nSupport {
  import UserController._

  /**
   * 一覧表示
   */
  def list = Action.async { implicit rs =>
    db.run(Users.sortBy(t => t.id).result).map { users =>
      // 一覧を表示
      Ok(views.html.user.list(users))
    }
  }

  /**
   * 編集画面表示
   */
  def edit(id: Option[Long]) = Action.async { implicit rs =>
    val form = if(id.isDefined) {
      // IDがフォームのhiddenで渡されたものと一致する場合に実施される
      db.run(Users.filter(t => t.id === id.get.bind).result.head).map { user =>
        userForm.fill(UserForm(Some(user.id), user.name, user.companyId))
      }
    } else {
      Future {
        userForm
      }
    }

    form.flatMap { form =>
      db.run(Companies.sortBy(_.id).result).map { companies =>
        Ok(views.html.user.edit(form, companies))
      }
    }
  }

  /**
   * 登録実行
   */
  def create = Action.async { implicit rs =>
    userForm.bindFromRequest.fold(
      error => {
        db.run(Companies.sortBy(t => t.id).result).map { companies =>
          BadRequest(views.html.user.edit(error, companies))
        }
      },
      form => {
        val user = UsersRow(0, form.name, form.companyId)
        db.run(Users += user).map { _ =>
          Redirect(routes.UserController.list)
        }
      }
    )
  }

  /**
   * 更新実行
   */
  def update = Action.async { implicit rs =>
    userForm.bindFromRequest.fold(
      error => {
        db.run(Companies.sortBy(t => t.id).result).map { companies =>
          BadRequest(views.html.user.edit(error, companies))
        }
      },
      form => {
        val user = UsersRow(form.id.get, form.name, form.companyId)
        db.run(Users.filter(u => u.id === user.id).update(user)).map { _ =>
          Redirect(routes.UserController.list)
        }
      }
    )
  }

  /**
   * 削除実行
   */
  def remove(id: Long) = Action.async { implicit rs =>
    db.run(Users.filter(u => u.id === id.bind).delete).map { _ =>
      Redirect(routes.UserController.list)
    }
  }
}

