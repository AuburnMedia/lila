import { h, VNode } from 'snabbdom';
import OfficialCtrl from '../ctrl';
import { bracket } from './bracket';

export default function (ctrl: OfficialCtrl): VNode {
  return h('main.official', [
    h('div.official__main', [renderTournamentInfo(ctrl), renderContent(ctrl)]),
    h('aside.official__side', [renderActions(ctrl), renderPlayerCount(ctrl)]),
  ]);
}

function renderTournamentInfo(ctrl: OfficialCtrl): VNode {
  const tour = ctrl.tournament;
  return h('div.official__info.box', [
    h('h1', tour.name),
    h('div.official__type', `${formatType(tour.tournamentType)} Tournament`),
    h('div.official__details', [
      h('span.official__clock', `${tour.clock.limit / 60}+${tour.clock.increment}`),
      h('span.official__variant', tour.variant),
      h('span.official__rated', tour.rated ? 'Rated' : 'Casual'),
    ]),
  ]);
}

function renderContent(ctrl: OfficialCtrl): VNode {
  if (ctrl.isKnockout) {
    const bracketView = bracket(ctrl);
    if (bracketView) return bracketView;
  }

  // For Swiss and Arena, show standings table
  return h('div.official__standings.box', [
    h('h2', 'Standings'),
    renderStandings(ctrl),
  ]);
}

function renderStandings(ctrl: OfficialCtrl): VNode {
  const standings = ctrl.data.standings;
  if (!standings || !standings.players.length) {
    return h('div.official__no-players', 'No players yet');
  }

  return h('table.slist', [
    h('thead', [
      h('tr', [
        h('th', 'Rank'),
        h('th', 'Player'),
        h('th', 'Rating'),
        h('th', 'Score'),
        ctrl.isSwiss && h('th', 'Perf'),
      ]),
    ]),
    h(
      'tbody',
      standings.players.map(player =>
        h('tr', [
          h('td', `${player.rank}`),
          h('td', h('a', { attrs: { href: `/@/${player.username}` } }, player.username)),
          h('td', `${player.rating}`),
          h('td', `${player.score}`),
          ctrl.isSwiss && h('td', player.perf !== undefined ? `${player.perf}` : '-'),
        ]),
      ),
    ),
  ]);
}

function renderActions(ctrl: OfficialCtrl): VNode {
  return h('div.official__actions.box', [
    h('h2', 'Actions'),
    ctrl.isCreated &&
      h(
        'button.button',
        {
          hook: {
            insert: vnode => {
              (vnode.elm as HTMLElement).onclick = () => ctrl.join();
            },
          },
        },
        'Join Tournament',
      ),
    ctrl.isStarted &&
      ctrl.opts.userId &&
      h(
        'button.button.button-red',
        {
          hook: {
            insert: vnode => {
              (vnode.elm as HTMLElement).onclick = () => ctrl.withdraw();
            },
          },
        },
        'Withdraw',
      ),
  ]);
}

function renderPlayerCount(ctrl: OfficialCtrl): VNode {
  return h('div.official__players.box', [
    h('div.official__players-count', [
      h('span.number', `${ctrl.tournament.nbPlayers}`),
      h('span.label', ctrl.tournament.nbPlayers === 1 ? 'player' : 'players'),
    ]),
  ]);
}

function formatType(type: string): string {
  return type.charAt(0).toUpperCase() + type.slice(1);
}
