package lila.official
package ui

import chess.variant.Variant
import play.api.data.Form

import lila.core.i18n.Translate
import lila.gathering.GatheringClock
import lila.ui.*

import ScalatagsTemplate.{ *, given }

final class OfficialFormUi(helpers: Helpers)(
    translatedVariantChoicesWithVariants: (
        Variant => String
    ) => Translate ?=> List[(String, String, Option[String])]
):
  import helpers.{ *, given }

  def create(form: Form[OfficialSetup])(using Context) =
    Page("Create Official Tournament")
      .css("swiss.form", "tournament.form")
      .js(Esm("bits.tourForm")):
        val fields = new OfficialFields(form)
        main(cls := "page-small")(
          div(cls := "official__form tour__form box box-pad")(
            h1(cls := "box__top")("Create Official Tournament"),
            p(cls := "explanation")(
              "Official Tournaments support three formats: Swiss, Arena, and Knockout. ",
              "Choose your format and configure the tournament settings below."
            ),
            postForm(cls := "form3", action := routes.OfficialTournament.create)(
              fields.tournamentTypeField,
              fields.tournamentFields,
              fields.gameFields,
              fields.formatSpecificFields,
              form3.globalError(form),
              form3.actions(
                a(href := routes.Lobby.home)(trans.site.cancel()),
                form3.submit("Create Tournament", icon = Icon.Trophy.some)
              )
            )
          )
        )

  private final class OfficialFields(form: Form[OfficialSetup])(using Context):

    def tournamentTypeField =
      form3.fieldset("Tournament Format", toggle = true.some)(
        form3.group(form("tournamentType"), frag("Format"), half = false) { f =>
          div(cls := "tournament-type-selector")(
            form3.radio(
              f,
              "swiss",
              label = frag(
                strong("Swiss Tournament"),
                br,
                small("Players face opponents with similar scores. Everyone plays the same number of rounds.")
              )
            ),
            form3.radio(
              f,
              "arena",
              label = frag(
                strong("Arena Tournament"),
                br,
                small("Continuous pairing for a set duration. Play as many games as possible!")
              )
            ),
            form3.radio(
              f,
              "knockout",
              label = frag(
                strong("Knockout Tournament"),
                br,
                small("Single elimination bracket. Lose once and you're out!")
              )
            )
          )
        }
      )

    def tournamentFields =
      form3.fieldset("Tournament Info", toggle = true.some)(
        form3.split(name, startsAt),
        description
      )

    def gameFields =
      form3.fieldset("Game Settings", toggle = true.some)(
        clock,
        form3.split(variant, rated)
      )

    def formatSpecificFields =
      frag(
        // Swiss-specific fields
        div(cls := "format-specific swiss-fields", style := "display: none;")(
          form3.fieldset("Swiss Settings", toggle = false.some)(
            form3.split(nbRounds, roundInterval)
          )
        ),
        // Arena-specific fields
        div(cls := "format-specific arena-fields", style := "display: none;")(
          form3.fieldset("Arena Settings", toggle = false.some)(
            form3.split(minutes, waitMinutes)
          )
        ),
        // Knockout-specific fields
        div(cls := "format-specific knockout-fields", style := "display: none;")(
          form3.fieldset("Knockout Settings", toggle = false.some)(
            seedingMethod
          )
        )
      )

    def name =
      form3.group(form("name"), trans.site.name(), half = true) { f =>
        div(
          form3.input(f),
          small(cls := "form-help")(
            "Give your tournament a memorable name"
          )
        )
      }

    def startsAt =
      form3.group(
        form("startDate"),
        trans.site.startsAt(),
        help = trans.site.inYourLocalTimezone().some,
        half = true
      )(form3.flatpickr(_)())

    def description =
      form3.group(
        form("description"),
        trans.site.tournDescription(),
        help = trans.site.tournDescriptionHelp().some
      )(form3.textarea(_)(rows := 4))

    def clock =
      form3.split(
        form3.group(form("clockTime"), trans.site.clockInitialTime(), half = true)(
          form3.select(_, GatheringClock.timeChoices)
        ),
        form3.group(form("clockIncrement"), trans.site.clockIncrement(), half = true)(
          form3.select(_, GatheringClock.incrementChoices)
        )
      )

    def variant =
      form3.group(form("variant"), trans.site.variant(), half = true)(
        form3.select(
          _,
          translatedVariantChoicesWithVariants(_.key.value).map(x => x._1 -> x._2)
        )
      )

    def rated =
      form3.checkbox(
        form("rated"),
        trans.site.rated(),
        help = trans.site.ratedFormHelp().some,
        half = true
      )

    // Swiss-specific
    def nbRounds =
      form3.group(
        form("nbRounds"),
        frag("Number of Rounds"),
        help = frag("Total number of rounds to play").some,
        half = true
      )(form3.input(_, typ = "number"))

    def roundInterval =
      form3.group(
        form("roundInterval"),
        frag("Round Interval"),
        help = frag("Seconds between rounds").some,
        half = true
      )(form3.input(_, typ = "number"))

    // Arena-specific
    def minutes =
      form3.group(
        form("minutes"),
        trans.site.duration(),
        help = frag("Tournament duration in minutes").some,
        half = true
      )(form3.input(_, typ = "number"))

    def waitMinutes =
      form3.group(
        form("waitMinutes"),
        frag("Wait Time"),
        help = frag("Minutes before tournament starts").some,
        half = true
      )(form3.input(_, typ = "number"))

    // Knockout-specific
    def seedingMethod =
      form3.group(
        form("seedingMethod"),
        frag("Seeding Method"),
        help = frag("How to determine initial bracket positions").some
      ) { f =>
        form3.select(
          f,
          Seq(
            "rating" -> "By Rating (highest rated players get top seeds)",
            "random" -> "Random (completely random bracket)",
            "manual" -> "Manual (you set the seeds)"
          )
        )
      }
