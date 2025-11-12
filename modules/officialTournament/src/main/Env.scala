package lila.officialTournament

import com.softwaremill.macwire.*
import play.api.Configuration

import lila.common.Bus
import lila.core.config.*
import lila.db.dsl.Coll

@Module
final class Env(
    appConfig: Configuration,
    db: lila.db.Db,
    userApi: lila.core.user.UserApi,
    gameRepo: lila.core.game.GameRepo,
    swissApi: lila.core.swiss.SwissApi,
    tournamentApi: lila.core.tournament.TournamentApi,
    cacheApi: lila.memo.CacheApi,
    lightUserApi: lila.core.user.LightUserApi,
    historyApi: lila.core.history.HistoryApi
)(using
    ex: Executor,
    scheduler: Scheduler,
    system: akka.actor.ActorSystem,
    mat: akka.stream.Materializer,
    mode: play.api.Mode
):

  private val mongo = new OfficialTournamentMongo(
    tournament = db(CollName("official_tournament")),
    player = db(CollName("official_tournament_player"))
  )

  private val repo: OfficialTournamentRepo = new OfficialTournamentRepo(mongo.tournament)
  
  private val playerRepo: OfficialTournamentPlayerRepo = 
    new OfficialTournamentPlayerRepo(mongo.player)

  lazy val verify = lila.tournament.TournamentCondition.Verify(historyApi, userApi)

  val api: OfficialTournamentApi = wire[OfficialTournamentApi]

  val form: OfficialTournamentForm = wire[OfficialTournamentForm]

  val json: OfficialTournamentJson = wire[OfficialTournamentJson]

  // Start scheduled tasks
  scheduler.scheduleWithFixedDelay(1.minute, 1.minute): () =>
    checkStartingTournaments()

  scheduler.scheduleWithFixedDelay(30.seconds, 30.seconds): () =>
    checkKnockoutRoundAdvancement()

  private def checkStartingTournaments(): Unit =
    repo.findStartingSoon(minutes = 1).foreach: tournaments =>
      tournaments.foreach: tournament =>
        api.start(tournament.id).map(_ => ())

  private def checkKnockoutRoundAdvancement(): Unit =
    repo.findActive(limit = 100).foreach: tournaments =>
      tournaments.filter(_.format == TournamentFormat.Knockout).foreach: tournament =>
        api.advanceKnockoutRound(tournament.id).map(_ => ())

private class OfficialTournamentMongo(
    val tournament: Coll,
    val player: Coll
)
