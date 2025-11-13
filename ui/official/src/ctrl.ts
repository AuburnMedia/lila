import { OfficialOpts, OfficialData, OfficialTournament } from './interfaces';

export default class OfficialCtrl {
  data: OfficialData;
  trans: Trans;
  redraw: () => void;

  constructor(
    readonly opts: OfficialOpts,
    redraw: () => void,
    trans: Trans,
  ) {
    this.data = opts.data;
    this.trans = trans;
    this.redraw = redraw;
  }

  get tournament(): OfficialTournament {
    return this.data.tournament;
  }

  get isSwiss(): boolean {
    return this.tournament.tournamentType === 'swiss';
  }

  get isArena(): boolean {
    return this.tournament.tournamentType === 'arena';
  }

  get isKnockout(): boolean {
    return this.tournament.tournamentType === 'knockout';
  }

  get isCreated(): boolean {
    return this.tournament.status === 'created';
  }

  get isStarted(): boolean {
    return this.tournament.status === 'started';
  }

  get isFinished(): boolean {
    return this.tournament.status === 'finished';
  }

  join = (): void => {
    this.opts.socketSend('join');
  };

  withdraw = (): void => {
    this.opts.socketSend('withdraw');
  };

  reload = (data: OfficialData): void => {
    this.data = data;
    this.redraw();
  };

  socket = {
    send: this.opts.socketSend,
    receive: (type: string, data: any): boolean => {
      switch (type) {
        case 'reload':
          this.reload(data);
          return true;
        case 'redirect':
          site.redirect(data);
          return true;
        default:
          return false;
      }
    },
  };
}
