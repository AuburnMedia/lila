package lila.officialTournament

import reactivemongo.api.bson.*
import scala.concurrent.duration.FiniteDuration
import scala.util.{ Try, Success, Failure }

import lila.core.id.{ SwissId, TourId }
import lila.core.tournament.Status as TournamentStatus
import lila.db.dsl.{ *, given }
import lila.gathering.Condition
import lila.tournament.TournamentCondition

object BsonHandlers:

  given BSONHandler[MatchId] = stringAnyValHandler(_.value, MatchId.apply)
  given BSONHandler[KnockoutRound] = intAnyValHandler(_.value, KnockoutRound.apply)

  // Clock.Config handler from lila.db.Handlers
  given BSONHandler[chess.Clock.Config] = clockConfigHandler

  // Variant handler from lila.db.Handlers
  given BSONHandler[chess.variant.Variant] = variantByKeyHandler

  // Tournament Status handler - reuse from lila.core
  given BSONHandler[TournamentStatus] = valueMapHandler(TournamentStatus.byId)(_.id)

  given BSONHandler[TournamentFormat] = quickHandler[TournamentFormat](
    {
      case BSONString("swiss") => TournamentFormat.Swiss
      case BSONString("arena") => TournamentFormat.Arena
      case BSONString("knockout") => TournamentFormat.Knockout
      case _ => TournamentFormat.Swiss
    },
    format =>
      BSONString(format match
        case TournamentFormat.Swiss => "swiss"
        case TournamentFormat.Arena => "arena"
        case TournamentFormat.Knockout => "knockout"
      )
  )

  given BSONHandler[MatchStatus] = quickHandler[MatchStatus](
    {
      case BSONString("pending") => MatchStatus.Pending
      case BSONString("playing") | BSONString("inprogress") => MatchStatus.InProgress
      case BSONString("completed") | BSONString("finished") => MatchStatus.Completed
      case _ => MatchStatus.Pending
    },
    status =>
      BSONString(status match
        case MatchStatus.Pending => "pending"
        case MatchStatus.InProgress => "playing"
        case MatchStatus.Completed => "completed"
      )
  )

  given BSONDocumentHandler[BracketPlayer] = Macros.handler[BracketPlayer]
  given BSONDocumentHandler[Match] = Macros.handler[Match]
  given BSONDocumentHandler[Bracket] = Macros.handler[Bracket]

  // BSON handler for FiniteDuration
  given BSONHandler[FiniteDuration] = quickHandler[FiniteDuration](
    {
      case BSONInteger(seconds) => scala.concurrent.duration.Duration(seconds, "seconds")
      case BSONLong(seconds) => scala.concurrent.duration.Duration(seconds, "seconds")
      case _ => scala.concurrent.duration.Duration(300, "seconds") // default 5 minutes
    },
    duration => BSONLong(duration.toSeconds)
  )

  given BSONHandler[FormatData] = new:
    def readTry(bson: BSONValue): Try[FormatData] =
      bson match
        case doc: BSONDocument =>
          (doc.getAsOpt[String]("type"), doc) match
            case (Some("swiss"), _) =>
              for
                round <- doc.getAsTry[Int]("round")
                nbRounds <- doc.getAsTry[Int]("nbRounds")
                nextRoundAt = doc.getAsOpt[Instant]("nextRoundAt")
                roundInterval <- doc.getAsTry[FiniteDuration]("roundInterval")
                swissId = doc.getAsOpt[SwissId]("swissId")
              yield SwissData(round, nbRounds, nextRoundAt, roundInterval, swissId)

            case (Some("arena"), _) =>
              for
                minutes <- doc.getAsTry[Int]("minutes")
                tournamentId = doc.getAsOpt[TourId]("tournamentId")
              yield ArenaData(minutes, tournamentId)

            case (Some("knockout"), _) =>
              for
                round <- doc.getAsTry[KnockoutRound]("round")
                maxRounds <- doc.getAsTry[Int]("maxRounds")
                bracket <- doc.getAsTry[Bracket]("bracket")
                nextRoundAt = doc.getAsOpt[Instant]("nextRoundAt")
              yield KnockoutData(round, maxRounds, bracket, nextRoundAt)

            case _ =>
              Failure(new Exception(s"Unknown format data type"))
        case _ => Failure(new Exception("FormatData must be a document"))

    def writeTry(data: FormatData): Try[BSONValue] =
      data match
        case SwissData(round, nbRounds, nextRoundAt, roundInterval, swissId) =>
          Success(
            BSONDocument(
              "type" -> "swiss",
              "round" -> round,
              "nbRounds" -> nbRounds,
              "nextRoundAt" -> nextRoundAt,
              "roundInterval" -> roundInterval,
              "swissId" -> swissId
            )
          )

        case ArenaData(minutes, tournamentId) =>
          Success(
            BSONDocument(
              "type" -> "arena",
              "minutes" -> minutes,
              "tournamentId" -> tournamentId
            )
          )

        case KnockoutData(round, maxRounds, bracket, nextRoundAt) =>
          Success(
            BSONDocument(
              "type" -> "knockout",
              "round" -> round,
              "maxRounds" -> maxRounds,
              "bracket" -> bracket,
              "nextRoundAt" -> nextRoundAt
            )
          )

  given BSONDocumentHandler[OfficialTournament.Settings] = Macros.handler[OfficialTournament.Settings]
  given BSONDocumentHandler[OfficialTournament] = Macros.handler[OfficialTournament]
