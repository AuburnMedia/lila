package lila.official

import lila.db.dsl.{ *, given }
import chess.Clock.Config as ClockConfig

// API for Official Tournament operations
final class OfficialApi(
    mongo: OfficialMongo,
    knockoutApi: lila.knockout.KnockoutApi,
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
      knockoutId = None,
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
    // Create underlying Knockout if type is Knockout
    val futureKnockoutId = setup.tournamentTypeEnum match
      case Knockout =>
        knockoutApi
          .create(
            name = tournament.name,
            createdBy = createdBy,
            clock = setup.clockConfig,
            variant = setup.realVariant,
            startsAt = tournament.startsAt,
            players = Nil, // Empty for now, players join separately
            seedingMethod = setup.seedingMethod.getOrElse(lila.knockout.SeedingMethod.Random)
          )
          .map(k => Some(k.id))
      case _ => fuccess(None)

    futureKnockoutId.flatMap: knockoutId =>
      val finalTournament = tournament.copy(knockoutId = knockoutId)
      insert(finalTournament).inject(finalTournament)

  // Join tournament
  def join(id: OfficialTournamentId, userId: UserId): Fu[Boolean] =
    byId(id).flatMap:
      case None => fuccess(false)
      case Some(tournament) =>
        if tournament.isEnterable then
          // Join underlying tournament
          val futureJoin = tournament.tournamentType match
            case Knockout =>
              tournament.knockoutId.so: knockoutId =>
                knockoutApi.join(knockoutId, userId).void
            case _ => funit

          futureJoin.flatMap: _ =>
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
          // Withdraw from underlying tournament
          val futureWithdraw = tournament.tournamentType match
            case Knockout =>
              tournament.knockoutId.so: knockoutId =>
                knockoutApi.withdraw(knockoutId, userId).void
            case _ => funit

          futureWithdraw.flatMap: _ =>
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
final class OfficialMongo(val official: Coll)
