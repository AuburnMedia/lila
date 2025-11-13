package lila.official

import chess.Clock.Config as ClockConfig
import chess.{ Rated, variant }
import reactivemongo.api.bson.Macros.Annotations.Key

import lila.core.tournament.Status
import lila.core.id.{ SwissId, TourId }

// Enum for tournament types
enum OfficialTournamentType(val key: String):
  case Swiss extends OfficialTournamentType("swiss")
  case Arena extends OfficialTournamentType("arena")
  case Knockout extends OfficialTournamentType("knockout")

object OfficialTournamentType:
  def fromKey(key: String): Option[OfficialTournamentType] =
    values.find(_.key == key)

// Main Official Tournament model
// This is a wrapper that delegates to the underlying tournament type
case class OfficialTournament(
    @Key("_id") id: OfficialTournamentId,
    name: String,
    tournamentType: OfficialTournamentType,
    // Reference IDs to actual tournament instances
    swissId: Option[SwissId],
    arenaId: Option[TourId],
    knockoutId: Option[KnockoutId],
    // Common fields
    clock: ClockConfig,
    variant: variant.Variant,
    rated: Rated,
    createdAt: Instant,
    createdBy: UserId,
    startsAt: Instant,
    status: Status,
    description: Option[String] = None,
    password: Option[String] = None,
    nbPlayers: Int = 0
):
  def isCreated = status == Status.created
  def isStarted = status == Status.started
  def isFinished = status == Status.finished
  def isEnterable = !isFinished

  def underlyingId: String = tournamentType match
    case OfficialTournamentType.Swiss => swissId.fold("")(_.value)
    case OfficialTournamentType.Arena => arenaId.fold("")(_.value)
    case OfficialTournamentType.Knockout => knockoutId.fold("")(_.value)

  def speed = chess.Speed(clock)

object OfficialTournament:
  def makeId = OfficialTournamentId(ThreadLocalRandom.nextString(8))
