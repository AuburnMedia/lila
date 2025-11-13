export interface OfficialTournament {
  id: string;
  name: string;
  tournamentType: 'swiss' | 'arena' | 'knockout';
  swissId?: string;
  arenaId?: string;
  knockoutId?: string;
  status: 'created' | 'started' | 'finished';
  nbPlayers: number;
  startsAt: number;
  clock: {
    limit: number;
    increment: number;
  };
  variant: string;
  rated: boolean;
}

export interface OfficialData {
  tournament: OfficialTournament;
  me?: {
    id: string;
    username: string;
  };
  standings?: StandingsData;
  bracket?: BracketData;
}

export interface StandingsData {
  players: Player[];
  page: number;
  nbPages: number;
}

export interface Player {
  rank: number;
  userId: string;
  username: string;
  rating: number;
  score: number;
  perf?: number;
}

// Knockout-specific interfaces
export interface BracketData {
  rounds: BracketRound[];
  currentRound: number;
  totalRounds: number;
}

export interface BracketRound {
  roundNumber: number;
  roundName: string;
  matches: BracketMatch[];
}

export interface BracketMatch {
  id: string;
  round: number;
  position: number;
  player1?: MatchPlayer;
  player2?: MatchPlayer;
  winner?: string;
  gameId?: string;
  status: 'pending' | 'inProgress' | 'completed' | 'bye';
  isBye: boolean;
}

export interface MatchPlayer {
  userId: string;
  username: string;
  rating: number;
  seed?: number;
}

export interface OfficialOpts {
  data: OfficialData;
  userId?: string;
  socketSend: SocketSend;
}

export type SocketSend = (type: string, data?: any) => void;
