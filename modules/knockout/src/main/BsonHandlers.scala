package lila.knockout

import reactivemongo.api.bson.*

import lila.core.tournament.Status
import lila.db.dsl.{ *, given }

private object BsonHandlers:

  // Import standard handlers for chess types
  given BSONHandler[chess.variant.Variant] = variantByKeyHandler
  given BSONHandler[chess.Clock.Config] = clockConfigHandler
  
  // Tournament Status handler (same pattern as tournament module)
  given BSONHandler[Status] = valueMapHandler(Status.byId)(_.id)

  given BSONHandler[SeedingMethod] = tryHandler[SeedingMethod](
    {
      case BSONString("random") => scala.util.Success(SeedingMethod.Random)
      case BSONString("rating") => scala.util.Success(SeedingMethod.Rating)
      case BSONString("manual") => scala.util.Success(SeedingMethod.Manual)
      case other => scala.util.Failure(new Exception(s"Invalid seeding method: $other"))
    },
    {
      case SeedingMethod.Random => BSONString("random")
      case SeedingMethod.Rating => BSONString("rating")
      case SeedingMethod.Manual => BSONString("manual")
    }
  )

  given BSONHandler[MatchStatus] = tryHandler[MatchStatus](
    {
      case BSONString("pending") => scala.util.Success(MatchStatus.Pending)
      case BSONString("inprogress") => scala.util.Success(MatchStatus.InProgress)
      case BSONString("completed") => scala.util.Success(MatchStatus.Completed)
      case BSONString("bye") => scala.util.Success(MatchStatus.Bye)
      case other => scala.util.Failure(new Exception(s"Invalid match status: $other"))
    },
    {
      case MatchStatus.Pending => BSONString("pending")
      case MatchStatus.InProgress => BSONString("inprogress")
      case MatchStatus.Completed => BSONString("completed")
      case MatchStatus.Bye => BSONString("bye")
    }
  )

  given BSONDocumentHandler[KnockoutMatch] = Macros.handler[KnockoutMatch]
  given BSONDocumentHandler[KnockoutPlayer] = Macros.handler[KnockoutPlayer]
  given BSONDocumentHandler[Knockout] = Macros.handler[Knockout]
