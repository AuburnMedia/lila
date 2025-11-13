package lila.official
package ui

import play.api.libs.json.*

import lila.ui.*
import ScalatagsTemplate.{ *, given }

final class OfficialShowUi(helpers: Helpers):
  import helpers.{ *, given }

  def apply(
      tournament: OfficialTournament,
      data: Option[JsObject] = None
  )(using ctx: Context): Page =
    Page(tournament.name)
      .css("official.show")
      .js(
        PageModule(
          "official",
          Json.obj(
            "data" -> Json.obj(
              "tournament" -> Json.obj(
                "id" -> tournament.id,
                "name" -> tournament.name,
                "tournamentType" -> tournament.tournamentType.key,
                "status" -> tournament.status.toString,
                "nbPlayers" -> tournament.nbPlayers
              )
            ),
            "userId" -> ctx.userId
          )
        )
      ):
        main(cls := "official")(
          div(cls := "official__main")(
            div(cls := "official__info box")(
              h1(tournament.name),
              div(cls := "official__type")(
                tournament.tournamentType match
                  case OfficialTournamentType.Swiss => "Swiss Tournament"
                  case OfficialTournamentType.Arena => "Arena Tournament"
                  case OfficialTournamentType.Knockout => "Knockout Tournament"
              ),
              div(cls := "official__details")(
                span(cls := "official__clock")(s"${tournament.clock.limitInMinutes}+${tournament.clock.incrementSeconds}"),
                span(cls := "official__variant")(tournament.variant.name),
                span(cls := "official__rated")(if tournament.rated.yes then "Rated" else "Casual")
              )
            ),
            div(cls := "official__content box")(
              h2("Tournament Details"),
              p("Tournament view coming soon...")
            )
          ),
          aside(cls := "official__side")(
            div(cls := "official__actions box")(
              h2("Actions"),
              if tournament.isCreated then
                postForm(action := routes.OfficialTournament.join(tournament.id))(
                  submitButton(cls := "button")(trans.site.join())
                )
              else if tournament.isStarted then
                postForm(action := routes.OfficialTournament.withdraw(tournament.id))(
                  submitButton(cls := "button button-red")(trans.site.withdraw())
                )
              else
                p("Tournament has finished")
            ),
            div(cls := "official__players box")(
              div(cls := "official__players-count")(
                span(cls := "number")(tournament.nbPlayers),
                span(cls := "label")(if tournament.nbPlayers == 1 then "player" else "players")
              )
            )
          )
        )
