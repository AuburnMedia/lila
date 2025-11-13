package lila.official

import chess.Clock.Config as ClockConfig
import chess.{ Rated, variant }
import reactivemongo.api.bson.Macros.Annotations.Key

import lila.core.tournament.Status

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
  def makeId = OfficialTournamentId.makeId

  // Factory methods for creating official tournaments
  def fromSwiss(swiss: lila.swiss.Swiss): OfficialTournament =
    OfficialTournament(
      id = makeId,
      name = swiss.name,
      tournamentType = OfficialTournamentType.Swiss,
      swissId = Some(swiss.id),
      arenaId = None,
      knockoutId = None,
      clock = swiss.clock,
      variant = swiss.variant,
      rated = swiss.settings.rated,
      createdAt = swiss.createdAt,
      createdBy = swiss.createdBy,
      startsAt = swiss.startsAt,
      status = swiss.status match
        case lila.swiss.Swiss.Status.created => Status.created
        case lila.swiss.Swiss.Status.started => Status.started
        case lila.swiss.Swiss.Status.finished => Status.finished
      ,
      description = swiss.settings.description,
      password = swiss.settings.password,
      nbPlayers = swiss.nbPlayers
    )

  def fromArena(arena: lila.tournament.Tournament): OfficialTournament =
    OfficialTournament(
      id = makeId,
      name = arena.name,
      tournamentType = OfficialTournamentType.Arena,
      swissId = None,
      arenaId = Some(arena.id),
      knockoutId = None,
      clock = arena.clock,
      variant = arena.variant,
      rated = arena.rated,
      createdAt = arena.createdAt,
      createdBy = arena.createdBy,
      startsAt = arena.startsAt,
      status = arena.status,
      description = arena.description,
      password = arena.password,
      nbPlayers = arena.nbPlayers
    )

  // TODO: Implement when Knockout module is ready
  // def fromKnockout(knockout: lila.knockout.Knockout): OfficialTournament = ???
