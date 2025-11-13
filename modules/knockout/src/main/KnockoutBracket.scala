package lila.knockout

import scala.util.Random

// Knockout bracket generation and management
object KnockoutBracket:

  // Generate initial bracket structure
  def generateBracket(
      players: List[KnockoutPlayer],
      seedingMethod: SeedingMethod
  ): List[KnockoutMatch] =
    if players.isEmpty then Nil
    else
      val seededPlayers = seedPlayers(players, seedingMethod)
      val bracketSize = Knockout.calculateBracketSize(players.length)
      val totalRounds = Knockout.calculateRounds(players.length)

      generateRounds(seededPlayers, bracketSize, totalRounds)

  // Seed players according to seeding method
  private def seedPlayers(
      players: List[KnockoutPlayer],
      method: SeedingMethod
  ): List[KnockoutPlayer] =
    method match
      case SeedingMethod.Random =>
        Random.shuffle(players).zipWithIndex.map { case (p, i) =>
          p.copy(seed = Some(i + 1))
        }
      case SeedingMethod.Rating =>
        players
          .sortBy(-_.rating)
          .zipWithIndex
          .map { case (p, i) => p.copy(seed = Some(i + 1)) }
      case SeedingMethod.Manual =>
        // For manual seeding, assume seeds are already assigned
        players.sortBy(_.seed.getOrElse(Int.MaxValue))

  // Generate all rounds of matches
  private def generateRounds(
      players: List[KnockoutPlayer],
      bracketSize: Int,
      totalRounds: Int
  ): List[KnockoutMatch] =
    // Round 1: pair players, with byes for higher seeds
    val byesNeeded = bracketSize - players.length
    val firstRoundMatches = generateFirstRound(players, byesNeeded)

    // Generate placeholder matches for subsequent rounds
    val subsequentRounds = (2 to totalRounds).flatMap { roundNum =>
      val matchesInRound = bracketSize / math.pow(2, roundNum).toInt
      (0 until matchesInRound).map { position =>
        KnockoutMatch(
          id = MatchId.makeId,
          round = KnockoutRoundNumber(roundNum),
          position = position,
          player1 = None,
          player2 = None,
          status = MatchStatus.Pending
        )
      }
    }

    firstRoundMatches ++ subsequentRounds

  // Generate first round with bye handling
  private def generateFirstRound(
      players: List[KnockoutPlayer],
      byesNeeded: Int
  ): List[KnockoutMatch] =
    val playersWithByes = players.take(byesNeeded)
    val playersInMatches = players.drop(byesNeeded)

    // Create bye matches for top seeds
    val byeMatches = playersWithByes.zipWithIndex.map { case (player, i) =>
      KnockoutMatch(
        id = MatchId.makeId,
        round = KnockoutRoundNumber(1),
        position = i,
        player1 = Some(player.userId),
        player2 = None,
        winner = Some(player.userId), // Auto-win
        status = MatchStatus.Bye,
        isBye = true
      )
    }

    // Create regular matches
    val regularMatches = playersInMatches
      .grouped(2)
      .zipWithIndex
      .map { case (pair, i) =>
        val p1 = pair.headOption.map(_.userId)
        val p2 = pair.lift(1).map(_.userId)
        KnockoutMatch(
          id = MatchId.makeId,
          round = KnockoutRoundNumber(1),
          position = byeMatches.length + i,
          player1 = p1,
          player2 = p2,
          status = if p1.isDefined && p2.isDefined then MatchStatus.Pending else MatchStatus.Pending
        )
      }
      .toList

    byeMatches ++ regularMatches

  // Update bracket with match result
  def updateMatchResult(
      matches: List[KnockoutMatch],
      matchId: MatchId,
      winner: UserId
  ): List[KnockoutMatch] =
    matches.map { m =>
      if m.id == matchId then m.copy(winner = Some(winner), status = MatchStatus.Completed)
      else m
    }

  // Advance winners to next round
  def advanceWinners(matches: List[KnockoutMatch], currentRound: KnockoutRoundNumber): List[KnockoutMatch] =
    val completedMatches = matches.filter(m => m.round == currentRound && m.isComplete)
    val nextRoundNumber = currentRound.next

    // Pair up winners for next round
    val nextRoundMatches = matches.filter(_.round == nextRoundNumber)

    completedMatches
      .sortBy(_.position)
      .map(_.winner.get)
      .grouped(2)
      .zipWithIndex
      .foldLeft(matches) { case (acc, (winners, i)) =>
        val matchToUpdate = nextRoundMatches.lift(i)
        matchToUpdate match
          case Some(m) =>
            val updated = m.copy(
              player1 = winners.headOption,
              player2 = winners.lift(1),
              status = if winners.length == 2 then MatchStatus.Pending else MatchStatus.Bye
            )
            acc.map(existingMatch => if existingMatch.id == m.id then updated else existingMatch)
          case None => acc
      }

  // Get bracket structure for visualization
  case class BracketRound(
      roundNumber: Int,
      roundName: String,
      matches: List[KnockoutMatch]
  )

  def getBracketStructure(
      matches: List[KnockoutMatch],
      totalRounds: Int
  ): List[BracketRound] =
    (1 to totalRounds).map { roundNum =>
      val roundMatches = matches.filter(_.round.value == roundNum)
      val roundName = getRoundName(roundNum, totalRounds)
      BracketRound(roundNum, roundName, roundMatches)
    }.toList

  private def getRoundName(roundNum: Int, totalRounds: Int): String =
    val remaining = totalRounds - roundNum + 1
    remaining match
      case 1 => "Final"
      case 2 => "Semi-Finals"
      case 3 => "Quarter-Finals"
      case n => s"Round $roundNum"
