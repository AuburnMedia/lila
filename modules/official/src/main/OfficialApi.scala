package lila.official

import lila.db.dsl.{ *, given }

// API for Official Tournament operations
final class OfficialApi(
    swissApi: lila.swiss.SwissApi,
    tournamentApi: lila.tournament.TournamentApi,
    // knockoutApi: lila.knockout.KnockoutApi, // TODO: implement
    mongo: OfficialMongo
)(using Executor):

  import OfficialTournamentType.*

  // Create official tournament by delegating to underlying type
  def create(setup: OfficialSetup, me: Me): Fu[OfficialTournament] =
    setup.tournamentTypeEnum match
      case Swiss =>
        createSwiss(setup, me)
      case Arena =>
        createArena(setup, me)
      case Knockout =>
        // createKnockout(setup, me) // TODO: implement
        fufail("Knockout tournaments not yet implemented")

  private def createSwiss(setup: OfficialSetup, me: Me): Fu[OfficialTournament] =
    for
      // Convert setup to Swiss format
      swissSetup = convertToSwissSetup(setup, me)
      // Create underlying Swiss tournament
      swiss <- swissApi.create(swissSetup, me.userId)
      // Create Official wrapper
      official = OfficialTournament.fromSwiss(swiss)
      // Store in database
      _ <- mongo.official.insert.one(official)
    yield official

  private def createArena(setup: OfficialSetup, me: Me): Fu[OfficialTournament] =
    for
      // Convert setup to Arena format
      arenaSetup = convertToArenaSetup(setup, me)
      // Create underlying Arena tournament
      arena <- tournamentApi.createTournament(arenaSetup, me.userId)
      // Create Official wrapper
      official = OfficialTournament.fromArena(arena)
      // Store in database
      _ <- mongo.official.insert.one(official)
    yield official

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

  // Join tournament
  def join(id: OfficialTournamentId, me: Me): Fu[Boolean] =
    byId(id).flatMap:
      case None => fuccess(false)
      case Some(official) =>
        official.tournamentType match
          case Swiss =>
            official.swissId.so(swissApi.join)
          case Arena =>
            official.arenaId.so(id => tournamentApi.join(id, me, none, none))
          case Knockout =>
            // knockoutApi.join(official.knockoutId.get, me)
            fufail("Knockout not implemented")

  // Helper methods to convert setup to specific formats
  private def convertToSwissSetup(setup: OfficialSetup, me: Me): lila.swiss.SwissForm.SwissData =
    // TODO: Implement proper conversion
    ???

  private def convertToArenaSetup(setup: OfficialSetup, me: Me): lila.tournament.TournamentSetup =
    // TODO: Implement proper conversion
    ???

// MongoDB access
final class OfficialMongo(val official: lila.db.AsyncColl)
