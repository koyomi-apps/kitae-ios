// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later
'use strict';
// A JavaScript twin of FigureRig.swift and FigurePainter.swift, reading the same
// ExerciseMotions.json, so poses can be checked as pictures before they reach Xcode.
// A change to the figure or the painter needs the same change in both.
const fs = require('fs');

const v = (x, y) => ({ x, y });
const add = (a, b) => v(a.x + b.x, a.y + b.y);
const sub = (a, b) => v(a.x - b.x, a.y - b.y);
const mul = (a, k) => v(a.x * k, a.y * k);
const len = a => Math.hypot(a.x, a.y);
const unit = a => { const l = len(a); return l > 0 ? mul(a, 1 / l) : v(0, 0); };
const front = d => v(d.y, -d.x);
const rotate = (p, r) => v(p.x * Math.cos(r) - p.y * Math.sin(r), p.x * Math.sin(r) + p.y * Math.cos(r));
const dir = deg => { const r = deg * Math.PI / 180; return v(Math.sin(r), -Math.cos(r)); };
const lerp = (a, b, t) => a + (b - a) * t;
const lerpV = (a, b, t) => add(a, mul(sub(b, a), t));
const f = p => `${p.x.toFixed(2)} ${p.y.toFixed(2)}`;
const BODY = { thigh: 46.5, shin: 46.7, upperArm: 35.3, forearm: 27.7, ankle: 7.4, far: v(-3.6, -1.6) };

function joint(root, target, l1, l2, bend) {
  const d = sub(target, root);
  const dist = Math.min(Math.max(len(d), Math.abs(l1 - l2) + 0.01), l1 + l2 - 0.01);
  const cosA = (l1 * l1 + dist * dist - l2 * l2) / (2 * l1 * dist);
  return add(root, mul(rotate(unit(d), bend * Math.acos(Math.min(1, Math.max(-1, cosA)))), l1));
}
const ease = x => 0.5 - 0.5 * Math.cos(Math.PI * Math.min(1, Math.max(0, x)));
function phase(t, k) {
  if (t < k[0]) return 0;
  if (t < k[1]) return ease((t - k[0]) / (k[1] - k[0]));
  if (t < k[2]) return 1;
  if (t < k[3]) return 1 - ease((t - k[2]) / (k[3] - k[2]));
  return 0;
}

// Shapes, as SVG path data. Capsules all wind the same way, as in Swift.
function capsule(p1, p2, r1, r2) {
  const d = sub(p2, p1), dist = len(d);
  if (dist < 0.01) return circle(p1, Math.max(r1, r2));
  const th = Math.atan2(d.y, d.x);
  const phi = Math.acos(Math.min(1, Math.max(-1, (r1 - r2) / dist)));
  const on = (c, r, a) => v(c.x + r * Math.cos(a), c.y + r * Math.sin(a));
  const large2 = 2 * phi > Math.PI ? 1 : 0, large1 = 2 * phi < Math.PI ? 1 : 0;
  return `M${f(on(p1, r1, th + phi))}L${f(on(p2, r2, th + phi))}` +
    `A${r2.toFixed(2)} ${r2.toFixed(2)} 0 ${large2} 0 ${f(on(p2, r2, th - phi))}` +
    `L${f(on(p1, r1, th - phi))}A${r1.toFixed(2)} ${r1.toFixed(2)} 0 ${large1} 0 ${f(on(p1, r1, th + phi))}Z`;
}
const circle = (c, r) => `M${(c.x - r).toFixed(2)} ${c.y.toFixed(2)}a${r} ${r} 0 1 0 ${2 * r} 0a${r} ${r} 0 1 0 ${-2 * r} 0Z`;
function ellipse(c, rx, ry, deg) {
  const pts = [];
  for (let i = 0; i < 48; i++) {
    const a = i / 48 * 2 * Math.PI;
    pts.push(add(c, rotate(v(rx * Math.cos(a), ry * Math.sin(a)), deg * Math.PI / 180)));
  }
  return 'M' + pts.map(f).join('L') + 'Z';
}
function outline(pts) {
  const n = pts.length;
  let d = `M${f(pts[0])}`;
  for (let i = 0; i < n; i++) {
    const p0 = pts[(i - 1 + n) % n], p1 = pts[i], p2 = pts[(i + 1) % n], p3 = pts[(i + 2) % n];
    d += `C${f(add(p1, mul(sub(p2, p0), 1 / 6)))} ${f(sub(p2, mul(sub(p3, p1), 1 / 6)))} ${f(p2)}`;
  }
  return d + 'Z';
}
const polygon = pts => 'M' + pts.map(f).join('L') + 'Z';
const hexagon = (c, r, deg) => polygon([0, 1, 2, 3, 4, 5].map(i => add(c, mul(dir(deg + i * 60), r))));

// Data, as ExerciseMotion.swift decodes it.
const V = a => v(a[0], a[1]);
function arm(a) {
  return 'upper' in a ? { kind: 'swing', upper: a.upper, bend: a.bend ?? 0, upperLength: a.upperLength ?? 1, flex: a.flex ?? 0 }
    : { kind: 'reach', hand: V(a.hand), onTorso: a.on === 'torso', scale: a.scale ?? 1, elbow: a.elbow ?? -1, upperLength: a.upperLength ?? 1, flex: a.flex ?? 0 };
}
function pose(j) {
  return {
    hip: V(j.hip), lean: j.lean, head: j.head ?? null, ankle: j.ankle ? V(j.ankle) : v(0, -BODY.ankle),
    foot: j.foot ?? 0, farAnkle: j.farAnkle ? V(j.farAnkle) : null, farFoot: j.farFoot ?? null,
    knee: j.knee ?? -1, arm: arm(j.arm), farArm: j.farArm ? arm(j.farArm) : null,
    thigh: j.thigh ?? null, shin: j.shin ?? null, farThigh: j.farThigh ?? null, farShin: j.farShin ?? null,
    curl: j.curl ?? 0, shrug: j.shrug ?? 0, kneeAt: j.kneeAt ? V(j.kneeAt) : null,
  };
}
// The muscles the figure shades, from the exercise's muscle map (ExerciseMuscles.json, like
// FigureMuscles in ExerciseMotion.swift): the ones it works most in olive, the ones that help
// lighter. The figure has no shapes of its own for the upper back and the hip's abductors, so its
// traps and glutes show them; the adductors, inside the thighs, aren't seen.
const STAND_INS = { upperBack: 'traps', abductors: 'glutes', adductors: null };
function figureMuscles(work) {
  const shape = x => (x in STAND_INS ? STAND_INS[x] : x);
  const primary = new Set((work ? work.primary : []).map(shape).filter(Boolean));
  const secondary = new Set((work ? work.secondary : []).map(shape).filter(x => x && !primary.has(x)));
  return { primary, secondary };
}
// The lighter shade first, so the muscles an exercise works most come out on top where they meet.
const ROLES = ['secondary', 'primary'];
let WORK = new Map();
function motion(j) {
  const front = j.view === 'front';
  return {
    key: j.key, name: j.name, view: front ? 'front' : 'side', legsFront: j.legsFront ?? false,
    gear: (j.gear || []).map(gearOf), shadowWidth: j.shadowWidth ?? 40,
    muscles: figureMuscles(WORK.get(j.key)), timing: j.timing || [0.08, 0.5, 0.58, 0.96],
    seconds: j.seconds ?? 2.4, shift: j.shift ?? 0, zoom: j.zoom ?? 1, shadow: j.shadow ? V(j.shadow) : v(4, 0),
    from: front ? frontPose(j.from) : pose(j.from), to: front ? frontPose(j.to) : pose(j.to),
  };
}
function lerpArm(a, b, t) {
  if (a.kind === 'swing' && b.kind === 'swing') return { kind: 'swing', upper: lerp(a.upper, b.upper, t), bend: lerp(a.bend, b.bend, t), upperLength: lerp(a.upperLength, b.upperLength, t), flex: lerp(a.flex, b.flex, t) };
  if (a.kind === 'reach' && b.kind === 'reach') return { ...a, hand: lerpV(a.hand, b.hand, t), scale: lerp(a.scale, b.scale, t), upperLength: lerp(a.upperLength, b.upperLength, t), flex: lerp(a.flex, b.flex, t) };
  return t < 0.5 ? a : b;
}
function lerpPose(a, b, t) {
  const mix = (x, y, fn) => (x == null || y == null) ? (x ?? y) : fn(x, y, t);
  return {
    hip: lerpV(a.hip, b.hip, t), lean: lerp(a.lean, b.lean, t), head: mix(a.head, b.head, lerp),
    ankle: lerpV(a.ankle, b.ankle, t), foot: lerp(a.foot, b.foot, t), farAnkle: mix(a.farAnkle, b.farAnkle, lerpV),
    farFoot: mix(a.farFoot, b.farFoot, lerp), knee: a.knee, arm: lerpArm(a.arm, b.arm, t),
    farArm: mix(a.farArm, b.farArm, lerpArm),
    thigh: mix(a.thigh, b.thigh, lerp), shin: mix(a.shin, b.shin, lerp),
    farThigh: mix(a.farThigh, b.farThigh, lerp), farShin: mix(a.farShin, b.farShin, lerp),
    curl: lerp(a.curl, b.curl, t), shrug: lerp(a.shrug, b.shrug, t), kneeAt: mix(a.kneeAt, b.kneeAt, lerpV),
  };
}

// The Figure struct.
const smooth01 = x => { const k = Math.min(1, Math.max(0, x)); return k * k * (3 - 2 * k); };
function figure(p) {
  // A kneeling pose pinned at the knee (`kneeAt`) finds its hip from the thigh's angle.
  if (p.kneeAt && p.thigh != null) p = { ...p, hip: sub(p.kneeAt, mul(dir(p.thigh), BODY.thigh)) };
  const spine = dir(p.lean), fr = v(-spine.y, spine.x);
  const pivot = add(p.hip, mul(spine, 18));
  const torso = (a, b) => {
    const q = add(p.hip, add(mul(spine, a), mul(fr, b)));
    if (!p.curl) return q;
    return add(pivot, rotate(sub(q, pivot), p.curl * Math.PI / 180 * smooth01((a - 10) / 16)));
  };
  const hd = dir(p.head ?? (p.lean + p.curl)), hf = v(-hd.y, hd.x);
  const neckBase = torso(52, 1.5), neckTop = add(neckBase, mul(hd, 9));
  const leg = (ankle, foot, thigh, shin, o) => {
    const hip = add(p.hip, o);
    if (thigh != null && shin != null) {
      const knee = add(hip, mul(dir(thigh), BODY.thigh));
      return { hip, knee, ankle: add(knee, mul(dir(shin), BODY.shin)), foot };
    }
    const a = add(ankle, o);
    return { hip, knee: joint(hip, a, BODY.thigh, BODY.shin, p.knee), ankle: a, foot };
  };
  const shoulderAt = torso(48 + p.shrug, -0.5);
  const armOf = (ap, o) => {
    const shoulder = add(shoulderAt, o);
    if (ap.kind === 'swing') {
      const elbow = add(shoulder, mul(dir(180 - ap.upper), BODY.upperArm * ap.upperLength));
      return { shoulder, elbow, wrist: add(elbow, mul(dir(180 - ap.upper - ap.bend), BODY.forearm)), scale: 1, flex: ap.flex };
    }
    const target = add(ap.onTorso ? torso(ap.hand.x, ap.hand.y) : ap.hand, o);
    const elbow = joint(shoulder, target, BODY.upperArm * ap.scale * ap.upperLength, BODY.forearm * ap.scale, ap.elbow);
    return { shoulder, elbow, wrist: add(elbow, mul(unit(sub(target, elbow)), BODY.forearm * ap.scale)), scale: ap.scale, flex: ap.flex };
  };
  const spineAt = a => rotate(spine, p.curl * Math.PI / 180 * smooth01((a - 10) / 16));
  return {
    pose: p, spine, front: fr, torso, spineAt, headDirection: hd, neckBase, neckTop,
    head: add(neckTop, add(mul(hd, 9.5), mul(hf, 1.5))),
    nearLeg: leg(p.ankle, p.foot, p.thigh, p.shin, v(0, 0)),
    farLeg: leg(p.farAnkle ?? p.ankle, p.farFoot ?? p.foot, p.farThigh ?? p.thigh, p.farShin ?? p.shin, BODY.far),
    nearArm: armOf(p.arm, v(0, 0)), farArm: armOf(p.farArm ?? p.arm, BODY.far),
  };
}

function muscle(a, b, ra, rb, k, w, side) {
  const out = mul(front(unit(sub(b, a))), side);
  const pt = (t, wd) => { const r = lerp(ra, rb, t); return [add(lerpV(a, b, t), mul(out, r - r * wd - 0.1)), r * wd]; };
  const [p1, r1] = pt(k[0], w[0]), [p2, r2] = pt(k[1], w[1]), [p3, r3] = pt(k[2], w[2]);
  return capsule(p1, p2, r1, r2) + capsule(p2, p3, r2, r3);
}
function legShape(l) {
  const td = unit(sub(l.knee, l.hip)), sd = unit(sub(l.ankle, l.knee));
  const mid = add(lerpV(l.hip, l.knee, 0.42), mul(front(td), 1));
  const calf = add(lerpV(l.knee, l.ankle, 0.3), mul(front(sd), -1.8));
  const turn = l.foot * Math.PI / 180;
  const heel = add(l.ankle, rotate(v(-4.5, 2.9), turn)), toe = add(l.ankle, rotate(v(17, 4.2), turn));
  return {
    thigh: capsule(l.hip, mid, 10.4, 9.2) + capsule(mid, l.knee, 9.2, 6.6),
    shin: capsule(l.knee, calf, 6.6, 7.2) + capsule(calf, l.ankle, 7.2, 3.9),
    foot: capsule(heel, toe, 4.5, 3.2) + capsule(l.ankle, heel, 3.9, 4.5),
    quads: muscle(l.hip, l.knee, 10.4, 6.6, [0.12, 0.5, 0.9], [0.55, 0.72, 0.7], 1),
    hamstrings: muscle(l.hip, l.knee, 10.4, 6.6, [0.14, 0.5, 0.88], [0.5, 0.62, 0.55], -1),
    calves: muscle(l.knee, l.ankle, 7.0, 3.9, [0.08, 0.3, 0.62], [0.62, 0.78, 0.5], -1),
  };
}
function armShape(a) {
  const ud = unit(sub(a.elbow, a.shoulder)), fd = unit(sub(a.wrist, a.elbow));
  const fore = add(lerpV(a.elbow, a.wrist, 0.3), mul(front(fd), 0.4));
  // The hand carries on from the forearm, bent at the wrist by `flex` degrees toward its front.
  const fl = a.flex * Math.PI / 180, hd = unit(add(mul(fd, Math.cos(fl)), mul(front(fd), Math.sin(fl))));
  const grip = add(a.wrist, mul(hd, 6.5 * Math.max(a.scale, 0.8)));
  const bent = Math.acos(Math.min(1, Math.max(-1, ud.x * fd.x + ud.y * fd.y))) * 180 / Math.PI;
  const k = Math.min(1, bent / 130);
  return {
    arm: a, grip,
    upper: capsule(a.shoulder, a.elbow, 6.8, 4.5),
    forearm: capsule(a.elbow, fore, 4.5, 5.0) + capsule(fore, a.wrist, 5.0, 3.3),
    hand: capsule(a.wrist, grip, 3.6, 3.9),
    biceps: muscle(a.shoulder, a.elbow, 6.8, 4.5, [0.2, 0.52, 0.86], [0.55, 0.8 + 0.15 * k, 0.6], 1),
    triceps: muscle(a.shoulder, a.elbow, 6.8, 4.5, [0.16, 0.5, 0.86], [0.55, 0.72, 0.58], -1),
    forearms: muscle(a.elbow, a.wrist, 5.0, 3.3, [0.1, 0.32, 0.75], [0.8, 0.85, 0.6], 1),
    delts: side => muscle(a.shoulder, a.elbow, 6.8, 4.5, [0, 0.17, 0.4], side === 0 ? [1, 1, 0.65] : [0.62, 0.62, 0.45], side),
  };
}

const THEMES = {
  light: { page: '#FDF9F0', card: '#F4F0E6', body: '#AAA696', far: '#CDC9BA', muscle: '#63611E',
    muscleFar: '#9C9A66', muscleSecondary: '#85823C', muscleSecondaryFar: '#B4B17E', gear: '#34332B', gearFace: '#4B4940', prop: '#DDD8C8', shadow: 'rgba(28,28,22,0.10)' },
  dark: { page: '#14140E', card: '#1E1E18', body: '#6F6C5F', far: '#47463D', muscle: '#CECA75',
    muscleFar: '#88864F', muscleSecondary: '#9A975E', muscleSecondaryFar: '#67653F', gear: '#D8D4C7', gearFace: '#B9B5A8', prop: '#34332B', shadow: 'rgba(0,0,0,0.34)' },
};

function gearOf(j) {
  return {
    kind: j.kind, on: j.on ?? null, from: j.from ? V(j.from) : null, to: j.to ? V(j.to) : null,
    at: j.at ? V(j.at) : null, thick: j.thick ?? 8, legs: j.legs ?? [], width: j.width ?? 4,
    handle: j.handle ?? 'bar', stack: j.stack ?? false, profile: j.profile ?? false, one: j.one ?? false, hang: j.hang ?? false,
    radius: j.radius ?? null, front: j.front ?? false,
    top: j.top ?? 70, bottom: j.bottom ?? 0, out: j.out ?? null, length: j.length ?? 40,
    anchor: j.anchor ? V(j.anchor) : null, side: j.side ?? 1,
  };
}

let clipSeq = 0;
// The painter: returns { defs, body } for one figure, in the same order as FigurePainter.draw.
function paint(m, F, c) {
  const defs = [], out = [];
  const fill = (d, col) => out.push(`<path d="${d}" fill="${col}"/>`);
  const fillInside = (clip, d, col) => {
    const id = `k${++clipSeq}`;
    defs.push(`<clipPath id="${id}"><path d="${clip}"/></clipPath>`);
    out.push(`<path d="${d}" fill="${col}" clip-path="url(#${id})"/>`);
  };
  const is = (x, role) => m.muscles[role].has(x);
  const near = { primary: c.muscle, secondary: c.muscleSecondary }, far = { primary: c.muscleFar, secondary: c.muscleSecondaryFar };
  const gears = kind => m.gear.filter(g => g.kind === kind);
  const plate = (at, r) => { fill(circle(at, r), c.gear); fill(circle(at, r * 0.735), c.gearFace); fill(circle(at, r * 0.21), c.body); };
  const leg = (l, body, mc) => {
    fill(l.thigh, body); fill(l.shin, body); fill(l.foot, body);
    for (const r of ROLES) {
      if (is('quads', r)) fillInside(l.thigh, l.quads, mc[r]);
      if (is('hamstrings', r)) fillInside(l.thigh, l.hamstrings, mc[r]);
      if (is('calves', r)) fillInside(l.shin, l.calves, mc[r]);
    }
  };
  const armDraw = (a, body, mc) => {
    fill(a.upper, body); fill(a.forearm, body); fill(a.hand, body);
    const cap = a.upper + circle(a.arm.shoulder, 7.2);
    for (const r of ROLES) {
      if (is('biceps', r)) fillInside(a.upper, a.biceps, mc[r]);
      if (is('triceps', r)) fillInside(a.upper, a.triceps, mc[r]);
      if (is('forearms', r)) fillInside(a.forearm, a.forearms, mc[r]);
      if (is('sideDelts', r)) fillInside(cap, a.delts(0), mc[r]);
      if (is('frontDelts', r)) fillInside(cap, a.delts(1), mc[r]);
      if (is('rearDelts', r)) fillInside(cap, a.delts(-1), mc[r]);
    }
  };
  const farArm = armShape(F.farArm), nearArm = armShape(F.nearArm);
  const farLeg = legShape(F.farLeg), nearLeg = legShape(F.nearLeg);
  // Points on the body that gear follows.
  const bodyPoint = name => {
    const nl = F.nearLeg;
    switch (name) {
      case 'hands': return nearArm.grip;
      case 'ankle': return nl.ankle;
      case 'shin': return add(lerpV(nl.knee, nl.ankle, 0.8), mul(front(unit(sub(nl.ankle, nl.knee))), 8));
      case 'calf': return add(lerpV(nl.knee, nl.ankle, 0.82), mul(front(unit(sub(nl.ankle, nl.knee))), -8.5));
      case 'thigh': return add(lerpV(nl.hip, nl.knee, 0.62), mul(front(unit(sub(nl.knee, nl.hip))), 9));
      case 'knee': return add(lerpV(nl.hip, nl.knee, 0.86), mul(front(unit(sub(nl.knee, nl.hip))), 9.5));
      case 'hips': return F.torso(-2, 11);
      case 'chest': return F.torso(36, 15);
      default: return nearArm.grip;
    }
  };
  const goblet = () => {
    const at = F.torso(40, 19), d = F.spineAt(40), n = front(d);
    fill(capsule(add(at, mul(d, -7)), add(at, mul(d, 7)), 1.7, 1.7), c.gear);
    for (const k of [-8, 8]) {
      const e = add(at, mul(d, k));
      fill(capsule(add(e, mul(n, -6.5)), add(e, mul(n, 6.5)), 2.8, 2.8), c.gear);
    }
  };
  const dumbbell = (arm, near) => {
    const g = arm.grip;
    if (gears('dumbbells').some(d => d.hang)) {
      const top = add(g, v(0, 2));
      fill(capsule(top, add(top, v(0, 15)), 1.7, 1.7), near ? c.gear : c.body);
      for (const y of [0, 15]) fill(capsule(add(top, v(-7, y)), add(top, v(7, y)), 2.8, 2.8), near ? c.gear : c.body);
      return;
    }
    if (gears('dumbbells').some(d => d.profile)) {
      const fd = unit(sub(arm.arm.wrist, arm.arm.elbow)), axis = front(fd);
      const col = near ? c.gear : c.body;
      fill(capsule(add(g, mul(axis, -7)), add(g, mul(axis, 7)), 1.7, 1.7), col);
      for (const k of [-8, 8]) {
        const e = add(g, mul(axis, k));
        fill(capsule(add(e, mul(fd, -6.5)), add(e, mul(fd, 6.5)), 2.8, 2.8), col);
      }
      return;
    }
    if (!near) { fill(hexagon(add(g, v(-1, -1)), 8.6, 30), c.body); return; }
    const at = add(g, v(1, 1));
    fill(hexagon(at, 8.6, 30), c.gear); fill(hexagon(at, 6.0, 30), c.gearFace); fill(circle(at, 2.4), c.body);
  };

  fill(ellipse(m.shadow, m.shadowWidth ?? 40, 3.4, 0), c.shadow);

  // Behind everything: machine stacks and frame posts.
  for (const g of gears('cable')) if (g.stack) fill(capsule(v(g.from.x, -3), v(g.from.x, g.from.y), 6.5, 6.5), c.prop);
  for (const g of gears('post')) fill(capsule(g.from, g.to, g.width / 2, g.width / 2), c.prop);

  // The far side.
  leg(farLeg, c.far, far);
  armDraw(farArm, c.far, far);
  const handDumbbells = gears('dumbbells').filter(d => d.on !== 'chest');
  const hasDumbbells = handDumbbells.length > 0, oneDumbbell = handDumbbells.some(d => d.one);
  if (hasDumbbells && !oneDumbbell) dumbbell(farArm, false);

  // Benches, seats and pads, under the body.
  const pad = g => {
    let from = g.from, to = g.to;
    if (g.on === 'back') { from = F.torso(g.top, g.out ?? -12.5); to = F.torso(g.bottom, (g.out ?? -12.5) - 1); }
    if (g.on === 'front') { from = F.torso(g.bottom, g.out ?? 13.5); to = F.torso(g.top, g.out ?? 13.5); }
    g = { ...g, from, to };
    const d = unit(sub(g.to, g.from)), right = v(-d.y, d.x), h = g.thick / 2;
    for (const x of g.legs) {
      const k = (x - g.from.x) / (g.to.x - g.from.x || 1), top = add(lerpV(g.from, g.to, k), mul(right, h));
      fill(capsule(top, v(x, -2), 1.9, 1.9), c.prop);
      fill(capsule(v(x - 8, -1.6), v(x + 8, -1.6), 1.6, 1.6), c.prop);
    }
    fill(capsule(add(g.from, mul(right, h)), add(g.to, mul(right, h)), h, h), c.prop);
  };
  for (const g of gears('pad')) if (!g.front) pad(g);

  // A T-bar runs from its anchor on the floor, between the legs, to the hands; plates at its end.
  for (const g of gears('tbar')) {
    const grip = nearArm.grip, d = unit(sub(grip, g.anchor)), n = v(-d.y, d.x), end = add(grip, mul(d, 12));
    fill(capsule(g.anchor, add(grip, mul(d, 16)), 1.8, 1.8), c.gear);
    fill(capsule(add(end, mul(n, -(g.radius ?? 15))), add(end, mul(n, g.radius ?? 15)), 3.4, 3.4), c.gear);
  }

  // The body.
  const torsoPts = [[-7, 3], [2, 9], [16, 9], [29, 12.5], [41, 12.5], [50.5, 8],
    [54, -2], [51, -9.5], [39, -11.5], [24, -8.5], [11, -10.5], [0, -14.5], [-8.5, -9]];
  const torso = outline(torsoPts.map(([a, b]) => F.torso(a, b)));
  const lean = F.pose.lean, turnAt = a => lean + F.pose.curl * smooth01((a - 10) / 16);
  fill(torso, c.body);
  for (const r of ROLES) {
    const mc = near[r];
    if (is('glutes', r)) fillInside(torso, ellipse(F.torso(-1.5, -8.5), 8.5, 10.5, turnAt(-1.5)), mc);
    if (is('chest', r)) fillInside(torso, ellipse(F.torso(37, 8.5), 6.5, 9, turnAt(37)), mc);
    if (is('abs', r)) fillInside(torso, capsule(F.torso(4, 5.8), F.torso(27, 8.5), 3.4, 3.4), mc);
    if (is('obliques', r)) fillInside(torso, ellipse(F.torso(14, 2), 4.5, 9, turnAt(14)), mc);
    if (is('lats', r)) fillInside(torso, ellipse(F.torso(33, -7.5), 4.5, 11, turnAt(33)), mc);
    if (is('traps', r)) fillInside(torso, ellipse(F.torso(50, -6), 5, 7, turnAt(50)), mc);
    if (is('lowerBack', r)) fillInside(torso, capsule(F.torso(6, -8), F.torso(26, -6.2), 2.8, 2.8), mc);
  }

  for (const g of gears('barbell')) if (g.on === 'back') {
    const bar = F.torso(50, -9), pl = add(bar, v(-9, 2.5));
    fill(capsule(pl, bar, 2.6, 2.6), c.gear);
    plate(pl, g.radius ?? 17);
  }
  // A front squat's bar rests on the front of the shoulders; drawn behind the head so the face stays clear.
  for (const g of gears('barbell')) if (g.on === 'front') plate(add(F.torso(50, 11), v(5, 1)), g.radius ?? 15);

  const hf = v(-F.headDirection.y, F.headDirection.x), turn = F.pose.head ?? (F.pose.lean + F.pose.curl);
  fill(capsule(F.neckBase, F.neckTop, 5.6, 5.0), c.body);
  fill(ellipse(F.head, 10.2, 11.6, turn), c.body);
  fill(ellipse(sub(add(F.head, mul(hf, 3.2)), mul(F.headDirection, 5.5)), 7.4, 6.4, turn), c.body);

  // Pads in front of the body, such as a preacher bench's, under the near arm.
  for (const g of gears('pad')) if (g.front) pad(g);

  // Cables run to the middle of the body, behind the near arm; bars are gripped by it.
  for (const g of gears('cable')) {
    const end = bodyPoint(g.on ?? 'hands');
    fill(capsule(g.from, end, 0.8, 0.8), c.gear);
    fill(circle(g.from, 4.2), c.gear); fill(circle(g.from, 1.7), c.prop);
  }
  for (const g of gears('bar')) fill(circle(g.at, 3.6), c.gear);

  // Foot plates move with the near foot, under its sole.
  for (const g of gears('footplate')) {
    const nl = F.nearLeg, turnF = nl.foot * Math.PI / 180;
    const heel = add(nl.ankle, rotate(v(-4.5, 2.9), turnF)), toe = add(nl.ankle, rotate(v(17, 4.2), turnF));
    const d = unit(sub(toe, heel)), n = v(-d.y, d.x), h = g.thick / 2;
    const mid = add(lerpV(heel, toe, 0.5), mul(n, 4 + h));
    fill(capsule(add(mid, mul(d, -g.length / 2)), add(mid, mul(d, g.length / 2)), h, h), c.prop);
  }
  if (gears('dumbbells').some(d => d.on === 'chest')) goblet();
  // A weight plate held upright in both hands, seen edge-on.
  for (const g of gears('plate')) {
    const at = nearArm.grip, r = g.radius ?? 12;
    fill(capsule(v(at.x, at.y - r), v(at.x, at.y + r), 2.6, 2.6), c.gear);
  }

  leg(nearLeg, c.body, near);
  armDraw(nearArm, c.body, near);

  for (const g of gears('barbell')) if (g.on === 'hands' || g.on == null) plate(nearArm.grip, g.radius ?? 17);
  for (const g of gears('barbell')) if (g.on === 'hips') plate(F.torso(-1, 12), g.radius ?? 17);
  for (const g of gears('wheel')) plate(nearArm.grip, g.radius ?? 8);
  for (const g of gears('ezbar')) plate(nearArm.grip, g.radius ?? 12.5);
  if (hasDumbbells) dumbbell(nearArm, true);
  for (const g of gears('cable')) {
    const end = bodyPoint(g.on ?? 'hands');
    if (g.handle === 'rope') {
      const d = unit(sub(end, g.from));
      fill(capsule(sub(end, mul(d, 9)), add(end, mul(d, 2)), 2.2, 2.4), c.gear);
    } else if (g.handle === 'bar') {
      fill(circle(end, 3.2), c.gear);
    } else if (g.handle === 'single') {
      fill(circle(end, 3.6), c.gear); fill(circle(end, 1.8), c.body);
    }
  }
  for (const g of gears('handle')) fill(circle(bodyPoint(g.on ?? 'hands'), 3.4), c.gear);
  for (const g of gears('roller')) {
    const at = g.at ?? bodyPoint(g.on);
    fill(circle(at, g.radius ?? 6.5), c.prop); fill(circle(at, 2.2), c.gear);
  }
  return { defs: defs.join(''), body: out.join('') };
}

// ----- The front view: the figure facing the viewer, for movements out to the sides -----
// Angles work as in the side view for the +x arm and leg (the figure's left, on the right of the
// picture); the other arm and leg mirror them unless `otherArm` says otherwise. `turn` turns the
// whole figure (a side plank lies at about -80), `lean` bends the body sideways above the waist,
// and `torsoLength`, `upperLength`, `foreLength`, `thighLength` and `shinLength` shorten parts that
// point toward or away from the viewer.
const FRONT = { shoulder: 19.5, hipJoint: 9 };
const FRONT_TORSO = [[-10, 0], [-8, 9], [-4, 15], [4, 16.5], [14, 14], [24, 15], [34, 18], [42, 21], [48, 22],
  [51.5, 17.5], [54, 10], [55.5, 5], [55.5, -5], [54, -10], [51.5, -17.5], [48, -22], [42, -21], [34, -18],
  [24, -15], [14, -14], [4, -16.5], [-4, -15], [-8, -9]];
function frontArm(a) {
  if (!a) return null;
  const lengths = { upperLength: a.upperLength ?? 1, foreLength: a.foreLength ?? 1 };
  return 'upper' in a ? { kind: 'swing', upper: a.upper, bend: a.bend ?? 0, ...lengths }
    : { kind: 'reach', hand: V(a.hand), onTorso: a.on === 'torso', elbow: a.elbow ?? 1, ...lengths };
}
function frontPose(j) {
  return {
    hip: V(j.hip), turn: j.turn ?? 0, lean: j.lean ?? 0, torsoLength: j.torsoLength ?? 1, shrug: j.shrug ?? 0,
    head: j.head ?? 0, headHeight: j.headHeight ?? 1, thigh: j.thigh ?? 3, thighLength: j.thighLength ?? 1, shin: j.shin ?? 1.5,
    shinLength: j.shinLength ?? 1, arm: frontArm(j.arm), otherArm: frontArm(j.otherArm),
  };
}
function lerpFrontArm(a, b, t) {
  if (a.kind !== b.kind) return t < 0.5 ? a : b;
  const s = { ...a, upperLength: lerp(a.upperLength, b.upperLength, t), foreLength: lerp(a.foreLength, b.foreLength, t) };
  return a.kind === 'swing' ? { ...s, upper: lerp(a.upper, b.upper, t), bend: lerp(a.bend, b.bend, t) }
    : { ...s, hand: lerpV(a.hand, b.hand, t) };
}
function lerpFront(a, b, t) {
  const L = k => lerp(a[k], b[k], t);
  return {
    hip: lerpV(a.hip, b.hip, t), turn: L('turn'), lean: L('lean'), torsoLength: L('torsoLength'), shrug: L('shrug'),
    head: L('head'), headHeight: L('headHeight'), thigh: L('thigh'), thighLength: L('thighLength'), shin: L('shin'), shinLength: L('shinLength'),
    arm: lerpFrontArm(a.arm, b.arm, t),
    otherArm: a.otherArm && b.otherArm ? lerpFrontArm(a.otherArm, b.otherArm, t) : (a.otherArm ?? b.otherArm),
  };
}
const frameSide = deg => { const r = deg * Math.PI / 180; return v(Math.cos(r), Math.sin(r)); };
function frontFigure(p) {
  const up = dir(p.turn), side = frameSide(p.turn), k = p.torsoLength, turnR = p.turn * Math.PI / 180;
  const pelvis = (a, b) => add(p.hip, add(mul(up, a), mul(side, b)));
  const pivot = pelvis(18 * k, 0);
  // Shrugging lifts the shoulders fully and the base of the neck a little.
  const lift = a => p.shrug * smooth01((a - 42) / 4) * (a >= 55 ? 0.35 : 1);
  const torso = (a, b) => {
    const q = pelvis((a + lift(a)) * k, b);
    return p.lean ? add(pivot, rotate(sub(q, pivot), p.lean * Math.PI / 180 * smooth01((a - 10) / 16))) : q;
  };
  const upperUp = dir(p.turn + p.lean), headUp = dir(p.turn + p.lean + p.head);
  const neckBase = torso(55, 0), neckTop = add(neckBase, mul(upperUp, 9 * k));
  // A head bent forward with the body sits lower, in front of the shoulders (`headHeight` below 1).
  const head = add(neckTop, mul(headUp, 10.5 * p.headHeight));
  const showsNeck = p.headHeight > 0.5;
  const arm = (ap, s, explicit) => {
    const shoulder = torso(48, FRONT.shoulder * s), m = d => v(d.x * s, d.y);
    const l1 = BODY.upperArm * ap.upperLength, l2 = BODY.forearm * ap.foreLength;
    let elbow, wrist;
    if (ap.kind === 'swing') {
      elbow = add(shoulder, mul(m(dir(180 - ap.upper)), l1));
      wrist = add(elbow, mul(m(dir(180 - ap.upper - ap.bend)), l2));
    } else {
      const target = ap.onTorso ? torso(ap.hand.x, ap.hand.y * s)
        : explicit ? ap.hand : v(p.hip.x + (ap.hand.x - p.hip.x) * s, ap.hand.y);
      elbow = joint(shoulder, target, l1, l2, ap.elbow * s);
      wrist = add(elbow, mul(unit(sub(target, elbow)), l2));
    }
    return { shoulder, elbow, wrist, side: s, foreLength: ap.foreLength };
  };
  const leg = s => {
    const hip = pelvis(0, FRONT.hipJoint * s), m = d => v(d.x * s, d.y);
    const knee = add(hip, mul(rotate(m(dir(180 - p.thigh)), turnR), BODY.thigh * p.thighLength));
    const ankle = add(knee, mul(rotate(m(dir(180 - p.shin)), turnR), BODY.shin * p.shinLength));
    return { hip, knee, ankle, side: s };
  };
  return {
    pose: p, up, side, upperUp, headUp, torso, neckBase, neckTop, head, showsNeck,
    turnAt: a => p.turn + p.lean * smooth01((a - 10) / 16),
    arms: [arm(p.otherArm ?? p.arm, -1, !!p.otherArm), arm(p.arm, 1, false)],
    legs: [leg(-1), leg(1)],
  };
}

// Draws a front figure back to front: shadow, frames and stacks, pads, legs, body and muscles
// (legs in front of the body when they come toward the viewer), head, cables, arms, and gear in
// the hands. Arms, and legs in front of the body, get a thin outline in the card's colour so they
// stand apart from the body behind them.
function paintFront(m, F, c) {
  const defs = [], out = [];
  const fill = (d, col) => out.push(`<path d="${d}" fill="${col}"/>`);
  const clipId = (d, evenOdd) => {
    const id = `k${++clipSeq}`;
    defs.push(`<clipPath id="${id}"><path d="${d}"${evenOdd ? ' clip-rule="evenodd"' : ''}/></clipPath>`);
    return id;
  };
  const fillInside = (clip, d, col) => out.push(`<path d="${d}" fill="${col}" clip-path="url(#${clipId(clip)})"/>`);
  // An outline in the card's colour, left off around a joint so limbs stay joined to the body.
  const separate = (d, joint, r) => {
    const keep = clipId(`M-2000 -2000H2000V2000H-2000Z${circle(joint, r)}`, true);
    out.push(`<path d="${d}" fill="none" stroke="${c.card}" stroke-width="3.2" stroke-linejoin="round" clip-path="url(#${keep})"/>`);
  };
  const is = (x, role) => m.muscles[role].has(x);
  const tint = { primary: c.muscle, secondary: c.muscleSecondary };
  const gears = kind => m.gear.filter(g => g.kind === kind);
  const plate = (at, r) => { fill(circle(at, r), c.gear); fill(circle(at, r * 0.735), c.gearFace); fill(circle(at, r * 0.21), c.body); };
  const turnDeg = F.pose.turn, turnR = turnDeg * Math.PI / 180;

  const legParts = l => {
    const mid = lerpV(l.hip, l.knee, 0.42), calf = lerpV(l.knee, l.ankle, 0.3);
    const thigh = capsule(l.hip, mid, 10.4, 9.2) + capsule(mid, l.knee, 9.2, 6.6);
    const shin = capsule(l.knee, calf, 6.6, 7.2) + capsule(calf, l.ankle, 7.2, 3.9);
    const foot = ellipse(add(l.ankle, rotate(v(1.5 * l.side, 3.4), turnR)), 5.2, 3.6, turnDeg);
    return { thigh, shin, foot, all: thigh + shin + foot };
  };
  const drawLegs = inFront => {
    for (const l of F.legs) {
      const s = legParts(l);
      if (inFront) separate(s.all, l.hip, 11.5);
      fill(s.thigh, c.body); fill(s.shin, c.body); fill(s.foot, c.body);
    }
  };
  const armParts = a => {
    const fd = unit(sub(a.wrist, a.elbow)), fore = lerpV(a.elbow, a.wrist, 0.3);
    const grip = add(a.wrist, mul(fd, 6.5 * Math.max(a.foreLength, 0.5)));
    const upper = capsule(a.shoulder, a.elbow, 6.8, 4.5);
    const forearm = capsule(a.elbow, fore, 4.5, 5.0) + capsule(fore, a.wrist, 5.0, 3.3);
    const hand = capsule(a.wrist, grip, 3.6, 3.9);
    return { a, fd, grip, upper, forearm, hand, all: upper + forearm + hand };
  };
  const arms = F.arms.map(armParts);
  const sideGrip = s => arms.find(x => x.a.side === s).grip;

  fill(ellipse(m.shadow, m.shadowWidth ?? 40, 3.4, 0), c.shadow);
  for (const g of gears('cable')) if (g.stack) fill(capsule(v(g.from.x, -3), v(g.from.x, g.from.y), 6.5, 6.5), c.prop);
  for (const g of gears('post')) fill(capsule(g.from, g.to, g.width / 2, g.width / 2), c.prop);
  const pad = g => {
    const d = unit(sub(g.to, g.from)), right = v(-d.y, d.x), h = g.thick / 2;
    for (const x of g.legs) {
      const k = (x - g.from.x) / (g.to.x - g.from.x || 1), top = add(lerpV(g.from, g.to, k), mul(right, h));
      fill(capsule(top, v(x, -2), 1.9, 1.9), c.prop);
      fill(capsule(v(x - 8, -1.6), v(x + 8, -1.6), 1.6, 1.6), c.prop);
    }
    fill(capsule(add(g.from, mul(right, h)), add(g.to, mul(right, h)), h, h), c.prop);
  };
  for (const g of gears('pad')) if (!g.front) pad(g);
  if (!m.legsFront) drawLegs(false);

  const torso = outline(FRONT_TORSO.map(([a, b]) => F.torso(a, b)));
  fill(torso, c.body);
  const both = f => { f(1); f(-1); };
  const ta = F.turnAt;
  const glutes = s => m.legsFront ? ellipse(F.torso(2, 17 * s), 3.2, 7, ta(2)) : ellipse(F.torso(1, 13.5 * s), 3.6, 7.5, ta(1));
  for (const r of ROLES) {
    const mc = tint[r];
    if (is('chest', r)) both(s => fillInside(torso, ellipse(F.torso(37.5, 8.5 * s), 8.2, 6.4, ta(37.5)), mc));
    if (is('abs', r)) fillInside(torso, capsule(F.torso(5, 0), F.torso(31, 0), 5, 5.2), mc);
    if (is('obliques', r)) both(s => fillInside(torso, ellipse(F.torso(16, 12 * s), 3.6, 8.5, ta(16)), mc));
    if (is('lats', r)) both(s => fillInside(torso, ellipse(F.torso(31, 16 * s), 3.4, 8, ta(31)), mc));
    if (is('traps', r)) both(s => fillInside(torso, capsule(F.torso(53.5, 6 * s), F.torso(50.5, 15.5 * s), 3.2, 3.6), mc));
    if (is('glutes', r) && !m.legsFront) both(s => fillInside(torso, glutes(s), mc));
  }
  if (m.legsFront) drawLegs(true);
  for (const r of ROLES) {
    if (is('glutes', r) && m.legsFront) both(s => fillInside(torso + legParts(F.legs[s > 0 ? 1 : 0]).thigh, glutes(s), tint[r]));
  }

  const headTurn = turnDeg + F.pose.lean + F.pose.head;
  // A head hanging forward (headHeight below 0.5) shows its crown in front of the shoulders, without a neck or jaw.
  if (F.showsNeck) {
    fill(capsule(F.neckBase, F.neckTop, 5.6, 5.0), c.body);
    fill(ellipse(add(F.head, mul(F.headUp, 1.5)), 9.8, 10.4, headTurn), c.body);
    fill(ellipse(sub(F.head, mul(F.headUp, 5)), 7.2, 6.8, headTurn), c.body);
  } else {
    out.push(`<path d="${circle(F.head, 10.6)}" fill="none" stroke="${c.card}" stroke-width="3.2"/>`);
    fill(circle(F.head, 10), c.body);
  }
  for (const g of gears('pad')) if (g.front) pad(g);

  for (const g of gears('cable')) {
    const end = sideGrip(g.side);
    fill(capsule(g.from, end, 0.8, 0.8), c.gear);
    fill(circle(g.from, 4.2), c.gear); fill(circle(g.from, 1.7), c.prop);
  }
  // A band stretched between the hands, in front of the body.
  if (gears('band').length) fill(capsule(sideGrip(1), sideGrip(-1), 1.2, 1.2), c.gear);

  // From the front the three delts are one shoulder cap, shaded as the most worked of them.
  const delts = ['frontDelts', 'sideDelts', 'rearDelts'];
  const deltsRole = delts.some(x => is(x, 'primary')) ? 'primary' : delts.some(x => is(x, 'secondary')) ? 'secondary' : null;
  for (const p of arms) {
    separate(p.all, p.a.shoulder, 9);
    fill(p.upper, c.body); fill(p.forearm, c.body); fill(p.hand, c.body);
    const a = p.a;
    for (const r of ROLES) {
      if (deltsRole === r) fillInside(p.upper + circle(a.shoulder, 7.2), muscle(a.shoulder, a.elbow, 6.8, 4.5, [0, 0.16, 0.38], [1, 1, 0.7], 0), tint[r]);
      if (is('triceps', r)) fillInside(p.upper, muscle(a.shoulder, a.elbow, 6.8, 4.5, [0.25, 0.55, 0.88], [0.5, 0.6, 0.5], a.side), tint[r]);
      if (is('biceps', r)) fillInside(p.upper, muscle(a.shoulder, a.elbow, 6.8, 4.5, [0.25, 0.55, 0.88], [0.5, 0.62, 0.5], -a.side), tint[r]);
    }
  }

  const dumbbells = gears('dumbbells');
  for (const p of arms) {
    if (!dumbbells.length || (dumbbells.some(d => d.one) && p.a.side !== 1)) continue;
    const g = p.grip;
    if (dumbbells.some(d => d.profile)) {
      const axis = front(p.fd);
      fill(capsule(add(g, mul(axis, -7)), add(g, mul(axis, 7)), 1.7, 1.7), c.gear);
      for (const k of [-8, 8]) {
        const e = add(g, mul(axis, k));
        fill(capsule(add(e, mul(p.fd, -6.5)), add(e, mul(p.fd, 6.5)), 2.8, 2.8), c.gear);
      }
    } else {
      fill(hexagon(g, 8.6, 30), c.gear); fill(hexagon(g, 6.0, 30), c.gearFace); fill(circle(g, 2.4), c.body);
    }
  }
  for (const g of gears('plate')) plate(sideGrip(1), g.radius ?? 12);
  for (const g of gears('handle')) for (const p of arms) fill(circle(p.grip, 3.4), c.gear);
  for (const g of gears('kneepads')) for (const l of F.legs) {
    const at = add(l.knee, mul(F.side, 10.5 * l.side));
    fill(capsule(add(at, mul(F.up, 8)), add(at, mul(F.up, -8)), 4, 4), c.prop);
  }
  return { defs: defs.join(''), body: out.join('') };
}

// One card at (x, y) of size w × h, laid out like FigurePainter.place.
function card(m, t, theme, x, y, w, h, still = false) {
  const c = THEMES[theme];
  const scale = Math.min(1.15, (h - 34) / 190) * m.zoom;
  const g = `translate(${(x + w / 2 + m.shift * scale).toFixed(2)} ${(y + h - 20).toFixed(2)}) scale(${scale.toFixed(4)})`;
  let defs = '', body = '';
  const draw = p => m.view === 'front' ? paintFront(m, frontFigure(p), c) : paint(m, figure(p), c);
  const blend = (a, b, k) => m.view === 'front' ? lerpFront(a, b, k) : lerpPose(a, b, k);
  if (still) {
    const a = draw(m.from), b = draw(m.to);
    defs = a.defs + b.defs;
    body = `<g opacity="0.4">${a.body}</g>${b.body}`;
  } else {
    const p = draw(blend(m.from, m.to, phase(t, m.timing)));
    defs = p.defs; body = p.body;
  }
  return { defs, body: `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="24" fill="${c.card}"/><g transform="${g}">${body}</g>` };
}

// Reads the animations, and the muscle map's file for their muscles.
function load(file, musclesFile = require('path').join(__dirname, '../../Kitae/Exercises/Muscles/ExerciseMuscles.json')) {
  WORK = new Map(JSON.parse(fs.readFileSync(musclesFile, 'utf8')).exercises.map(e => [e.key, e]));
  return JSON.parse(fs.readFileSync(file, 'utf8')).map(motion);
}
function render(svg, file, scale = 1) {
  const { Resvg } = require('@resvg/resvg-js');
  fs.writeFileSync(file, new Resvg(svg, { fitTo: { mode: 'zoom', value: scale } }).render().asPng());
}

module.exports = { load, card, render, THEMES, motion, figure, pose, lerpPose, phase, frontFigure, frontPose, lerpFront, v, add, sub, mul, dir, unit, rotate, len, BODY };
