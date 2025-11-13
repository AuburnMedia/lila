package lila.official

export lila.core.lilaism.Lilaism.{ *, given }
export lila.common.extensions.*

private val logger = lila.log("official")

opaque type OfficialTournamentId = String
object OfficialTournamentId extends OpaqueString[OfficialTournamentId]:
  def makeId = OfficialTournamentId(ThreadLocalRandom.nextString(8))
