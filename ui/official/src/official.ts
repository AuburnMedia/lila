import { init, classModule, attributesModule } from 'snabbdom';
import { OfficialOpts } from './interfaces';
import OfficialCtrl from './ctrl';
import view from './view/main';

const patch = init([classModule, attributesModule]);

export default function (opts: OfficialOpts) {
  const element = document.querySelector('.official') as HTMLElement;
  
  site.socket = site.makeSocket('/official/' + opts.data.tournament.id, opts.data.tournament.id, {
    receive(t: string, d: any) {
      ctrl.socket.receive(t, d);
    },
  });

  const ctrl = new OfficialCtrl(opts, redraw, site.trans(opts.data.i18n));

  function redraw() {
    patch(element, view(ctrl));
  }

  redraw();

  return {
    socketReceive: site.socket.receive,
  };
}

// Make it available globally for page scripts
(window as any).LichessOfficial = (opts: OfficialOpts) => {
  return exports.default(opts);
};
