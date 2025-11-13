package lila.official

export lila.core.lilaism.Lilaism.{ *, given }
export lila.common.extensions.*
import lila.core.id.{ SwissId, TourId }

private val logger = lila.log("official")

opaque type OfficialTournamentId = String
object OfficialTournamentId extends OpaqueString[OfficialTournamentId]:
  def makeId = OfficialTournamentId(ThreadLocalRandom.nextString(8))
