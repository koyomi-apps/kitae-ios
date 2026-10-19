// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later
'use strict';
// Checks every motion through its loop: limbs keep their lengths, nothing goes below the floor,
// and hands reach what they hold. node check.js FILE
const M = require('./motion');
const list = M.load(process.argv[2]);
const out = [];
const near = (a, b, what, m, i) => { if (Math.abs(a - b) > 0.02) out.push(`${m.key}: ${what} ${a.toFixed(2)} vs ${b.toFixed(2)} at ${i}`); };
for (const m of list) {
  const seen = new Set();
  const say = s => { if (!seen.has(s)) { seen.add(s); out.push(`${m.key}: ${s}`); } };
  for (let i = 0; i <= 20; i++) {
    const t = M.phase(i / 20, m.timing);
    if (m.view === 'front') {
      const p = M.lerpFront(m.from, m.to, t), F = M.frontFigure(p);
      for (const l of F.legs) {
        near(M.len(M.sub(l.knee, l.hip)), M.BODY.thigh * p.thighLength, 'thigh', m, i);
        if (l.knee.y > 0.5 || l.ankle.y > -2) say(`leg below the floor at ${i} (ankle ${l.ankle.y.toFixed(1)})`);
      }
      for (const a of F.arms) if (a.wrist.y > 0 || a.elbow.y > 0) say(`arm below the floor at ${i}`);
      continue;
    }
    const p = M.lerpPose(m.from, m.to, t), F = M.figure(p);
    for (const [name, l] of [['near', F.nearLeg], ['far', F.farLeg]]) {
      near(M.len(M.sub(l.knee, l.hip)), M.BODY.thigh, name + ' thigh', m, i);
      const shin = M.len(M.sub(l.ankle, l.knee));
      if (Math.abs(shin - M.BODY.shin) > 0.02) say(`${name} ankle out of reach (${shin.toFixed(1)}) at ${i}`);
      if (l.knee.y > -1 || l.ankle.y > -2) say(`${name} leg below the floor at ${i}`);
    }
    for (const [name, a, ap] of [['near', F.nearArm, p.arm], ['far', F.farArm, p.farArm ?? p.arm]]) {
      if (a.wrist.y > 0 || a.elbow.y > 0) say(`${name} arm below the floor at ${i}`);
      if (ap.kind === 'reach') {
        const o = name === 'far' ? M.BODY.far : M.v(0, 0);
        const target = M.add(ap.onTorso ? F.torso(ap.hand.x, ap.hand.y) : ap.hand, o);
        const miss = M.len(M.sub(a.wrist, target));
        if (miss > 1.5) say(`${name} hand misses its target by ${miss.toFixed(1)} at ${i}`);
      }
    }
    if (F.head.y > -8) say(`head low (${F.head.y.toFixed(1)}) at ${i}`);
  }
}
console.log([...new Set(out.map(l => l.replace(/ at \d+.*$/, '').replace(/ by [\d.]+/, '').replace(/ \([^)]*\)/, '')))].join('\n') || 'all good');
console.log(list.length, 'motions checked');
