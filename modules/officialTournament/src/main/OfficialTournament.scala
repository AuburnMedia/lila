package lila.officialTournament

import chess.Clock.Config as ClockConfig
import chess.format.Fen
import chess.{ Rated, Speed, IntRating }
import scalalib.ThreadLocalRandom
import scala.concurrent.duration.FiniteDuration

import lila.core.id.{ OfficialTournamentId, TourId, SwissId }
import lila.core.tournament.Status as TournamentStatus
import lila.gathering.Condition
import lila.rating.PerfType
import lila.tournament.TournamentCondition

// Main tournament model that unifies Swiss, Arena, and Knockout formats
case class OfficialTournament(
    id: OfficialTournamentId,
    name: String,
    format: TournamentFormat,
    status: TournamentStatus,
    clock: ClockConfig,
    variant: chess.variant.Variant,
    rated: Rated,
    startsAt: Instant,
    createdAt: Instant,
    createdBy: UserId,
    nbPlayers: Int,
    settings: OfficialTournament.Settings,
    winnerId: Option[UserId] = None,
    finishedAt: Option[Instant] = None,
    featuredGameId: Option[GameId] = None,
    // Format-specific data
    formatData: FormatData
):
  def isCreated = status == TournamentStatus.created
  def isStarted = status == TournamentStatus.started
  def isFinished = status == TournamentStatus.finished
  def isNotFinished = !isFinished
  def isNowOrSoon = startsAt.isBefore(nowInstant.plusMinutes(15)) && !isFinished

  def speed: Speed = Speed(clock)
  def perfType: PerfType = lila.rating.PerfType(variant, speed)

  def isPrivate: Boolean = settings.password.isDefined

  def estimatedDuration: FiniteDuration =
    format match
      case TournamentFormat.Swiss =>
        formatData.asSwiss.fold(60.minutes): data =>
          val roundDuration = (clock.limit.toSeconds + clock.increment.toSeconds * 80 + 10).toInt
          (roundDuration * data.nbRounds).seconds
      case TournamentFormat.Arena =>
        formatData.asArena.fold(60.minutes)(_.minutes.minutes)
      case TournamentFormat.Knockout =>
        formatData.asKnockout.fold(60.minutes): data =>
          val roundDuration = (clock.limit.toSeconds + clock.increment.toSeconds * 80 + 30).toInt
          (roundDuration * data.maxRounds).seconds

  def finishesAt: Instant = startsAt.plus(estimatedDuration)

  def withFormatData(data: FormatData): OfficialTournament = copy(formatData = data)

  def start: OfficialTournament = copy(status = TournamentStatus.started)

  def finish(winner: Option[UserId]): OfficialTournament =
    copy(
      status = TournamentStatus.finished,
      winnerId = winner,
      finishedAt = Some(nowInstant)
    )

object OfficialTournament:

  val maxPlayers = 4000
  val maxKnockoutPlayers = 256 // For single elimination, more manageable size

  case class Settings(
      description: Option[String] = None,
      position: Option[Fen.Full] = None,
      password: Option[String] = None,
      conditions: TournamentCondition.All,
      hasChat: Boolean = true
  )

  def makeId: OfficialTournamentId = OfficialTournamentId(ThreadLocalRandom.nextString(8))

// Tournament format types
enum TournamentFormat:
  case Swiss, Arena, Knockout

  def name: String = this match
    case Swiss => "Swiss"
    case Arena => "Arena"
    case Knockout => "Knockout"

// Format-specific data
sealed trait FormatData:
  def asSwiss: Option[SwissData] = this match
    case d: SwissData => Some(d)
    case _ => None

  def asArena: Option[ArenaData] = this match
    case d: ArenaData => Some(d)
    case _ => None

  def asKnockout: Option[KnockoutData] = this match
    case d: KnockoutData => Some(d)
    case _ => None

case class SwissData(
    round: Int, // Current round (0 = not started)
    nbRounds: Int,
    nextRoundAt: Option[Instant],
    roundInterval: FiniteDuration,
    // Reference to underlying Swiss tournament for delegation
    swissId: Option[SwissId] = None
) extends FormatData:
  def isLastRound: Boolean = round == nbRounds
  def advanceRound: SwissData = copy(round = round + 1, nextRoundAt = None)

case class ArenaData(
    minutes: Int,
    // Reference to underlying Arena tournament for delegation
    tournamentId: Option[TourId] = None
) extends FormatData

case class KnockoutData(
    round: KnockoutRound,
    maxRounds: Int, // Total rounds needed (log2 of players)
    bracket: Bracket,
    nextRoundAt: Option[Instant]
) extends FormatData:
  def isLastRound: Boolean = round.value == maxRounds
  def advanceRound: KnockoutData = copy(round = round.next, nextRoundAt = None)

// Knockout-specific models
case class Bracket(
    matches: List[Match],
    currentRound: KnockoutRound
):
  def matchesForRound(round: KnockoutRound): List[Match] =
    matches.filter(_.round == round)

  def withMatch(m: Match): Bracket =
    copy(matches = matches.filterNot(_.id == m.id) :+ m)

  def remainingPlayers: Int =
    matchesForRound(currentRound).count(_.isActive)

case class Match(
    id: MatchId,
    round: KnockoutRound,
    player1: Option[BracketPlayer],
    player2: Option[BracketPlayer],
    winnerId: Option[UserId] = None,
    gameId: Option[GameId] = None,
    status: MatchStatus = MatchStatus.Pending
):
  def isActive: Boolean = status != MatchStatus.Completed && !hasBye
  def hasBye: Boolean = player1.isEmpty || player2.isEmpty || player1.exists(_.isBye) || player2.exists(_.isBye)
  def isPending: Boolean = status == MatchStatus.Pending
  def isInProgress: Boolean = status == MatchStatus.InProgress
  def isCompleted: Boolean = status == MatchStatus.Completed

  def withWinner(userId: UserId): Match =
    copy(winnerId = Some(userId), status = MatchStatus.Completed)

  def withGame(gId: GameId): Match =
    copy(gameId = Some(gId), status = MatchStatus.InProgress)

  def getWinner: Option[BracketPlayer] =
    winnerId.flatMap: wId =>
      player1.filter(_.userId == wId).orElse(player2.filter(_.userId == wId))

  // Auto-complete matches with byes
  def autoComplete: Match =
    if hasBye then
      val winner = player1.flatMap(p => if p.isBye then None else Some(p.userId))
        .orElse(player2.flatMap(p => if p.isBye then None else Some(p.userId)))
      copy(winnerId = winner, status = MatchStatus.Completed)
    else this

case class BracketPlayer(
    userId: UserId,
    rating: IntRating,
    seed: Int,
    isBye: Boolean = false
)

enum MatchStatus:
  case Pending, InProgress, Completed

  def name: String = this match
    case Pending => "pending"
    case InProgress => "playing"
    case Completed => "completed"

object MatchStatus:
  def fromString(s: String): Option[MatchStatus] =
    s.toLowerCase match
      case "pending" => Some(Pending)
      case "playing" | "inprogress" => Some(InProgress)
      case "completed" | "finished" => Some(Completed)
      case _ => None
