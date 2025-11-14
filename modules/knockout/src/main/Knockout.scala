package lila.knockout

import chess.Clock.Config as ClockConfig
import chess.format.Fen
import chess.{ Rated, Speed }
import reactivemongo.api.bson.Macros.Annotations.Key
import scalalib.ThreadLocalRandom

import lila.core.id.KnockoutId
import lila.core.tournament.Status
import lila.rating.PerfType

// Status specific to knockout tournaments
enum KnockoutStatus:
  case Created, Started, RoundInProgress, RoundComplete, Finished

// Match in a knockout tournament
case class KnockoutMatch(
    id: MatchId,
    knockoutId: KnockoutId,
    round: KnockoutRoundNumber,
    position: Int, // Position in the bracket
    player1: Option[UserId],
    player2: Option[UserId],
    winner: Option[UserId] = None,
    gameId: Option[GameId] = None,
    status: MatchStatus,
    isBye: Boolean = false
):
  def isComplete = winner.isDefined
  def isPending = !isComplete && player1.isDefined && player2.isDefined
  def isWaitingForPlayers = player1.isEmpty || player2.isEmpty

enum MatchStatus:
  case Pending, InProgress, Completed, Bye

// Player state in knockout tournament
case class KnockoutPlayer(
    userId: UserId,
    knockoutId: KnockoutId,
    rating: Int,
    seed: Int,
    currentRound: KnockoutRoundNumber,
    isActive: Boolean = true,
    isEliminated: Boolean = false
)

// Main Knockout tournament model
case class Knockout(
    @Key("_id") id: KnockoutId,
    name: String,
    clock: ClockConfig,
    variant: chess.variant.Variant,
    currentRound: KnockoutRoundNumber, // current round
    totalRounds: Int, // calculated from player count
    nbPlayers: Int,
    createdAt: Instant,
    createdBy: UserId,
    startsAt: Instant,
    seedingMethod: SeedingMethod,
    status: Status,
    winnerId: Option[UserId] = None
):
  def isCreated = status == Status.created
  def isStarted = status == Status.started
  def isFinished = status == Status.finished
  def isEnterable = isCreated && nbPlayers < Knockout.maxPlayers

  def speed = Speed(clock)
  def perfType: PerfType = lila.rating.PerfType(variant, speed)

  def progressPercent: Int =
    if isCreated then 0
    else if isFinished then 100
    else (currentRound.value * 100 / totalRounds).atMost(100)

object Knockout:
  val maxPlayers = 256

  def makeId = KnockoutId(ThreadLocalRandom.nextString(8))

  // Calculate number of rounds needed for given player count
  def calculateRounds(playerCount: Int): Int =
    if playerCount <= 1 then 0
    else math.ceil(math.log(playerCount) / math.log(2)).toInt

  // Calculate bracket size (next power of 2)
  def calculateBracketSize(playerCount: Int): Int =
    if playerCount <= 0 then 0
    else math.pow(2, calculateRounds(playerCount)).toInt

  // Calculate number of byes needed
  def calculateByes(playerCount: Int): Int =
    calculateBracketSize(playerCount) - playerCount

// Seeding method for initial bracket
enum SeedingMethod:
  case Random, Rating, Manual

object SeedingMethod:
  def fromString(s: String): Option[SeedingMethod] = s.toLowerCase match
    case "random" => Some(Random)
    case "rating" => Some(Rating)
    case "manual" => Some(Manual)
    case _ => None
