package lila.official

import reactivemongo.api.bson.*

import lila.db.dsl.{ *, given }

private object BsonHandlers:

  given BSONHandler[OfficialTournamentType] = tryHandler[OfficialTournamentType](
    { case BSONString(key) => OfficialTournamentType.fromKey(key).toTry(s"Invalid tournament type: $key") },
    tpe => BSONString(tpe.key)
  )

  given BSONDocumentHandler[OfficialTournament] = Macros.handler[OfficialTournament]
