package lila.knockout

import lila.db.dsl.{ *, given }
import lila.core.id.KnockoutId

final class KnockoutApi(mongo: KnockoutMongo)(using Executor):

  import BsonHandlers.{ *, given }

  def byId(id: KnockoutId): Fu[Option[Knockout]] =
    mongo.knockout.byId[Knockout](id)

  def create(
      name: String,
      createdBy: UserId,
      clock: chess.Clock.Config,
      variant: chess.variant.Variant,
      startsAt: Instant,
      players: List[UserId],
      seedingMethod: SeedingMethod
  ): Fu[Knockout] =
    val knockout = Knockout(
      id = Knockout.makeId,
      name = name,
      createdBy = createdBy,
      createdAt = nowInstant,
      startsAt = startsAt,
      clock = clock,
      variant = variant,
      status = lila.core.tournament.Status.created,
      currentRound = KnockoutRoundNumber(0),
      totalRounds = Knockout.calculateRounds(players.size),
      nbPlayers = players.size,
      winnerId = none,
      seedingMethod = seedingMethod
    )
    mongo.knockout.insert.one(knockout)(using BsonHandlers.given_BSONDocumentHandler_Knockout).inject(knockout)

  def join(id: KnockoutId, userId: UserId): Fu[Option[Knockout]] =
    byId(id).flatMap:
      case None => fuccess(none)
      case Some(knockout) =>
        if knockout.status != lila.core.tournament.Status.created then fuccess(knockout.some)
        else
          val player = KnockoutPlayer(
            userId = userId,
            knockoutId = id,
            rating = 1500, // TODO: get actual rating
            seed = knockout.nbPlayers + 1,
            currentRound = KnockoutRoundNumber(0),
            isEliminated = false
          )
          for
            _ <- mongo.knockoutPlayer.insert.one(player)(using BsonHandlers.given_BSONDocumentHandler_KnockoutPlayer)
            updated = knockout.copy(nbPlayers = knockout.nbPlayers + 1)
            _ <- mongo.knockout.update.one($id(id), updated)(using BsonHandlers.given_BSONDocumentHandler_Knockout)
          yield updated.some

  def withdraw(id: KnockoutId, userId: UserId): Fu[Option[Knockout]] =
    byId(id).flatMap:
      case None => fuccess(none)
      case Some(knockout) =>
        if knockout.status != lila.core.tournament.Status.created then fuccess(knockout.some)
        else
          for
            _ <- mongo.knockoutPlayer.delete.one($doc("userId" -> userId, "knockoutId" -> id))
            updated = knockout.copy(nbPlayers = (knockout.nbPlayers - 1).atLeast(0))
            _ <- mongo.knockout.update.one($id(id), updated)(using BsonHandlers.given_BSONDocumentHandler_Knockout)
          yield updated.some

  def start(id: KnockoutId): Fu[Option[Knockout]] =
    byId(id).flatMap:
      case None => fuccess(none)
      case Some(knockout) if knockout.status != lila.core.tournament.Status.created =>
        fuccess(knockout.some)
      case Some(knockout) =>
        // Get all players
        mongo.knockoutPlayer
          .list[KnockoutPlayer]($doc("knockoutId" -> id))
          .flatMap: players =>
            // Generate bracket
            val matches = KnockoutBracket.generateBracket(
              players,
              knockout.seedingMethod
            )
            // Add knockoutId to matches
            val matchesWithId = matches.map: m =>
              m.copy(knockoutId = id)
            for
              _ <- mongo.knockoutMatch.insert.many(matchesWithId)(using BsonHandlers.given_BSONDocumentHandler_KnockoutMatch)
              updated = knockout.copy(
                status = lila.core.tournament.Status.started,
                currentRound = KnockoutRoundNumber(1)
              )
              _ <- mongo.knockout.update.one($id(id), updated)(using BsonHandlers.given_BSONDocumentHandler_Knockout)
            yield updated.some

  def finish(id: KnockoutId, winnerId: UserId): Fu[Option[Knockout]] =
    byId(id).flatMap:
      case None => fuccess(none)
      case Some(knockout) =>
        val updated = knockout.copy(
          status = lila.core.tournament.Status.finished,
          winnerId = winnerId.some
        )
        mongo.knockout.update.one($id(id), updated)(using BsonHandlers.given_BSONDocumentHandler_Knockout).inject(updated.some)

  def list(limit: Int = 50): Fu[List[Knockout]] =
    mongo.knockout.list[Knockout](
      $empty,
      sort = $sort.desc("createdAt"),
      nb = limit
    )

  def matches(id: KnockoutId): Fu[List[KnockoutMatch]] =
    mongo.knockoutMatch.list[KnockoutMatch](
      $doc("knockoutId" -> id),
      sort = $sort.asc("round").thenAsc("position")
    )

  def players(id: KnockoutId): Fu[List[KnockoutPlayer]] =
    mongo.knockoutPlayer.list[KnockoutPlayer]($doc("knockoutId" -> id))
