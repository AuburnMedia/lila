package lila.official

import com.softwaremill.macwire.*

import lila.common.autoconfig.{ *, given }
import play.api.Configuration

@Module
private class OfficialConfig(
    @ConfigName("collection.official") val officialColl: CollName
)

@Module
final class Env(
    appConfig: Configuration,
    db: lila.db.Db,
    helpers: lila.ui.Helpers
)(using Executor):

  private val config = appConfig.get[OfficialConfig]("official")(AutoConfig.loader)

  private lazy val officialColl = db(config.officialColl)

  lazy val mongo = OfficialMongo(officialColl)

  lazy val api: OfficialApi = wire[OfficialApi]

  lazy val forms: OfficialForm = wire[OfficialForm]

  lazy val ui = OfficialUi(helpers)
