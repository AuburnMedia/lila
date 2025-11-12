package lila.officialTournament

import chess.Clock.Config as ClockConfig
import chess.{ Rated, Rated as ChessRated }
import scala.concurrent.duration.FiniteDuration

export lila.core.lilaism.Lilaism.{ *, given }
export lila.common.extensions.*
export lila.core.id.OfficialTournamentId

private val logger = lila.log("officialTournament")

opaque type MatchId = String
object MatchId extends OpaqueString[MatchId]:
  def make: MatchId = MatchId(scalalib.ThreadLocalRandom.nextString(8))

opaque type KnockoutRound = Int
object KnockoutRound extends OpaqueInt[KnockoutRound]:
  extension (r: KnockoutRound)
    def next: KnockoutRound = KnockoutRound(r.value + 1)
    def prev: KnockoutRound = KnockoutRound(r.value - 1)

// Tournament setup data for creation
case class TournamentSetup(
    name: Option[String],
    format: TournamentFormat,
    clock: ClockConfig,
    variant: chess.variant.Variant,
    rated: Rated,
    startsAt: Instant,
    description: Option[String],
    position: Option[chess.format.Fen.Full],
    password: Option[String],
    conditions: lila.tournament.TournamentCondition.All,
    hasChat: Boolean,
    // Format-specific fields
    swissRounds: Option[Int],
    roundInterval: Option[FiniteDuration],
    arenaMinutes: Option[Int]
)