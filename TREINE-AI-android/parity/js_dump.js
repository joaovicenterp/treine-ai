/* Lado JavaScript do arnês de paridade: mesmas entradas, mesma saída. */
const fs = require('fs');
const path = require('path');
const SRC = '/home/claude/treineai/src';

const window = { document: { createElement: () => ({ getContext: () => null }) } };
global.window = window;
global.navigator = { userAgent: 'node' };

function load(f) {
  const code = fs.readFileSync(path.join(SRC, f), 'utf8');
  new Function('window', 'navigator', 'document', code)(window, global.navigator, window.document);
}
load('data.js');
load('motion.js');

const M = window.TA_MOTION;
const D = window.TA_DATA;

const r4 = v => Math.round(v * 10000) / 10000;
const poseErrFor = i => {
  switch (i % 4) {
    case 0: return {};
    case 1: return { lean: .6 };
    case 2: return { asym: .5, drift: .4 };
    default: return { shallow: .5, lean: .2 };
  }
};

const svc = M.createService();
const rows = [];

for (const ex of D.EX) {
  for (let i = 0; i <= 20; i++) {
    const p = i / 20;
    const lm = M.poseFor(ex.pattern, p, poseErrFor(i), ex.rep);
    const m = M.metricsOf(lm);
    const JOINTVAL = {
      knee: m.knee, elbow: m.elbow, hip: m.hip, shoulder: m.shoulder, ankle: m.ankle,
      trunk: m.trunk, shrug: m.shrug, hipAbd: m.hipAbd, hold: 0,
      hipMin: Math.min(m.hipL, m.hipR), hipMax: Math.max(m.hipL, m.hipR),
      kneeMin: Math.min(m.kneeL, m.kneeR), kneeMax: Math.max(m.kneeL, m.kneeR)
    };
    svc.exercise = ex;
    const setup = svc.checkSetup(lm, 0.5);
    const full = M.bboxOf(lm, M.KEYPOINTS, true);

    const hist = {}, rates = {};
    const ctx = {
      m, p, joint: ex.rep.joint, down: ex.rep.bottom < ex.rep.top,
      stab: (k, v) => {
        const h = (hist[k] = hist[k] || []);
        h.push(v); if (h.length > 26) h.shift();
        if (h.length < 10) return 0;
        const mean = h.reduce((a, b) => a + b, 0) / h.length;
        return Math.sqrt(h.reduce((a, b) => a + (b - mean) ** 2, 0) / h.length);
      },
      rate: (k, v) => { const prev = rates[k]; rates[k] = v; return prev == null ? 0 : v - prev; }
    };

    const live = [], rep = [];
    (ex.checks || []).forEach(name => {
      const r = M.RULES[name];
      if (!r) return;
      if (r.phase === 'live') { const o = r.run(ctx); if (o) live.push(o.code); }
      else { const o = r.run(ctx, { depth: .7, tDown: 1.4, tUp: .9, restP: .08 }); if (o) rep.push(o.code); }
    });

    rows.push({
      ex: ex.id, p: r4(p), joint: r4(JOINTVAL[ex.rep.joint] ?? 0),
      knee: r4(m.knee), elbow: r4(m.elbow), hip: r4(m.hip), shoulder: r4(m.shoulder),
      ankle: r4(m.ankle), trunk: r4(m.trunk), torsoLean: r4(m.torsoLean), shrug: r4(m.shrug),
      hipAbd: r4(m.hipAbd), headFwd: r4(m.headFwd), elbowOff: r4(m.elbowOff),
      hipDev: r4(m.hipDev), valgus: r4(m.valgus),
      size: r4(Math.max(full.w, full.h)),
      hint: setup.hint, ready: !!setup.ready, live, rep
    });
  }
}

process.stdout.write(JSON.stringify(rows, null, 0));
