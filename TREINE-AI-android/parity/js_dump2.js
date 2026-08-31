/* Lado JavaScript da etapa 2: posicionamento degradado + motor de repetição. */
const fs = require('fs');
const path = require('path');
const SRC = '/home/claude/treineai/src';

let CLOCK = 0;
global.performance = { now: () => CLOCK };
global.requestAnimationFrame = () => 0;
global.cancelAnimationFrame = () => {};

const window = { document: { createElement: () => ({ getContext: () => null }) } };
global.window = window;
global.navigator = { userAgent: 'node' };

function load(f) {
  const code = fs.readFileSync(path.join(SRC, f), 'utf8');
  new Function('window', 'navigator', 'document', 'performance', 'requestAnimationFrame', 'cancelAnimationFrame', code)(
    window, global.navigator, window.document, global.performance,
    global.requestAnimationFrame, global.cancelAnimationFrame);
}
load('data.js');
load('motion.js');

const M = window.TA_MOTION;
const D = window.TA_DATA;
const r4 = v => Math.round(v * 10000) / 10000;

const scaleShift = (lm, k, dx, dy) => lm.map(p => ({
  x: .5 + (p.x - .5) * k + dx, y: .5 + (p.y - .5) * k + dy, z: p.z, visibility: p.visibility
}));
const hide = (lm, idx) => lm.map((p, i) => idx.has(i) ? { x: p.x, y: p.y, z: p.z, visibility: .05 } : p);

const cases = base => [
  ['normal', base, .5],
  ['escuro', base, .05],
  ['semPose', null, .5],
  ['longe', scaleShift(base, .35, 0, 0), .5],
  ['muitoLonge', scaleShift(base, .2, 0, 0), .5],
  ['limiteDistancia', scaleShift(base, .62, 0, 0), .5],
  ['perto', scaleShift(base, 2.4, 0, 0), .5],
  ['cortadoEmbaixo', scaleShift(base, 1.6, 0, .45), .5],
  ['cortadoEmCima', scaleShift(base, 1.6, 0, -.55), .5],
  ['esquerda', scaleShift(base, 1, -.35, 0), .5],
  ['direita', scaleShift(base, 1, .35, 0), .5],
  ['pernasOcultas', hide(base, new Set([25, 26, 27, 28, 29, 30, 31, 32])), .5],
  ['bracosOcultos', hide(base, new Set([13, 14, 15, 16, 17, 18, 19, 20, 21, 22])), .5],
  ['troncoOculto', hide(base, new Set([11, 12, 23, 24])), .5],
  ['escuroELonge', scaleShift(base, .2, 0, 0), .05]
];

/* mesmo gerador determinístico do lado Kotlin */
function series(ex, reps, frames, shallow, jitter) {
  const out = [];
  let t = 0;
  let seed = 12345n;
  const M64 = (1n << 64n) - 1n;
  const rnd = () => {
    seed = (seed * 6364136223846793005n + 1442695040888963407n) & M64;
    const shifted = Number(BigInt.asUintN(64, seed) >> 33n);
    return (shifted / 2147483648) % 1;
  };
  for (let r = 0; r < reps; r++) {
    for (let f = 0; f < frames; f++) {
      const phase = f / (frames - 1);
      const tri = phase < .5 ? phase * 2 : (1 - phase) * 2;
      const amp = 1 - shallow * (r % 2);
      const p = Math.max(0, Math.min(1, tri * amp + (rnd() - .5) * jitter));
      const e = { lean: (r % 3) * .25, asym: r % 2 === 0 ? .3 : 0 };
      out.push([t, M.poseFor(ex.pattern, p, e, ex.rep)]);
      t += 33;
    }
    t += 400;
  }
  return out;
}

const setup = [];
for (const ex of D.EX) {
  const base = M.poseFor(ex.pattern, .25, {}, ex.rep);
  const svc = M.createService();
  svc.exercise = ex;
  for (const [name, lm, bright] of cases(base)) {
    const s = svc.checkSetup(lm, bright);
    setup.push({
      ex: ex.id, case: name, hint: s.hint, body: !!s.body, light: !!s.light,
      distance: !!s.distance, full: !!s.full, framing: !!s.framing, ready: !!s.ready,
      size: r4(s.size || 0)
    });
  }
}

const engine = [];
for (const ex of D.EX) {
  const frames = series(ex, 6, 26, .3, .04);
  const svc = M.createService();
  svc.exercise = ex;
  svc.cfg = ex.rep;
  svc.targetReps = 5;
  const fb = [], qualities = [];
  let blockedTimes = 0, targetAt = -1;
  svc.on('feedback', i => fb.push(i.code));
  svc.on('quality', q => qualities.push(q));
  svc.on('blocked', b => { if (b) blockedTimes++; });
  svc.on('target', () => { if (targetAt < 0) targetAt = svc.repCount; });

  CLOCK = 0;
  svc.start();
  for (const [ts, lm] of frames) {
    CLOCK = ts;
    if (ex.hold) svc._analyseHold(M.metricsOf(lm), ts);
    else svc._analyse(M.metricsOf(lm), ts);
  }
  CLOCK = frames[frames.length - 1][0] + 100;
  const sum = svc.summary();
  const errors = {};
  Object.keys(sum.errors).sort().forEach(k => { errors[k] = sum.errors[k]; });

  engine.push({
    ex: ex.id, reps: sum.reps, valid: sum.validReps, score: sum.score,
    best: sum.best, worst: sum.worst, avgDepth: r4(sum.avgDepth), avgTempo: r4(sum.avgTempo),
    mainError: sum.mainError, errors, blocked: blockedTimes, targetAt,
    quality: qualities.length ? qualities[qualities.length - 1] : -1,
    feedback: fb, repScores: sum.repDetail.map(r => r.score),
    repDepth: sum.repDetail.map(r => r4(r.depth))
  });
}

process.stdout.write(JSON.stringify({ setup, engine }));
