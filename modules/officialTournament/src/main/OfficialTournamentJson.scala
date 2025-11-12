package lila.officialTournament

import play.api.libs.json.*

import lila.common.Json.given
import lila.core.id.OfficialTournamentId
import lila.core.tournament.Status as TournamentStatus
import lila.core.i18n.{ Translator, Translate }

final class OfficialTournamentJson(
    lightUserApi: lila.core.user.LightUserApi
)(using Executor):

  import OfficialTournamentJson.{ *, given }

  def tournamentJson(tournament: OfficialTournament)(using Translate): Fu[JsObject] =
    lightUserApi.asyncManyFallback(
      List(tournament.createdBy) ++ tournament.winnerId.toList
    ).map: users =>
      val userMap = users.map(u => u.id -> u).toMap
      val baseJson = Json.obj(
        "id" -> tournament.id,
        "name" -> tournament.name,
        "format" -> tournament.format.toString.toLowerCase,
        "status" -> tournament.status.toString.toLowerCase,
        "clock" -> Json.obj(
          "limit" -> tournament.clock.limitSeconds.value,
          "increment" -> tournament.clock.incrementSeconds.value
        ),
        "variant" -> tournament.variant.key,
        "rated" -> tournament.rated.yes,
        "startsAt" -> tournament.startsAt.toMillis,
        "createdAt" -> tournament.createdAt.toMillis,
        "createdBy" -> userMap.get(tournament.createdBy).map(lightUserJson),
        "nbPlayers" -> tournament.nbPlayers,
        "perf" -> Json.obj(
          "key" -> tournament.perfType.key,
          "name" -> tournament.perfType.trans
        )
      )

      val formatJson = tournament.format match
        case TournamentFormat.Swiss =>
          tournament.formatData.asSwiss.fold(Json.obj()): data =>
            Json.obj(
              "round" -> data.round,
              "nbRounds" -> data.nbRounds,
              "nextRoundAt" -> data.nextRoundAt.map(_.toMillis)
            )
        
        case TournamentFormat.Arena =>
          tournament.formatData.asArena.fold(Json.obj()): data =>
            Json.obj(
              "minutes" -> data.minutes,
              "finishesAt" -> tournament.finishesAt.toMillis
            )
        
        case TournamentFormat.Knockout =>
          tournament.formatData.asKnockout.fold(Json.obj()): data =>
            Json.obj(
              "round" -> data.round,
              "maxRounds" -> data.maxRounds,
              "remainingPlayers" -> data.bracket.remainingPlayers,
              "nextRoundAt" -> data.nextRoundAt.map(_.toMillis)
            )

      val optionalFields = List(
        tournament.settings.description.map("description" -> JsString(_)),
        tournament.winnerId.flatMap(userMap.get).map("winner" -> lightUserJson(_)),
        tournament.finishedAt.map(ts => "finishedAt" -> JsNumber(ts.toMillis))
      ).flatten

      baseJson ++ Json.obj("formatData" -> formatJson) ++ JsObject(optionalFields)

  def bracketJson(knockout: KnockoutData): JsObject =
    Json.obj(
      "currentRound" -> knockout.round,
      "maxRounds" -> knockout.maxRounds,
      "matches" -> knockout.bracket.matches.map(matchJson)
    )

  def matchJson(m: Match): JsObject =
    Json.obj(
      "id" -> m.id,
      "round" -> m.round,
      "status" -> m.status.name,
      "player1" -> m.player1.map(playerJson),
      "player2" -> m.player2.map(playerJson),
      "winnerId" -> m.winnerId,
      "gameId" -> m.gameId
    )

  def playerJson(p: BracketPlayer): JsObject =
    Json.obj(
      "id" -> p.userId,
      "rating" -> p.rating.value,
      "seed" -> p.seed,
      "isBye" -> p.isBye
    )

  def lightUserJson(u: lila.core.LightUser): JsObject =
    Json.obj(
      "id" -> u.id,
      "name" -> u.name
    ) ++ u.title.fold(Json.obj())(t => Json.obj("title" -> t))

  def tournamentListJson(tournaments: List[OfficialTournament])(using Translate): Fu[JsArray] =
    tournaments.map(tournamentJson).parallel.map(JsArray(_))

  def standingsJson(players: List[OfficialTournamentPlayer]): Fu[JsArray] =
    lightUserApi.asyncManyFallback(players.map(_.userId)).map: users =>
      val userMap = users.map(u => u.id -> u).toMap
      JsArray:
        players.zipWithIndex.map: (player, rank) =>
          Json.obj(
            "rank" -> (rank + 1),
            "user" -> userMap.get(player.userId).map(lightUserJson),
            "rating" -> player.rating.value,
            "score" -> player.score,
            "performance" -> player.performance,
            "seed" -> player.seed
          )

object OfficialTournamentJson:
  
  given Writes[OfficialTournamentId] = Writes(id => JsString(id.value))
  given Writes[MatchId] = Writes(id => JsString(id.value))
  given Writes[KnockoutRound] = Writes(r => JsNumber(r.value))
  
  given Writes[TournamentFormat] = Writes: format =>
    JsString(format match
      case TournamentFormat.Swiss => "swiss"
      case TournamentFormat.Arena => "arena"
      case TournamentFormat.Knockout => "knockout"
    )
  
  given Writes[MatchStatus] = Writes(s => JsString(s.name))
