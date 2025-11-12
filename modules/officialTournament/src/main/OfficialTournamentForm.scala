package lila.officialTournament

import chess.Clock.{ Config as ClockConfig, IncrementSeconds, LimitSeconds }
import chess.format.Fen
import chess.{ Rated, Speed }
import play.api.data.*
import play.api.data.Forms.*

import lila.common.Form.{ *, given }
import lila.tournament.TournamentCondition

final class OfficialTournamentForm(using mode: play.api.Mode):

  import OfficialTournamentForm.*

  def create(user: User): Form[TournamentSetupData] =
    Form(
      mapping(
        "name" -> optional(eventName(2, 30, user.isVerifiedOrAdmin)),
        "format" -> text.verifying(formatIn),
        "clock" -> mapping(
          "limit" -> number.into[LimitSeconds].verifying(clockLimits.contains),
          "increment" -> number(min = 0, max = 120).into[IncrementSeconds]
        )(ClockConfig.apply)(unapply)
          .verifying("Invalid clock", _.estimateTotalSeconds > 0),
        "variant" -> optional(typeIn(chess.variant.Variant.list.all.map(_.key).toSet)),
        "rated" -> optional(boolean.into[Rated]),
        "startsAt" -> optional(inTheFuture(ISOInstantOrTimestamp.mapping)),
        "description" -> optional(cleanNonEmptyText),
        "position" -> optional(lila.common.Form.fen.playableStrict),
        "password" -> optional(cleanNonEmptyText),
        "hasChat" -> optional(boolean),
        "conditions" -> TournamentCondition.form.all(Nil),
        // Swiss-specific
        "swissRounds" -> optional(number(min = 3, max = 100)),
        "roundInterval" -> optional(numberIn(roundIntervalSeconds)),
        // Arena-specific
        "arenaMinutes" -> optional(numberIn(arenaMinuteChoices.map(_._1)))
      )(TournamentSetupData.apply)(unapply)
        .verifying("Invalid clock", _.validClock)
        .verifying("15s and 0+1 variant games cannot be rated", _.validRatedVariant)
        .verifying("This format requires specific fields", _.hasRequiredFormatFields)
    ).fill(
      TournamentSetupData(
        name = None,
        format = "swiss",
        clock = ClockConfig(LimitSeconds(180), IncrementSeconds(0)),
        variant = chess.variant.Standard.key.some,
        rated = Rated.Yes.some,
        startsAt = Some(nowInstant.plusMinutes(if mode.isProd then 10 else 2)),
        description = None,
        position = None,
        password = None,
        hasChat = true.some,
        conditions = TournamentCondition.All.empty,
        swissRounds = 7.some,
        roundInterval = 60.some,
        arenaMinutes = 45.some
      )
    )

  def edit(user: User, tournament: OfficialTournament): Form[TournamentSetupData] =
    create(user).fill(TournamentSetupData.fromTournament(tournament))

object OfficialTournamentForm:

  val clockLimits = LimitSeconds.from(
    Seq(0, 15, 30, 45, 60, 90) ++ {
      (120 to 480 by 60) ++ (600 to 2700 by 300) ++ (3000 to 10800 by 600)
    }
  )

  val clockLimitChoices = options(
    LimitSeconds.raw(clockLimits),
    l =>
      s"${chess.Clock.Config(LimitSeconds(l), IncrementSeconds(0)).limitString}${
          if l <= 1 then " minute" else " minutes"
        }"
  )

  val roundIntervalSeconds: Seq[Int] = Seq(
    5, 10, 20, 30, 45, 60, 120, 180, 300, 600, 900, 1200, 1800, 2700, 3600
  )

  val roundIntervalChoices = options(
    roundIntervalSeconds,
    s =>
      if s < 60 then s"$s seconds"
      else if s < 3600 then s"${s / 60} minute(s)"
      else s"${s / 3600} hour(s)"
  )

  val arenaMinuteChoices = List(
    20 -> "20 minutes",
    25 -> "25 minutes",
    30 -> "30 minutes",
    35 -> "35 minutes",
    40 -> "40 minutes",
    45 -> "45 minutes",
    50 -> "50 minutes",
    60 -> "60 minutes",
    90 -> "90 minutes",
    120 -> "2 hours",
    180 -> "3 hours"
  )

  val formatChoices = List(
    "swiss" -> "Swiss",
    "arena" -> "Arena",
    "knockout" -> "Knockout"
  )

  private def formatIn(s: String) = formatChoices.map(_._1).contains(s)

  case class TournamentSetupData(
      name: Option[String],
      format: String,
      clock: ClockConfig,
      variant: Option[chess.variant.Variant.LilaKey],
      rated: Option[Rated],
      startsAt: Option[Instant],
      description: Option[String],
      position: Option[Fen.Full],
      password: Option[String],
      hasChat: Option[Boolean],
      conditions: TournamentCondition.All,
      // Swiss-specific
      swissRounds: Option[Int],
      roundInterval: Option[Int],
      // Arena-specific
      arenaMinutes: Option[Int]
  ):
    def realVariant = chess.variant.Variant.orDefault(variant)
    def realRated = rated.getOrElse(Rated.Yes)
    def realStartsAt = startsAt.getOrElse(nowInstant.plusMinutes(10))
    def realHasChat = hasChat.getOrElse(true)

    def tournamentFormat: TournamentFormat = format match
      case "swiss" => TournamentFormat.Swiss
      case "arena" => TournamentFormat.Arena
      case "knockout" => TournamentFormat.Knockout
      case _ => TournamentFormat.Swiss

    def validClock = (clock.limitInMinutes + clock.incrementSeconds.value) > 0

    def validRatedVariant =
      !realRated.yes || lila.core.game.allowRated(realVariant, clock.some)

    def hasRequiredFormatFields: Boolean = format match
      case "swiss" => swissRounds.isDefined && roundInterval.isDefined
      case "arena" => arenaMinutes.isDefined
      case "knockout" => true // No extra required fields
      case _ => false

    def toSetup: TournamentSetup = TournamentSetup(
      name = name,
      format = tournamentFormat,
      clock = clock,
      variant = realVariant,
      rated = realRated,
      startsAt = realStartsAt,
      description = description,
      position = position.filter(_ => realVariant.standard),
      password = password,
      conditions = conditions,
      hasChat = realHasChat,
      swissRounds = swissRounds,
      roundInterval = roundInterval.map(_.seconds),
      arenaMinutes = arenaMinutes
    )

  object TournamentSetupData:
    def fromTournament(tournament: OfficialTournament): TournamentSetupData =
      val formatStr = tournament.format match
        case TournamentFormat.Swiss => "swiss"
        case TournamentFormat.Arena => "arena"
        case TournamentFormat.Knockout => "knockout"

      TournamentSetupData(
        name = tournament.name.some,
        format = formatStr,
        clock = tournament.clock,
        variant = tournament.variant.key.some,
        rated = tournament.rated.some,
        startsAt = tournament.startsAt.some,
        description = tournament.settings.description,
        position = tournament.settings.position,
        password = tournament.settings.password,
        hasChat = tournament.settings.hasChat.some,
        conditions = tournament.settings.conditions,
        swissRounds = tournament.formatData.asSwiss.map(_.nbRounds),
        roundInterval = tournament.formatData.asSwiss.map(_.roundInterval.toSeconds.toInt),
        arenaMinutes = tournament.formatData.asArena.map(_.minutes)
      )

  val joinForm = Form(
    single(
      "password" -> optional(nonEmptyText)
    )
  )

  case class JoinData(password: Option[String])
