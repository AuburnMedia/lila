package controllers

import play.api.mvc.*
import views.html

import lila.app.{ *, given }
import lila.common.HTTPRequest
import lila.core.id.OfficialTournamentId
import lila.officialTournament.{ OfficialTournament as OfficialTournamentModel, * }

final class OfficialTournament(env: Env)(using akka.stream.Materializer) extends LilaController(env):

  private def api = env.officialTournament.api
  private def forms = env.officialTournament.form
  private def repo = env.officialTournament.repo

  def home = Open:
    for
      upcoming <- api.findUpcoming(20)
      active <- api.findActive(20)
      recent <- api.findRecent(10)
      // TODO: Create home view
      page <- renderPage:
        html.base.layout(
          title = "Official Tournaments",
          moreCss = cssTag("tournament"),
          moreJs = esmInit("tournament.list")
        ):
          main(cls := "page-small box box-pad")(
            h1("Official Tournaments"),
            div(cls := "tournament__list")(
              h2("Active Tournaments"),
              active.map(t => div(t.name)),
              h2("Upcoming Tournaments"),
              upcoming.map(t => div(t.name)),
              h2("Recent Results"),
              recent.map(t => div(t.name))
            )
          )
    yield Ok(page)

  def show(id: OfficialTournamentId) = Open:
    Found(api.byId(id)): tournament =>
      negotiate(
        html = for
          // TODO: Create show view based on format
          page <- renderPage:
            html.base.layout(
              title = tournament.name,
              moreCss = cssTag("tournament"),
              moreJs = esmInit("tournament.show")
            ):
              main(cls := "tournament")(
                h1(tournament.name),
                p(s"Format: ${tournament.format}"),
                p(s"Status: ${tournament.status}"),
                p(s"Players: ${tournament.nbPlayers}")
              )
        yield Ok(page),
        json = for
          json <- env.officialTournament.json.tournamentJson(tournament)
        yield JsonOk(json)
      )

  def form = Auth: ctx ?=> me ?=>
    NoLameOrBot:
      Ok.page:
        html.base.layout(
          title = "Create Official Tournament",
          moreCss = cssTag("tournament.form"),
          moreJs = esmInit("bits.tourForm")
        ):
          main(cls := "page-small box box-pad")(
            h1("Create Official Tournament"),
            // TODO: Create proper form view
            p("Tournament creation form will be here")
          )

  def create = AuthBody: ctx ?=> me ?=>
    NoLameOrBot:
      forms.create(me).bindFromRequest().fold(
        err =>
          BadRequest.page:
            html.base.layout(
              title = "Create Official Tournament",
              moreCss = cssTag("tournament.form")
            ):
              main(cls := "page-small box box-pad")(
                h1("Create Official Tournament"),
                p("Form has errors")
              )
        ,
        data =>
          api.create(data.toSetup).map: tournament =>
            Redirect(routes.OfficialTournament.show(tournament.id))
      )

  def join(id: OfficialTournamentId) = AuthBody: ctx ?=> me ?=>
    NoLameOrBot:
      given lila.gathering.Condition.GetMyTeamIds = me => env.team.cached.teamIdsList(me.userId)
      forms.joinForm.bindFromRequest().fold(
        _ => BadRequest,
        data =>
          api.join(id, data.password).flatMap:
            case JoinResult.Ok =>
              Redirect(routes.OfficialTournament.show(id))
            case JoinResult.WrongPassword =>
              BadRequest("Wrong password")
            case JoinResult.NotFound =>
              NotFound("Tournament not found")
            case JoinResult.AlreadyStarted =>
              BadRequest("Tournament already started")
            case JoinResult.Full =>
              BadRequest("Tournament is full")
            case JoinResult.ConditionsNotMet(_) =>
              BadRequest("You don't meet the tournament conditions")
      )

  def withdraw(id: OfficialTournamentId) = Auth: ctx ?=> me ?=>
    api.withdraw(id) >> Redirect(routes.OfficialTournament.show(id))

  def edit(id: OfficialTournamentId) = Auth: ctx ?=> me ?=>
    Found(api.byId(id)): tournament =>
      if tournament.createdBy == me.userId || isGranted(_.ManageTournament) then
        Ok.page:
          html.base.layout(
            title = s"Edit ${tournament.name}",
            moreCss = cssTag("tournament.form")
          ):
            main(cls := "page-small box box-pad")(
              h1(s"Edit ${tournament.name}"),
              // TODO: Create edit form view
              p("Edit form will be here")
            )
      else Unauthorized("You can't edit this tournament")

  def update(id: OfficialTournamentId) = AuthBody: ctx ?=> me ?=>
    Found(api.byId(id)): tournament =>
      if tournament.createdBy == me.userId || isGranted(_.ManageTournament) then
        // TODO: Implement update logic
        Redirect(routes.OfficialTournament.show(id))
      else Unauthorized("You can't edit this tournament")

  def terminate(id: OfficialTournamentId) = Auth: ctx ?=> me ?=>
    Found(api.byId(id)): tournament =>
      if tournament.createdBy == me.userId || isGranted(_.ManageTournament) then
        api.cancel(id).map: success =>
          if success then Redirect(routes.OfficialTournament.home)
          else BadRequest("Cannot terminate tournament")
      else Unauthorized("You can't terminate this tournament")

  // API endpoints
  def apiShow(id: OfficialTournamentId) = Anon:
    Found(api.byId(id)): tournament =>
      env.officialTournament.json.tournamentJson(tournament).map(JsonOk(_))

  def apiList(format: Option[String]) = Anon:
    val tournaments = format match
      case Some("swiss") => api.byFormat(TournamentFormat.Swiss, 50)
      case Some("arena") => api.byFormat(TournamentFormat.Arena, 50)
      case Some("knockout") => api.byFormat(TournamentFormat.Knockout, 50)
      case _ => api.findActive(50)
    
    tournaments.flatMap: tours =>
      env.officialTournament.json.tournamentListJson(tours).map(JsonOk(_))

  def apiBracket(id: OfficialTournamentId) = Anon:
    Found(api.byId(id)): tournament =>
      tournament.formatData.asKnockout match
        case Some(knockout) =>
          JsonOk(env.officialTournament.json.bracketJson(knockout))
        case None =>
          BadRequest(jsonError("Not a knockout tournament"))
