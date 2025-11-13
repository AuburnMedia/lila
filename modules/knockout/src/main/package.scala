package lila.knockout

export lila.core.lilaism.Lilaism.{ *, given }
export lila.common.extensions.*

private val logger = lila.log("knockout")

opaque type KnockoutId = String
object KnockoutId extends OpaqueString[KnockoutId]:
  def makeId = KnockoutId(ThreadLocalRandom.nextString(8))

opaque type MatchId = String
object MatchId extends OpaqueString[MatchId]:
  def makeId = MatchId(ThreadLocalRandom.nextString(8))

opaque type KnockoutRoundNumber = Int
object KnockoutRoundNumber extends OpaqueInt[KnockoutRoundNumber]:
  extension (r: KnockoutRoundNumber)
    def next = KnockoutRoundNumber(r + 1)
    def prev = KnockoutRoundNumber((r - 1).atLeast(1))
