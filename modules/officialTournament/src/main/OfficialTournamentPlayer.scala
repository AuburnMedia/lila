package lila.officialTournament

import reactivemongo.api.bson.*
import scalalib.ThreadLocalRandom
import chess.IntRating

import lila.db.dsl.{ *, given }

final class OfficialTournamentPlayerRepo(val coll: Coll)(using Executor):

  import BsonHandlers.{ *, given }

  private given BSONDocumentHandler[OfficialTournamentPlayer] = Macros.handler

  def insert(player: OfficialTournamentPlayer): Funit =
    coll.insert.one(player).void

  def remove(tourId: OfficialTournamentId, userId: UserId): Funit =
    coll.delete.one($doc("tourId" -> tourId, "userId" -> userId)).void

  def byId(id: OfficialTournamentPlayer.Id): Fu[Option[OfficialTournamentPlayer]] =
    coll.byId[OfficialTournamentPlayer](id.value)

  def byTourAndUser(tourId: OfficialTournamentId, userId: UserId): Fu[Option[OfficialTournamentPlayer]] =
    coll.one[OfficialTournamentPlayer]($doc("tourId" -> tourId, "userId" -> userId))

  def playersByTour(tourId: OfficialTournamentId): Fu[List[OfficialTournamentPlayer]] =
    coll
      .find($doc("tourId" -> tourId))
      .cursor[OfficialTournamentPlayer]()
      .list(200)

  def rankedPlayers(tourId: OfficialTournamentId): Fu[List[OfficialTournamentPlayer]] =
    coll
      .find($doc("tourId" -> tourId))
      .sort($sort.desc("rating"))
      .cursor[OfficialTournamentPlayer]()
      .list(200)

  def countByTour(tourId: OfficialTournamentId): Fu[Int] =
    coll.countSel($doc("tourId" -> tourId))

  def updateScore(id: OfficialTournamentPlayer.Id, score: Int): Funit =
    coll.updateField($id(id.value), "score", score).void

  def updatePerformance(id: OfficialTournamentPlayer.Id, perf: Int): Funit =
    coll.updateField($id(id.value), "performance", perf).void

  def updateSeed(id: OfficialTournamentPlayer.Id, seed: Int): Funit =
    coll.updateField($id(id.value), "seed", seed).void

  def setSeedsForTour(tourId: OfficialTournamentId): Funit =
    rankedPlayers(tourId).flatMap: players =>
      players.zipWithIndex.map: (player, index) =>
        updateSeed(player.id, index + 1)
      .parallel.void

  def topPlayers(tourId: OfficialTournamentId, nb: Int): Fu[List[OfficialTournamentPlayer]] =
    coll
      .find($doc("tourId" -> tourId))
      .sort($sort.desc("score") ++ $sort.desc("performance"))
      .cursor[OfficialTournamentPlayer]()
      .list(nb)

// Player model
case class OfficialTournamentPlayer(
    id: OfficialTournamentPlayer.Id,
    tourId: OfficialTournamentId,
    userId: UserId,
    rating: IntRating,
    score: Int,
    performance: Option[Int],
    seed: Int,
    joinedAt: Instant
)

object OfficialTournamentPlayer:
  opaque type Id = String
  object Id extends OpaqueString[Id]:
    def apply(tourId: OfficialTournamentId, userId: UserId): Id =
      Id(s"${tourId.value}:${userId.value}")
  
  def makeId(tourId: OfficialTournamentId, userId: UserId): Id =
    Id(tourId, userId)

  given BSONHandler[Id] = stringAnyValHandler[Id](identity, Id.apply)
