package lila.official

import play.api.data.*
import play.api.data.Forms.*
import chess.Clock.{ IncrementSeconds, LimitSeconds }
import chess.{ Clock, Rated }

import lila.common.Form.{ *, given }
import lila.gathering.GatheringClock

final class OfficialForm:

  import OfficialForm.*
  import GatheringClock.*

  def create(using me: Me) = form.fill(empty)

  private def empty(using me: Me) = OfficialSetup(
    tournamentType = OfficialTournamentType.Swiss.key,
    name = me.titleUsername.some,
    clockTime = timeDefault,
    clockIncrement = incrementDefault,
    // Swiss-specific
    nbRounds = nbRoundsDefault.some,
    roundInterval = roundIntervalDefault.some,
    // Arena-specific  
    minutes = minuteDefault.some,
    waitMinutes = waitMinuteDefault.some,
    // Knockout-specific
    seedingMethod = SeedingMethod.Rating.key.some,
    // Common
    startDate = none,
    variant = chess.variant.Standard.id.toString.some,
    position = None,
    password = None,
    rated = Rated.Yes.some,
    description = none,
    hasChat = true.some
  )

  private def form(using Me) = Form:
    mapping(
      "tournamentType" -> nonEmptyText.verifying(t =>
        OfficialTournamentType.fromKey(t).isDefined
      ),
      "name" -> optional(eventName(2, 30)),
      "clockTime" -> numberInDouble(timeChoices),
      "clockIncrement" -> numberIn(incrementChoices).into[IncrementSeconds],
      // Swiss-specific
      "nbRounds" -> optional(number),
      "roundInterval" -> optional(number),
      // Arena-specific
      "minutes" -> optional(number),
      "waitMinutes" -> optional(number),
      // Knockout-specific
      "seedingMethod" -> optional(text),
      // Common
      "startDate" -> optional(inTheFuture(ISOInstantOrTimestamp.mapping)),
      "variant" -> optional(text.verifying(v => guessVariant(v).isDefined)),
      "position" -> optional(lila.common.Form.fen.playableStrict),
      "rated" -> optional(boolean.into[Rated]),
      "password" -> optional(cleanNonEmptyText),
      "description" -> optional(cleanNonEmptyText),
      "hasChat" -> optional(boolean)
    )(OfficialSetup.apply)(unapply)
      .verifying("Invalid clock", _.validClock)
      .verifying("Invalid tournament type", _.validType)

object OfficialForm:

  import chess.variant.*

  val nbRoundsDefault = 7
  val roundIntervalDefault = 60 // seconds
  val minuteDefault = 45
  val waitMinuteDefault = 5

  // Seeding methods for Knockout tournaments
  enum SeedingMethod(val key: String):
    case Random extends SeedingMethod("random")
    case Rating extends SeedingMethod("rating")
    case Manual extends SeedingMethod("manual")

  object SeedingMethod:
    def fromKey(key: String): Option[SeedingMethod] =
      values.find(_.key == key)

  val validVariants =
    List(Standard, Chess960, KingOfTheHill, ThreeCheck, Antichess, Atomic, Horde, RacingKings, Crazyhouse)

  def guessVariant(from: String): Option[Variant] =
    validVariants.find: v =>
      v.key.value == from || from.toIntOption.exists(v.id.value == _)

private[official] case class OfficialSetup(
    tournamentType: String,
    name: Option[String],
    clockTime: Double,
    clockIncrement: IncrementSeconds,
    // Swiss-specific
    nbRounds: Option[Int],
    roundInterval: Option[Int],
    // Arena-specific
    minutes: Option[Int],
    waitMinutes: Option[Int],
    // Knockout-specific
    seedingMethod: Option[String],
    // Common
    startDate: Option[Instant],
    variant: Option[String],
    position: Option[chess.format.Fen.Full],
    rated: Option[Rated],
    password: Option[String],
    description: Option[String],
    hasChat: Option[Boolean]
):
  def validClock = (clockTime + clockIncrement.value) > 0

  def validType = OfficialTournamentType.fromKey(tournamentType).isDefined

  def clockConfig = Clock.Config(LimitSeconds((clockTime * 60).toInt), clockIncrement)

  def realVariant = variant.flatMap(OfficialForm.guessVariant) | chess.variant.Standard

  def realRated: Rated = rated | Rated.Yes

  def tournamentTypeEnum: OfficialTournamentType =
    OfficialTournamentType.fromKey(tournamentType).get
