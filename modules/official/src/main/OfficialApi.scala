package lila.official

import lila.db.dsl.{ *, given }
import chess.Clock.Config as ClockConfig

// API for Official Tournament operations
final class OfficialApi(
    mongo: OfficialMongo,
    reloadSockets: OfficialTournamentId => Unit
)(using Executor):

  import OfficialTournamentType.*

  // Create a new official tournament
  def create(setup: OfficialSetup, createdBy: UserId): Fu[OfficialTournament] =
    val tournament = OfficialTournament(
      id = OfficialTournament.makeId,
      name = setup.name.getOrElse(s"${createdBy.value}'s Tournament"),
      tournamentType = setup.tournamentTypeEnum,
      swissId = None, // TODO: Create underlying Swiss if type is Swiss
      arenaId = None, // TODO: Create underlying Arena if type is Arena
      knockoutId = None, // TODO: Create underlying Knockout if type is Knockout
      clock = setup.clockConfig,
      variant = setup.realVariant,
      rated = setup.realRated,
      createdAt = nowInstant,
      createdBy = createdBy,
      startsAt = setup.startDate.getOrElse(nowInstant.plusMinutes(5)),
      status = lila.core.tournament.Status.created,
      description = setup.description,
      password = setup.password,
      nbPlayers = 0
    )
    insert(tournament).inject(tournament)

  // Join tournament
  def join(id: OfficialTournamentId, userId: UserId): Fu[Boolean] =
    byId(id).flatMap:
      case None => fuccess(false)
      case Some(tournament) =>
        if tournament.isEnterable then
          // TODO: Add player to tournament and underlying Swiss/Arena/Knockout
          // For now just increment player count
          mongo.official
            .update
            .one($id(id.value), $inc("nbPlayers" -> 1))
            .map: result =>
              if result.n > 0 then reloadSockets(id)
              result.n > 0
        else fuccess(false)

  // Withdraw from tournament
  def withdraw(id: OfficialTournamentId, userId: UserId): Fu[Boolean] =
    byId(id).flatMap:
      case None => fuccess(false)
      case Some(tournament) =>
        if tournament.isCreated then
          // TODO: Remove player from tournament and underlying Swiss/Arena/Knockout
          // For now just decrement player count
          mongo.official
            .update
            .one($id(id.value), $inc("nbPlayers" -> -1))
            .map: result =>
              if result.n > 0 then reloadSockets(id)
              result.n > 0
        else fuccess(false)

  // Get official tournament by ID
  def byId(id: OfficialTournamentId): Fu[Option[OfficialTournament]] =
    mongo.official.byId[OfficialTournament](id.value)

  // List official tournaments
  def list(page: Int, perPage: Int = 20): Fu[List[OfficialTournament]] =
    mongo.official
      .find($empty)
      .sort($sort.desc("startsAt"))
      .skip((page - 1) * perPage)
      .cursor[OfficialTournament]()
      .list(perPage)

  // Store official tournament
  def insert(tournament: OfficialTournament): Funit =
    mongo.official.insert.one(tournament).void

// MongoDB access
final class OfficialMongo(val official: lila.db.AsyncColl)
