package lila.officialTournament

import chess.{ IntRating, Rated, Speed }
import chess.Clock.Config as ClockConfig

import lila.core.tournament.Status as TournamentStatus
import lila.gathering.Condition
import lila.gathering.Condition.{ WithVerdicts, GetMyTeamIds }
import lila.tournament.TournamentCondition

final class OfficialTournamentApi(
    repo: OfficialTournamentRepo,
    playerRepo: OfficialTournamentPlayerRepo,
    verify: TournamentCondition.Verify,
    swissApi: lila.core.swiss.SwissApi,
    tournamentApi: lila.core.tournament.TournamentApi,
    userApi: lila.core.user.UserApi,
    gameRepo: lila.core.game.GameRepo
)(using Executor, Scheduler):

  import BsonHandlers.{ *, given }

  // ========================================
  // Tournament Lifecycle
  // ========================================

  def create(setup: TournamentSetup)(using me: Me): Fu[OfficialTournament] =
    for
      // Validate rate limiting
      recentCount <- repo.recentCreationCount(me.userId, nowInstant.minusHours(1))
      _ <- if recentCount >= 5 then fufail("Too many tournaments created recently") else funit
      
      // Create the tournament
      tournament = OfficialTournament(
        id = OfficialTournament.makeId,
        name = setup.name.getOrElse(s"${me.username}'s ${setup.format.name} Tournament"),
        format = setup.format,
        status = TournamentStatus.created,
        clock = setup.clock,
        variant = setup.variant,
        rated = setup.rated,
        startsAt = setup.startsAt,
        createdAt = nowInstant,
        createdBy = me.userId,
        nbPlayers = 0,
        settings = OfficialTournament.Settings(
          description = setup.description,
          position = setup.position,
          password = setup.password,
          conditions = setup.conditions,
          hasChat = setup.hasChat
        ),
        formatData = createFormatData(setup)
      )
      
      _ <- repo.insert(tournament)
    yield tournament

  private def createFormatData(setup: TournamentSetup): FormatData =
    setup.format match
      case TournamentFormat.Swiss =>
        SwissData(
          round = 0,
          nbRounds = setup.swissRounds.getOrElse(7),
          nextRoundAt = Some(setup.startsAt),
          roundInterval = setup.roundInterval.getOrElse(60.seconds),
          swissId = None
        )
      
      case TournamentFormat.Arena =>
        ArenaData(
          minutes = setup.arenaMinutes.getOrElse(45),
          tournamentId = None
        )
      
      case TournamentFormat.Knockout =>
        KnockoutData(
          round = KnockoutRound(0),
          maxRounds = 0, // Will be calculated when bracket is generated
          bracket = Bracket(Nil, KnockoutRound(0)),
          nextRoundAt = Some(setup.startsAt)
        )

  def start(id: OfficialTournamentId): Fu[Option[OfficialTournament]] =
    repo.byId(id).flatMap:
      case Some(tournament) if tournament.isCreated =>
        tournament.format match
          case TournamentFormat.Swiss => startSwiss(tournament)
          case TournamentFormat.Arena => startArena(tournament)
          case TournamentFormat.Knockout => startKnockout(tournament)
      case other => fuccess(other)

  private def startSwiss(tournament: OfficialTournament): Fu[Option[OfficialTournament]] =
    // Delegate to existing Swiss implementation
    // This will be implemented with the Swiss adapter
    fuccess(tournament.start.some)

  private def startArena(tournament: OfficialTournament): Fu[Option[OfficialTournament]] =
    // Delegate to existing Arena implementation
    // This will be implemented with the Arena adapter
    fuccess(tournament.start.some)

  private def startKnockout(tournament: OfficialTournament): Fu[Option[OfficialTournament]] =
    for
      players <- playerRepo.rankedPlayers(tournament.id)
      bracket = generateBracket(players)
      knockoutData = tournament.formatData.asKnockout.getOrElse(
        KnockoutData(KnockoutRound(1), calculateMaxRounds(players.size), bracket, None)
      )
      newData = knockoutData.copy(
        round = KnockoutRound(1),
        maxRounds = calculateMaxRounds(players.size),
        bracket = bracket
      )
      updated = tournament.start.withFormatData(newData)
      _ <- repo.update(updated)
    yield updated.some

  def finish(id: OfficialTournamentId): Fu[Option[OfficialTournament]] =
    repo.byId(id).flatMap:
      case Some(tournament) if tournament.isStarted =>
        determineWinner(tournament).flatMap: winner =>
          val finished = tournament.finish(winner)
          repo.update(finished).inject(finished.some)
      case other => fuccess(other)

  private def determineWinner(tournament: OfficialTournament): Fu[Option[UserId]] =
    tournament.format match
      case TournamentFormat.Swiss =>
        // Get winner from Swiss standings
        fuccess(None) // TODO: implement with adapter
      
      case TournamentFormat.Arena =>
        // Get winner from Arena leaderboard
        fuccess(None) // TODO: implement with adapter
      
      case TournamentFormat.Knockout =>
        tournament.formatData.asKnockout match
          case Some(data) if data.bracket.remainingPlayers == 1 =>
            // Find the winner of the final match
            fuccess(data.bracket.matchesForRound(data.round).headOption.flatMap(_.winnerId))
          case _ => fuccess(None)

  def cancel(id: OfficialTournamentId)(using Me): Fu[Boolean] =
    repo.byId(id).flatMap:
      case Some(tournament) if tournament.isCreated =>
        repo.remove(id).inject(true)
      case _ => fuccess(false)

  // ========================================
  // Player Management
  // ========================================

  def join(id: OfficialTournamentId, password: Option[String])(using
      getMyTeamIds: GetMyTeamIds,
      me: Me
  ): Fu[JoinResult] =
    repo.byId(id).flatMap:
      case None => fuccess(JoinResult.NotFound)
      case Some(tournament) if !tournament.isCreated => fuccess(JoinResult.AlreadyStarted)
      case Some(tournament) if tournament.nbPlayers >= OfficialTournament.maxPlayers =>
        fuccess(JoinResult.Full)
      case Some(tournament) =>
        // Check password
        if tournament.isPrivate && tournament.settings.password != password then
          fuccess(JoinResult.WrongPassword)
        else
          // Check conditions using usingPerfOf to provide Perf context
          userApi.usingPerfOf(me, tournament.perfType.key): perf ?=>
            verify(tournament.settings.conditions, tournament.perfType).flatMap:
              case verdicts if !verdicts.accepted =>
                fuccess(JoinResult.ConditionsNotMet(verdicts.list.map(_.verdict)))
              case _ =>
                // All conditions met, add player
                for
                  rating <- getUserRating(me.userId, tournament.perfType)
                  player = OfficialTournamentPlayer(
                    id = OfficialTournamentPlayer.makeId(id, me.userId),
                    tourId = id,
                    userId = me.userId,
                    rating = rating,
                    score = 0,
                    performance = none,
                    seed = 0, // Will be calculated before start
                    joinedAt = nowInstant
                  )
                  _ <- playerRepo.insert(player)
                  _ <- repo.setNbPlayers(id, tournament.nbPlayers + 1)
                yield JoinResult.Ok

  def withdraw(id: OfficialTournamentId)(using me: Me): Funit =
    repo.byId(id).flatMap:
      case Some(tournament) if tournament.isCreated =>
        playerRepo.remove(id, me.userId) >>
          repo.setNbPlayers(id, (tournament.nbPlayers - 1).atLeast(0))
      case _ => funit

  private def getUserRating(userId: UserId, perfType: lila.rating.PerfType): Fu[IntRating] =
    userApi.withIntRatingIn(userId, perfType.key).map:
      _.map(_._2).getOrElse(IntRating(1500))

  // ========================================
  // Query Methods
  // ========================================

  def byId(id: OfficialTournamentId): Fu[Option[OfficialTournament]] =
    repo.byId(id)

  def findUpcoming(limit: Int = 20): Fu[List[OfficialTournament]] =
    repo.findUpcoming(limit)

  def findActive(limit: Int = 20): Fu[List[OfficialTournament]] =
    repo.findActive(limit)

  def findRecent(limit: Int = 20): Fu[List[OfficialTournament]] =
    repo.findRecent(limit)

  def byCreator(userId: UserId, limit: Int = 20): Fu[List[OfficialTournament]] =
    repo.byCreator(userId, limit)

  def byFormat(format: TournamentFormat, limit: Int = 50): Fu[List[OfficialTournament]] =
    repo.byFormat(format, limit)

  def search(term: String, limit: Int = 10): Fu[List[OfficialTournament]] =
    repo.search(term, limit)

  // ========================================
  // Knockout-Specific Logic
  // ========================================

  private def generateBracket(players: List[OfficialTournamentPlayer]): Bracket =
    val sortedPlayers = players.sortBy(p => -p.rating.value).zipWithIndex.map:
      case (player, index) =>
        BracketPlayer(player.userId, player.rating, index + 1, isBye = false)
    
    val nextPowerOfTwo = math.pow(2, math.ceil(math.log(sortedPlayers.size) / math.log(2))).toInt
    val numByes = nextPowerOfTwo - sortedPlayers.size
    
    // Add byes for top seeds
    val byes = (1 to numByes).map: i =>
      BracketPlayer(UserId(""), IntRating(0), i, isBye = true)
    
    val allSlots = (sortedPlayers ++ byes).toList
    
    // Create initial matches using standard tournament seeding
    val matches = pairPlayers(allSlots, KnockoutRound(1))
    
    Bracket(matches.map(_.autoComplete), KnockoutRound(1))

  private def pairPlayers(players: List[BracketPlayer], round: KnockoutRound): List[Match] =
    // Standard single-elimination seeding
    // 1v16, 8v9, 4v13, 5v12, 2v15, 7v10, 3v14, 6v11, etc.
    val n = players.size
    val seeded = (0 until n / 2).map: i =>
      val p1 = players(i)
      val p2 = players(n - 1 - i)
      Match(
        id = MatchId.make,
        round = round,
        player1 = Some(p1),
        player2 = Some(p2),
        winnerId = None,
        gameId = None,
        status = MatchStatus.Pending
      )
    seeded.toList

  private def calculateMaxRounds(numPlayers: Int): Int =
    math.ceil(math.log(numPlayers) / math.log(2)).toInt

  def advanceKnockoutRound(id: OfficialTournamentId): Fu[Option[Int]] =
    repo.byId(id).flatMap:
      case Some(tournament) =>
        tournament.formatData.asKnockout match
          case Some(data) =>
            val currentRound = data.bracket.matchesForRound(data.round)
            if currentRound.forall(_.isCompleted) then
              // All matches in current round are complete
              val winners = currentRound.flatMap(_.getWinner)
              if winners.size <= 1 then
                // Tournament is complete
                finish(id).inject(none)
              else
                // Create next round
                val nextRound = data.round.next
                val nextMatches = pairPlayers(winners, nextRound)
                val newBracket = data.bracket.copy(
                  matches = data.bracket.matches ++ nextMatches.map(_.autoComplete),
                  currentRound = nextRound
                )
                val newData = data.copy(
                  round = nextRound,
                  bracket = newBracket,
                  nextRoundAt = Some(nowInstant.plusMinutes(5)) // 5 min break between rounds
                )
                repo.updateFormatData(id, newData).inject(Some(nextRound.value))
            else
              fuccess(none)
          case None => fuccess(none)
      case None => fuccess(none)

  def updateMatchResult(
      tourId: OfficialTournamentId,
      matchId: MatchId,
      winnerId: UserId
  ): Funit =
    repo.updateMatch(tourId, matchId, _.withWinner(winnerId)).void >>
      advanceKnockoutRound(tourId).void

  def setMatchGame(tourId: OfficialTournamentId, matchId: MatchId, gameId: GameId): Funit =
    repo.updateMatch(tourId, matchId, _.withGame(gameId)).void

// Join result types
enum JoinResult:
  case Ok
  case NotFound
  case AlreadyStarted
  case Full
  case WrongPassword
  case ConditionsNotMet(verdicts: List[Condition.Verdict])
