/* Lado JavaScript da etapa 3: todas as 18 regras, quadro a quadro. */
const fs = require('fs');
const path = require('path');
const SRC = '/home/claude/treineai/src';

global.performance = { now: () => 0 };
const window = { document: { createElement: () => ({ getContext: () => null }) } };
global.window = window;
global.navigator = { userAgent: 'node' };
function load(f) {
  new Function('window', 'navigator', 'document', fs.readFileSync(path.join(SRC, f), 'utf8'))(
    window, global.navigator, window.document);
}
load('data.js');
load('motion.js');
const M = window.TA_MOTION;
const D = window.TA_DATA;

const ALL_RULES = ['depth', 'rom', 'tempo', 'lockout', 'symmetry', 'torsoLean', 'backNeutral',
  'kneeValgus', 'hipSag', 'headPos', 'torsoStable', 'momentum', 'elbowDrift',
  'hipShoot', 'hipLockout', 'kneeLock', 'hipStable', 'shoulderDepth'];

function wobble(lm, f) {
  const a = Math.sin(f * 0.7) * 0.05;
  const b = Math.sin(f * 0.41 + 1.3) * 0.06;
  const c = Math.sin(f * 1.13) * 0.04;
  const S = { 11: [a, b * .6], 12: [a, b * .6], 13: [b * 1.4, c], 14: [b * 1.4, c],
    23: [c, a * 1.2], 24: [c, a * 1.2], 25: [b, c * 1.5], 26: [b, c * 1.5],
    27: [c * .5, a * .4], 28: [c * .5, a * .4], 0: [b * 2, a] };
  return lm.map((p, i) => S[i] ? { x: p.x + S[i][0], y: p.y + S[i][1], z: p.z, visibility: p.visibility } : p);
}

const PATS = ['squat', 'benchPress', 'curl', 'row', 'plank', 'hinge', 'pullup', 'lunge',
  'pushup', 'overheadPress', 'hipThrust', 'legPress'];
let picks = PATS.map(pat => D.EX.find(e => e.pattern === pat)).filter(Boolean)
  .concat(D.EX.filter(e => e.hold).slice(0, 2));
picks = picks.filter((e, i) => picks.findIndex(x => x.id === e.id) === i);

const rows = [];
for (const ex of picks) {
  const hist = {}, rates = {};
  const ctx = {
    m: null, p: 0, joint: ex.rep.joint, down: ex.rep.bottom < ex.rep.top,
    stab: (k, v) => {
      const h = (hist[k] = hist[k] || []);
      h.push(v); if (h.length > 26) h.shift();
      if (h.length < 10) return 0;
      const mean = h.reduce((a, b) => a + b, 0) / h.length;
      return Math.sqrt(h.reduce((a, b) => a + (b - mean) ** 2, 0) / h.length);
    },
    rate: (k, v) => { const prev = rates[k]; rates[k] = v; return prev == null ? 0 : v - prev; }
  };
  for (let f = 0; f < 45; f++) {
    const phase = (f % 15) / 14;
    const lm = wobble(M.poseFor(ex.pattern, phase, { lean: .3, asym: .4 }, ex.rep), f);
    ctx.m = M.metricsOf(lm);
    ctx.p = phase * 1.4;
    const depth = .95 - (f % 5) * .06;
    const repArg = { depth, tDown: .3 + (f % 4) * .5, tUp: .2 + (f % 3) * .4, restP: (f % 6) * .05 };
    const issues = [];
    ALL_RULES.forEach(n => {
      const r = M.RULES[n]; if (!r || r.phase !== 'live') return;
      const o = r.run(ctx); if (o) issues.push({ c: o.code, l: o.level, w: o.weight, m: o.msg });
    });
    ALL_RULES.forEach(n => {
      const r = M.RULES[n]; if (!r || r.phase !== 'rep') return;
      const o = r.run(ctx, repArg); if (o) issues.push({ c: o.code, l: o.level, w: o.weight, m: o.msg });
    });
    rows.push({ ex: ex.id, f, issues });
  }
}
/* hipShoot: quadril subindo mais rápido que os ombros */
{
  const hs = D.EX.find(e => e.pattern === 'squat');
  const hist = {}, rates = {};
  const ctxH = {
    m: null, p: .5, joint: 'knee', down: true,
    stab: (k, v) => {
      const h = (hist[k] = hist[k] || []);
      h.push(v); if (h.length > 26) h.shift();
      if (h.length < 10) return 0;
      const mean = h.reduce((a, b) => a + b, 0) / h.length;
      return Math.sqrt(h.reduce((a, b) => a + (b - mean) ** 2, 0) / h.length);
    },
    rate: (k, v) => { const prev = rates[k]; rates[k] = v; return prev == null ? 0 : v - prev; }
  };
  for (let f = 0; f < 14; f++) {
    const base = M.poseFor('squat', .5, {}, hs.rep);
    const dHip = -0.12 * f, dSh = -0.02 * f;
    const lm = base.map((p, i) => (i === 23 || i === 24) ? { x: p.x, y: p.y + dHip, z: p.z, visibility: p.visibility }
      : (i === 11 || i === 12) ? { x: p.x, y: p.y + dSh, z: p.z, visibility: p.visibility } : p);
    ctxH.m = M.metricsOf(lm);
    const issues = [];
    ALL_RULES.forEach(n => {
      const r = M.RULES[n]; if (!r || r.phase !== 'live') return;
      const o = r.run(ctxH); if (o) issues.push({ c: o.code, l: o.level, w: o.weight, m: o.msg });
    });
    rows.push({ ex: 'hipShootCase', f, issues });
  }
}

process.stdout.write(JSON.stringify(rows));
