# Official Tournaments Feature - Implementation Plan

## Executive Summary

This document provides a comprehensive implementation plan for the Official Tournaments feature, which adds a unified interface for creating and managing three tournament formats: Swiss, Arena, and Knockout (Single Elimination).

## Current State Analysis

### Existing Infrastructure
- **Swiss Tournaments**: Fully implemented in `/modules/swiss/`
  - Pairing system using Swiss-system algorithm
  - Complete UI with standings, player lists, round management
  - Form at `/swiss/new/:teamId`
  - Real-time updates via WebSocket

- **Arena Tournaments**: Fully implemented in `/modules/tournament/`
  - Arena pairing system with dynamic matchmaking
  - Team battle support
  - Leaderboards and scoring
  - Form at `/tournament/new`
  - Real-time updates via WebSocket

- **Knockout Tournaments**: **NOT IMPLEMENTED** - requires full implementation
  - Single elimination bracket logic
  - Bye handling for odd player counts
  - Round progression system
  - Bracket visualization component

### Key Architecture Patterns

1. **Module Structure**: `/modules/[type]/src/main/`
   - Model files (case classes for data)
   - API files for business logic
   - Form files for validation
   - UI files for view rendering
   - BsonHandlers for database serialization

2. **Controller Pattern**: `/app/controllers/[Type].scala`
   - HTTP request handling
   - Form binding and validation
   - Redirection to views

3. **Frontend Structure**: `/ui/[type]/`
   - TypeScript controllers (`ctrl.ts`)
   - View components (`view/`)
   - Interfaces (`interfaces.ts`)
   - WebSocket handlers (`socket.ts`)

4. **Routing**: Centralized in `/conf/routes`

## Implementation Strategy

### Phase 1: Foundation (Week 1)

#### 1.1 Backend Module Creation
**Location**: `/modules/official/`

**Files to Create**:
```
modules/official/src/main/
├── OfficialTournament.scala          # Base model
├── OfficialTournamentType.scala      # Enum: Swiss, Arena, Knockout
├── OfficialForm.scala                # Form validation
├── OfficialApi.scala                 # Business logic
├── BsonHandlers.scala                # Database serialization
├── Env.scala                         # Dependency injection
├── package.scala                     # Package object
└── ui/
    └── OfficialFormUi.scala          # Form rendering
```

**Key Models**:
```scala
// OfficialTournamentType.scala
enum OfficialTournamentType:
  case Swiss, Arena, Knockout

// OfficialTournament.scala
case class OfficialTournament(
  id: OfficialTournamentId,
  name: String,
  tournamentType: OfficialTournamentType,
  // Swiss-specific fields (Option)
  swissId: Option[SwissId],
  // Arena-specific fields (Option)
  arenaId: Option[TourId],
  // Knockout-specific fields (Option)
  knockoutId: Option[KnockoutId],
  createdAt: Instant,
  createdBy: UserId,
  startsAt: Instant,
  status: OfficialStatus
)
```

#### 1.2 Knockout Tournament Implementation
**Location**: `/modules/knockout/`

This is the most complex part as it requires implementing an entirely new tournament system.

**Files to Create**:
```
modules/knockout/src/main/
├── Knockout.scala                    # Main model
├── KnockoutApi.scala                 # Business logic
├── KnockoutBracket.scala            # Bracket generation
├── KnockoutPairing.scala            # Match pairing
├── KnockoutRound.scala              # Round management
├── KnockoutPlayer.scala             # Player state
├── BsonHandlers.scala               # Database serialization
└── ui/
    ├── KnockoutShow.scala           # Tournament view
    └── KnockoutBracketUi.scala      # Bracket visualization
```

**Core Algorithm Requirements**:
1. **Bracket Generation**:
   - Calculate rounds: `ceil(log2(playerCount))`
   - Assign byes to higher seeds when playerCount is not power of 2
   - Create initial bracket structure

2. **Pairing Logic**:
   - Single elimination: loser is eliminated
   - Winner advances to next round
   - Handle byes (auto-advance)

3. **Seeding**:
   - Option 1: Random seeding
   - Option 2: Rating-based seeding
   - Option 3: Manual seeding

### Phase 2: Frontend (Week 2)

#### 2.1 UI Package Creation
**Location**: `/ui/official/`

**Files to Create**:
```
ui/official/
├── package.json
├── tsconfig.json
├── src/
│   ├── official.ts                  # Main entry point
│   ├── ctrl.ts                      # Controller
│   ├── interfaces.ts                # TypeScript interfaces
│   ├── socket.ts                    # WebSocket handling
│   └── view/
│       ├── main.ts                  # Main view
│       ├── form.ts                  # Form view
│       └── standings.ts             # Standings view
└── css/
    └── official.scss                # Styling
```

#### 2.2 Knockout Bracket Component
**Location**: `/ui/knockout/`

**Files to Create**:
```
ui/knockout/
├── package.json
├── tsconfig.json
├── src/
│   ├── knockout.ts
│   ├── ctrl.ts
│   ├── interfaces.ts
│   ├── bracket.ts                   # Bracket data structure
│   └── view/
│       ├── bracket.ts               # Bracket visualization
│       └── match.ts                 # Individual match component
└── css/
    └── build/
        └── knockout.bracket.scss    # Bracket styling
```

**Bracket Visualization Requirements**:
- SVG or Canvas-based rendering
- Responsive layout (mobile-friendly)
- Interactive (clickable matches)
- Real-time updates
- Smooth animations for results

### Phase 3: Controllers & Routes (Week 2)

#### 3.1 Controller Creation
**Location**: `/app/controllers/OfficialTournament.scala`

**Routes to Add** (in `/conf/routes`):
```scala
# Official Tournaments
GET   /official                              controllers.OfficialTournament.home
GET   /official/new                          controllers.OfficialTournament.form
POST  /official/new                          controllers.OfficialTournament.create
GET   /official/$id<\w{8}>                   controllers.OfficialTournament.show(id: OfficialTournamentId)
GET   /official/$id<\w{8}>/standings         controllers.OfficialTournament.standings(id: OfficialTournamentId)
POST  /official/$id<\w{8}>/join              controllers.OfficialTournament.join(id: OfficialTournamentId)
POST  /official/$id<\w{8}>/withdraw          controllers.OfficialTournament.withdraw(id: OfficialTournamentId)
```

### Phase 4: Integration (Week 3)

#### 4.1 Build System Integration
**Update**: `/build.sbt`

Add official and knockout modules to the build configuration.

#### 4.2 Database Schema
MongoDB collections:
- `official_tournament` - Official tournament metadata
- `knockout` - Knockout tournament data
- `knockout_pairing` - Knockout matches
- `knockout_player` - Player states in knockout

### Phase 5: Testing (Week 3)

#### 5.1 Backend Tests
```
modules/official/src/test/
modules/knockout/src/test/
```

#### 5.2 Frontend Tests
```
ui/official/tests/
ui/knockout/tests/
```

### Phase 6: Polish & Production (Week 4)

- Error handling
- Edge case handling
- Performance optimization
- Accessibility features
- Mobile responsiveness
- Documentation

## Detailed Component Specifications

### 1. Tournament Creation Form

**URL**: `/official/new`

**Form Fields**:
1. Tournament Type (Radio buttons):
   - [ ] Swiss
   - [ ] Arena  
   - [ ] Knockout

2. Common Fields (all types):
   - Name
   - Description
   - Start time
   - Clock time
   - Clock increment
   - Variant
   - Rated/Casual
   - Entry conditions

3. Format-Specific Fields:
   - **Swiss**: Number of rounds, round interval
   - **Arena**: Duration
   - **Knockout**: Seeding method, bracket size

**UX Requirements**:
- Smart defaults based on tournament type
- Show/hide format-specific fields dynamically
- Validation with helpful error messages
- Help tooltips matching existing patterns
- Submit button disabled until form is valid

### 2. Dashboard Views

#### Swiss Dashboard
- Reuse existing Swiss standings component
- Show current round and pairings
- Display next round time
- Player list with scores

#### Arena Dashboard
- Reuse existing Arena leaderboard
- Show real-time scores
- Display top players
- Show time remaining

#### Knockout Dashboard
**New Component Required**

**Bracket Layout**:
```
Round 1        Round 2       Finals        Winner
Player A ┐
         ├─── Winner A/B ┐
Player B ┘               │
                         ├─── Winner QF1/QF2 ┐
Player C ┐               │                    │
         ├─── Winner C/D ┘                    │
Player D ┘                                    ├─── Champion
                                              │
Player E ┐                                    │
         ├─── Winner E/F ┐                    │
Player F ┘               │                    │
                         ├─── Winner QF3/QF4 ┘
Player G ┐               │
         ├─── Winner G/H ┘
Player H ┘
```

**Bracket Component Features**:
- Horizontal scrolling on mobile
- Clickable matches (navigate to game)
- Live match indicators (pulsing/animated)
- Completed matches show score
- User's match highlighted
- Legend: Completed, Live, Upcoming, Bye

### 3. Real-time Updates

**WebSocket Events**:
```scala
sealed trait OfficialEvent
case class PlayerJoined(userId: UserId) extends OfficialEvent
case class PlayerWithdrew(userId: UserId) extends OfficialEvent
case class TournamentStarted() extends OfficialEvent
case class RoundStarted(round: Int) extends OfficialEvent
case class MatchCompleted(matchId: MatchId, winner: UserId) extends OfficialEvent
case class TournamentFinished(winner: UserId) extends OfficialEvent
```

## Technical Challenges & Solutions

### Challenge 1: Knockout Bracket Rendering
**Problem**: Complex SVG/Canvas rendering with dynamic sizing

**Solution Options**:
1. Use D3.js for bracket visualization
2. Custom SVG generation with Snabbdom
3. HTML/CSS grid-based layout (simpler, more accessible)

**Recommendation**: Start with HTML/CSS grid, add SVG enhancements later

### Challenge 2: Bye Handling
**Problem**: Odd number of players requires byes

**Solution**:
```scala
def generateBracket(players: List[Player]): Bracket =
  val roundsNeeded = ceil(log2(players.length)).toInt
  val bracketSize = pow(2, roundsNeeded).toInt
  val byesNeeded = bracketSize - players.length
  
  // Assign byes to top seeds
  val seededPlayers = players.sortBy(-_.rating)
  val playersWithByes = seededPlayers.take(byesNeeded)
  
  // Create initial pairings
  createInitialPairings(seededPlayers, playersWithByes)
```

### Challenge 3: State Management
**Problem**: Complex state for bracket progression

**Solution**: Use state machine pattern
```scala
enum KnockoutStatus:
  case Created, Started, RoundInProgress, RoundComplete, Finished

case class KnockoutState(
  currentRound: Int,
  matches: Map[MatchId, Match],
  activePlayers: Set[UserId],
  eliminatedPlayers: Set[UserId]
)
```

## Migration Path for Existing Tournaments

**Question**: Should Official Tournaments replace or coexist with existing Swiss/Arena?

**Recommendation**: Coexist
- Keep existing `/swiss/new` and `/tournament/new` endpoints
- Add new `/official/new` as alternative creation method
- Official tournaments internally create Swiss/Arena/Knockout instances
- This provides backward compatibility

## Success Metrics

1. **Functional**:
   - [ ] Users can create all three tournament types
   - [ ] Tournaments execute correctly
   - [ ] Brackets display properly
   - [ ] Real-time updates work

2. **UX**:
   - [ ] Forms are intuitive
   - [ ] Mobile experience is smooth
   - [ ] Accessibility standards met
   - [ ] Performance is acceptable

3. **Technical**:
   - [ ] Code follows existing patterns
   - [ ] Tests have good coverage
   - [ ] No regressions in existing features
   - [ ] Documentation is complete

## Timeline Estimate

- **Minimum Viable Product**: 3-4 weeks (1 developer)
- **Production Ready**: 6-8 weeks (1 developer)
- **With Team**: 2-3 weeks (3 developers)

## Next Steps

1. **Immediate** (Session 1):
   - Create module structure
   - Define data models
   - Create basic forms
   - Set up routing

2. **Short-term** (Week 1):
   - Implement knockout pairing logic
   - Create bracket generation algorithm
   - Build basic UI components

3. **Medium-term** (Weeks 2-3):
   - Complete frontend implementation
   - Add real-time updates
   - Implement bracket visualization
   - Testing

4. **Long-term** (Week 4+):
   - Polish and optimization
   - Production deployment
   - Monitoring and iteration

## Conclusion

This is a substantial feature that requires careful implementation across backend and frontend. The key to success is:
1. Maximum reuse of existing Swiss/Arena code
2. Clean architecture for the new Knockout format
3. Consistent UX matching existing patterns
4. Thorough testing at each phase
5. Incremental delivery and validation
