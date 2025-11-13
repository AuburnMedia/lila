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
          api.create(setup, me.userId).map: tournament =>
            Redirect(routes.OfficialTournament.show(tournament.id))
      )
  }

  def show(id: lila.official.OfficialTournamentId) = Open:
    api.byId(id).flatMap:
      case None => NotFound.page(views.html.base.notFound)
      case Some(tournament) =>
        Ok.page(ui.show(tournament))

  def join(id: lila.official.OfficialTournamentId) = Auth { ctx ?=> me ?=>
    api.join(id, me.userId).map: success =>
      if success then Redirect(routes.OfficialTournament.show(id))
      else BadRequest("Could not join tournament")
  }

  def withdraw(id: lila.official.OfficialTournamentId) = Auth { ctx ?=> me ?=>
    api.withdraw(id, me.userId).map: success =>
      if success then Redirect(routes.OfficialTournament.show(id))
      else BadRequest("Could not withdraw from tournament")
  }
