import { h, VNode } from 'snabbdom';
import OfficialCtrl from '../ctrl';
import { BracketData, BracketRound, BracketMatch } from '../interfaces';

export function bracket(ctrl: OfficialCtrl): VNode | undefined {
  const bracketData = ctrl.data.bracket;
  if (!bracketData) return undefined;

  return h('div.bracket-container', [
    h('div.bracket-header', [
      h('h2', `Tournament Bracket`),
      h('div.bracket-info', [
        h('span', `Round ${bracketData.currentRound} of ${bracketData.totalRounds}`),
      ]),
    ]),
    h('div.bracket-scroll', [h('div.bracket', bracketData.rounds.map(round => renderRound(ctrl, round)))]),
  ]);
}

function renderRound(ctrl: OfficialCtrl, round: BracketRound): VNode {
  return h('div.bracket-round', { key: round.roundNumber }, [
    h('div.round-header', round.roundName),
    h('div.round-matches', round.matches.map(match => renderMatch(ctrl, match))),
  ]);
}

function renderMatch(ctrl: OfficialCtrl, match: BracketMatch): VNode {
  const isUserMatch =
    ctrl.opts.userId &&
    (match.player1?.userId === ctrl.opts.userId || match.player2?.userId === ctrl.opts.userId);

  return h(
    'div.bracket-match',
    {
      key: match.id,
      class: {
        'match-pending': match.status === 'pending',
        'match-inprogress': match.status === 'inProgress',
        'match-completed': match.status === 'completed',
        'match-bye': match.isBye,
        'user-match': isUserMatch,
      },
      hook: {
        insert: vnode => {
          if (match.gameId) {
            (vnode.elm as HTMLElement).onclick = () => {
              site.redirect(`/${match.gameId}`);
            };
          }
        },
      },
    },
    [
      h('div.match-connector'),
      h('div.match-players', [
        renderPlayer(match.player1, match.winner === match.player1?.userId),
        renderPlayer(match.player2, match.winner === match.player2?.userId),
      ]),
      match.status === 'inProgress' && h('div.match-status', 'Live'),
      match.isBye && h('div.match-status', 'Bye'),
    ],
  );
}

function renderPlayer(player: BracketMatch['player1'], isWinner: boolean): VNode {
  if (!player) {
    return h('div.match-player.empty', [h('span.player-name', 'TBD')]);
  }

  return h('div.match-player', { class: { winner: isWinner } }, [
    h('span.player-seed', player.seed ? `#${player.seed}` : ''),
    h('span.player-name', player.username),
    h('span.player-rating', `${player.rating}`),
  ]);
}
