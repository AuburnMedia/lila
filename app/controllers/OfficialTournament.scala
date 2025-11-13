package controllers

import play.api.mvc.*
import play.api.libs.json.*

import lila.app.{ *, given }
import lila.common.HTTPRequest
import lila.official.{ OfficialTournament as OfficialModel, OfficialForm }

final class OfficialTournament(env: Env)(using akka.stream.Materializer) extends LilaController(env):

  private def api = env.official.api
  private def forms = env.official.forms
  private def ui = env.official.ui

  def home = Open:
    for
      tournaments <- api.list(1, 20)
      page <- renderPage(ui.home(tournaments))
    yield Ok(page)

  def form = Auth { ctx ?=> me ?=>
    Ok.page(ui.form.create(forms.create))
  }

  def create = AuthBody { ctx ?=> me ?=>
    forms.create
      .bindFromRequest()
      .fold(
        err => BadRequest.page(ui.form.create(err)),
        setup =>
          // TODO: Implement tournament creation logic
          Redirect(routes.OfficialTournament.home)
      )
  }

  def show(id: lila.official.OfficialTournamentId) = Open:
    api.byId(id).flatMap:
      case None => NotFound.page(views.html.base.notFound)
      case Some(tournament) =>
        Ok.page(ui.show(tournament))

  def join(id: lila.official.OfficialTournamentId) = Auth { ctx ?=> me ?=>
    // TODO: Implement join logic via api
    Redirect(routes.OfficialTournament.show(id))
  }

  def withdraw(id: lila.official.OfficialTournamentId) = Auth { ctx ?=> me ?=>
    // TODO: Implement withdraw logic via api
    Redirect(routes.OfficialTournament.show(id))
  }
