package controllers

import play.api.mvc.*
import play.api.libs.json.*

import lila.app.{ *, given }
import lila.common.HTTPRequest
import lila.official.{ OfficialTournament as OfficialModel, OfficialForm }

final class OfficialTournament(env: Env)(using akka.stream.Materializer) extends LilaController(env):

  private def api = env.official.api
  private def forms = env.official.forms

  def home = Open:
    for
      tournaments <- api.list(1, 20)
      page <- renderPage(views.html.base.notFound) // TODO: Create proper view
    yield Ok(page)

  def form = Auth { ctx ?=> me ?=>
    Ok.page(env.official.forms.create.bindFromRequest())
  }

  def create = AuthBody { ctx ?=> me ?=>
    env.official.forms.create
      .bindFromRequest()
      .fold(
        err => BadRequest.page(err), // TODO: proper form error page
        setup =>
          // TODO: Implement tournament creation
          Redirect(routes.OfficialTournament.home)
      )
  }

  def show(id: lila.official.OfficialTournamentId) = Open:
    api.byId(id).flatMap:
      case None => NotFound.page(views.html.base.notFound)
      case Some(tournament) =>
        // TODO: Render proper tournament view
        Ok.page(views.html.base.notFound)

  def join(id: lila.official.OfficialTournamentId) = Auth { ctx ?=> me ?=>
    // TODO: Implement join logic
    Redirect(routes.OfficialTournament.show(id))
  }

  def withdraw(id: lila.official.OfficialTournamentId) = Auth { ctx ?=> me ?=>
    // TODO: Implement withdraw logic
    Redirect(routes.OfficialTournament.show(id))
  }
