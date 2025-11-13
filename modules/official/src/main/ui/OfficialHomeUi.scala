package lila.official
package ui

import lila.ui.*
import ScalatagsTemplate.{ *, given }

final class OfficialHomeUi(helpers: Helpers):
  import helpers.{ *, given }

  def apply(tournaments: List[OfficialTournament])(using Context): Page =
    Page("Official Tournaments")
      .css("official.home")
      .js(Esm("official")):
        main(cls := "official-home page-small box")(
          h1(cls := "box__top")("Official Tournaments"),
          div(cls := "official-home__create")(
            a(cls := "button", href := routes.OfficialTournament.form)(
              "Create New Tournament"
            )
          ),
          div(cls := "official-home__list")(
            if tournaments.isEmpty then
              p(cls := "official-home__empty")("No tournaments yet. Create the first one!")
            else
              tournaments.map: tournament =>
                div(cls := "official-home__tournament")(
                  a(cls := "official-home__link", href := routes.OfficialTournament.show(tournament.id))(
                    div(cls := "official-home__name")(tournament.name),
                    div(cls := "official-home__info")(
                      span(cls := "official-home__type")(
                        tournament.tournamentType match
                          case OfficialTournamentType.Swiss => "Swiss"
                          case OfficialTournamentType.Arena => "Arena"
                          case OfficialTournamentType.Knockout => "Knockout"
                      ),
                      span(cls := "official-home__players")(
                        s"${tournament.nbPlayers} ",
                        if tournament.nbPlayers == 1 then "player" else "players"
                      ),
                      span(cls := "official-home__status")(
                        if tournament.isCreated then "Starting soon"
                        else if tournament.isStarted then "In progress"
                        else "Finished"
                      )
                    )
                  )
                )
          )
        )
