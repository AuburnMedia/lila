package lila.official

import com.softwaremill.macwire.*

import lila.common.autoconfig.{ *, given }
import lila.core.config.CollName
import lila.core.socket.{ GetVersion, SocketVersion }
import lila.db.dsl.Coll
import play.api.Configuration

@Module
private class OfficialConfig(
    @ConfigName("collection.official") val officialColl: CollName
)

@Module
final class Env(
    appConfig: Configuration,
    db: lila.db.Db,
    helpers: lila.ui.Helpers,
    socketKit: lila.core.socket.SocketKit,
    chat: lila.core.chat.ChatApi,
    knockoutEnv: lila.knockout.Env
)(using
    Executor,
    akka.actor.ActorSystem,
    Scheduler
):

  private val config = appConfig.get[OfficialConfig]("official")(using AutoConfig.loader)

  private lazy val officialColl = db(config.officialColl)

  lazy val mongo = OfficialMongo(officialColl)

  lazy val api: OfficialApi = wire[OfficialApi]

  lazy val forms: OfficialForm = wire[OfficialForm]

  lazy val ui = OfficialUi(helpers)

  private lazy val socket = wire[OfficialSocket]
  
  private lazy val knockoutApi = knockoutEnv.api

  def version(id: OfficialTournamentId): Fu[SocketVersion] =
    socket.rooms.ask[SocketVersion](id.value.into(RoomId))(GetVersion.apply)

  def reload(id: OfficialTournamentId): Unit = socket.reload(id)
