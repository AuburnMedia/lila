package lila.officialTournament

import reactivemongo.api.*
import reactivemongo.api.bson.*

import lila.core.id.{ SwissId, TourId }
import lila.core.tournament.Status as TournamentStatus
import lila.db.dsl.{ *, given }

final class OfficialTournamentRepo(val coll: Coll)(using Executor):

  import BsonHandlers.{ *, given }

  // Basic CRUD operations
  def byId(id: OfficialTournamentId): Fu[Option[OfficialTournament]] =
    coll.byId[OfficialTournament](id)

  def insert(tournament: OfficialTournament): Funit =
    coll.insert.one(tournament).void

  def update(tournament: OfficialTournament): Funit =
    coll.update.one($id(tournament.id), tournament).void

  def remove(id: OfficialTournamentId): Funit =
    coll.delete.one($id(id)).void

  // Query methods
  def byStatus(status: TournamentStatus): Fu[List[OfficialTournament]] =
    coll
      .find($doc("status" -> status))
      .sort($sort.desc("startsAt"))
      .cursor[OfficialTournament]()
      .list(100)

  def byFormat(format: TournamentFormat, limit: Int = 50): Fu[List[OfficialTournament]] =
    coll
      .find($doc("format" -> format))
      .sort($sort.desc("startsAt"))
      .cursor[OfficialTournament]()
      .list(limit)

  def byCreator(userId: UserId, limit: Int = 20): Fu[List[OfficialTournament]] =
    coll
      .find($doc("createdBy" -> userId))
      .sort($sort.desc("createdAt"))
      .cursor[OfficialTournament]()
      .list(limit)

  def findUpcoming(limit: Int = 20): Fu[List[OfficialTournament]] =
    coll
      .find(
        $doc(
          "status" -> TournamentStatus.created,
          "startsAt" -> $doc("$gte" -> nowInstant, "$lte" -> nowInstant.plusDays(7))
        )
      )
      .sort($sort.asc("startsAt"))
      .cursor[OfficialTournament]()
      .list(limit)

  def findActive(limit: Int = 20): Fu[List[OfficialTournament]] =
    coll
      .find($doc("status" -> TournamentStatus.started))
      .sort($sort.asc("startsAt"))
      .cursor[OfficialTournament]()
      .list(limit)

  def findRecent(limit: Int = 20): Fu[List[OfficialTournament]] =
    coll
      .find($doc("status" -> TournamentStatus.finished))
      .sort($sort.desc("finishedAt"))
      .cursor[OfficialTournament]()
      .list(limit)

  def findStartingSoon(minutes: Int): Fu[List[OfficialTournament]] =
    coll
      .find(
        $doc(
          "status" -> TournamentStatus.created,
          "startsAt" -> $doc(
            "$gte" -> nowInstant,
            "$lte" -> nowInstant.plusMinutes(minutes)
          )
        )
      )
      .sort($sort.asc("startsAt"))
      .cursor[OfficialTournament]()
      .list(50)

  // Projection methods for efficient queries
  def lightById(id: OfficialTournamentId): Fu[Option[LightTournament]] =
    coll.one[LightTournament](
      $id(id),
      $doc("name" -> true, "format" -> true, "status" -> true, "startsAt" -> true)
    )

  def countByStatus(status: TournamentStatus): Fu[Int] =
    coll.countSel($doc("status" -> status))

  def countByCreator(userId: UserId): Fu[Int] =
    coll.countSel($doc("createdBy" -> userId))

  // Update specific fields
  def setStatus(id: OfficialTournamentId, status: TournamentStatus): Funit =
    coll.updateField($id(id), "status", status).void

  def setNbPlayers(id: OfficialTournamentId, nbPlayers: Int): Funit =
    coll.updateField($id(id), "nbPlayers", nbPlayers).void

  def setWinner(id: OfficialTournamentId, userId: UserId): Funit =
    coll.update
      .one(
        $id(id),
        $set(
          "winnerId" -> userId,
          "status" -> TournamentStatus.finished,
          "finishedAt" -> nowInstant
        )
      )
      .void

  def setFeaturedGame(id: OfficialTournamentId, gameId: GameId): Funit =
    coll.updateField($id(id), "featuredGameId", gameId).void

  def updateFormatData(id: OfficialTournamentId, data: FormatData): Funit =
    coll.updateField($id(id), "formatData", data).void

  // Knockout-specific updates
  def updateBracket(id: OfficialTournamentId, bracket: Bracket): Funit =
    coll.update
      .one(
        $doc("_id" -> id, "format" -> TournamentFormat.Knockout),
        $set("formatData.bracket" -> bracket)
      )
      .void

  def updateMatch(id: OfficialTournamentId, matchId: MatchId, update: Match => Match): Fu[Option[Match]] =
    byId(id).flatMap:
      case Some(tournament) =>
        tournament.formatData.asKnockout match
          case Some(knockoutData) =>
            knockoutData.bracket.matches.find(_.id == matchId) match
              case Some(oldMatch) =>
                val newMatch = update(oldMatch)
                val newBracket = knockoutData.bracket.withMatch(newMatch)
                updateBracket(id, newBracket).inject(newMatch.some)
              case None => fuccess(none)
          case None => fuccess(none)
      case None => fuccess(none)

  def advanceKnockoutRound(id: OfficialTournamentId): Funit =
    byId(id).flatMap:
      case Some(tournament) =>
        tournament.formatData.asKnockout match
          case Some(data) =>
            val newData = data.advanceRound
            updateFormatData(id, newData)
          case None => funit
      case None => funit

  // Swiss-specific delegation reference
  def setSwissId(id: OfficialTournamentId, swissId: SwissId): Funit =
    coll.update
      .one(
        $doc("_id" -> id, "format" -> TournamentFormat.Swiss),
        $set("formatData.swissId" -> swissId)
      )
      .void

  // Arena-specific delegation reference
  def setTournamentId(id: OfficialTournamentId, tournamentId: TourId): Funit =
    coll.update
      .one(
        $doc("_id" -> id, "format" -> TournamentFormat.Arena),
        $set("formatData.tournamentId" -> tournamentId)
      )
      .void

  // Aggregation for statistics
  def statsGroupedByFormat: Fu[Map[TournamentFormat, Int]] =
    import reactivemongo.api.bson.collection.BSONCollection
    coll
      .aggregateWith[Bdoc](): framework =>
        import framework.*
        List(
          Group(BSONString("$format"))("count" -> SumAll)
        )
      .collect[List]()
      .map: docs =>
        docs.flatMap: doc =>
          for
            format <- doc.getAsOpt[TournamentFormat]("_id")
            count <- doc.int("count")
          yield format -> count
        .toMap

  // Search by name
  def search(term: String, limit: Int = 10): Fu[List[OfficialTournament]] =
    coll
      .find(
        $doc(
          "name" -> $doc("$regex" -> BSONRegex(s"(?i)${java.util.regex.Pattern.quote(term)}", ""))
        )
      )
      .sort($sort.desc("startsAt"))
      .cursor[OfficialTournament]()
      .list(limit)

  // Check if user can create more tournaments (rate limiting)
  def recentCreationCount(userId: UserId, since: Instant): Fu[Int] =
    coll.countSel(
      $doc(
        "createdBy" -> userId,
        "createdAt" -> $doc("$gte" -> since)
      )
    )

  // Cleanup old finished tournaments (for maintenance)
  def removeOldFinished(olderThan: Instant): Fu[Int] =
    coll.delete
      .one(
        $doc(
          "status" -> TournamentStatus.finished,
          "finishedAt" -> $doc("$lt" -> olderThan)
        )
      )
      .map(_.n)

// Light projection for lists
case class LightTournament(
    id: OfficialTournamentId,
    name: String,
    format: TournamentFormat,
    status: TournamentStatus,
    startsAt: Instant
)

object LightTournament:
  import BsonHandlers.given
  given BSONDocumentReader[LightTournament] = Macros.reader[LightTournament]
