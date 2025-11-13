package lila.knockout

import com.softwaremill.macwire.*

import lila.common.autoconfig.{ *, given }
import lila.core.config.CollName
import play.api.Configuration

@Module
private class KnockoutConfig(
    @ConfigName("collection.knockout") val knockoutColl: CollName,
    @ConfigName("collection.knockout_match") val knockoutMatchColl: CollName,
    @ConfigName("collection.knockout_player") val knockoutPlayerColl: CollName
)

@Module
final class Env(
    appConfig: Configuration,
    db: lila.db.Db
)(using Executor):

  private val config = appConfig.get[KnockoutConfig]("knockout")(AutoConfig.loader)

  private lazy val knockoutColl = db(config.knockoutColl)
  private lazy val knockoutMatchColl = db(config.knockoutMatchColl)
  private lazy val knockoutPlayerColl = db(config.knockoutPlayerColl)

  lazy val mongo = KnockoutMongo(knockoutColl, knockoutMatchColl, knockoutPlayerColl)

// MongoDB access
final class KnockoutMongo(
    val knockout: lila.db.AsyncColl,
    val knockoutMatch: lila.db.AsyncColl,
    val knockoutPlayer: lila.db.AsyncColl
)
