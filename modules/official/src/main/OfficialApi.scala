package lila.official

import lila.db.dsl.{ *, given }

// API for Official Tournament operations
final class OfficialApi(
    mongo: OfficialMongo
)(using Executor):

  import OfficialTournamentType.*

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
