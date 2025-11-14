package lila.official

import reactivemongo.api.bson.*

import lila.core.tournament.Status
import lila.db.dsl.{ *, given }

private object BsonHandlers:

  // Tournament Status handler (same pattern as tournament module)
  given BSONHandler[Status] = valueMapHandler(Status.byId)(_.id)

  given BSONHandler[OfficialTournamentType] = tryHandler[OfficialTournamentType](
    { case BSONString(key) => OfficialTournamentType.fromKey(key).toTry(s"Invalid tournament type: $key") },
    tpe => BSONString(tpe.key)
  )

  given BSONDocumentHandler[OfficialTournament] = Macros.handler[OfficialTournament]
