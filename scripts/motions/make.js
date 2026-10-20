// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later
'use strict';
// Writes ExerciseMotions.json: each exercise's start and end poses and gear. The muscles the
// figure shades come from the muscle map's ExerciseMuscles.json (see muscles.js).
// Units: a figure 190 tall, floor at y = 0, y down, facing right, near ankle at x = 0.
// Run: node make.js [OUT.json], which writes the app's copy by default.
const fs = require('fs');

const M = require('./motion');
const r1 = x => Math.round(x * 10) / 10;
const pt = q => [r1(q.x), r1(q.y)];
const add = (a, b) => [r1(a[0] + b[0]), r1(a[1] + b[1])];
const body = p => M.figure(M.pose({ arm: { upper: 0 }, ...p }));
// A point on a pose's torso: `a` along the spine from the hip, `b` out the front.
const on = (p, a, b) => pt(body(p).torso(a, b));
// The near shoulder of a pose.
const shoulder = p => pt(body(p).nearArm.shoulder);
// A pad behind the back, from `top` down to `bottom` along the spine; or under the front.
const backPad = (p, top, bottom, out = -12.5) => ({ kind: 'pad', from: on(p, top, out), to: on(p, bottom, out - 1) });
const frontPad = (p, bottom, top, out = 13.5) => ({ kind: 'pad', from: on(p, bottom, out), to: on(p, top, out) });
// A frame post straight down from a point to the floor.
const postDown = (from, width = 4) => ({ kind: 'post', from, to: [from[0], -2], width });

const stand = { hip: [-1, -99.6], lean: 1.5, head: 0 };
// Bench and seat tops.
const BENCH = -47, SEAT = -48;
const lie = { hip: [0, -59.5], lean: -90, ankle: [44, -7.4] };
const flatBench = { kind: 'pad', from: [-92, BENCH], to: [24, BENCH], legs: [-76, 8] };
const lieShoulder = shoulder({ ...lie, arm: { upper: 0 } });

const incline = { hip: [0, -58], lean: -55, head: -45, ankle: [50, -7.4] };
const inclineShoulder = shoulder({ ...incline, arm: { upper: 0 } });
const inclineChest = on(incline, 42, 16);

const preacher = { hip: [-12, -58], lean: 22, head: 12, ankle: [40, -7.4] };
const preacherArm = (() => {
  const F = body({ ...preacher, arm: { upper: 48, bend: 0 } });
  const s = F.nearArm.shoulder, e = F.nearArm.elbow, d = M.unit(M.sub(e, s)), back = M.v(-d.y, d.x);
  return { from: pt(M.add(M.add(s, M.mul(d, 7)), M.mul(back, 7))), to: pt(M.add(M.add(e, M.mul(d, 6)), M.mul(back, 7))) };
})();

const reclined = { hip: [0, -58], lean: -45, head: -25, ankle: [52, -7.4] };
const prone = { hip: [-24, -80], lean: 52, head: 70, ankle: [-74, -7.4] };
const proneShoulder = shoulder({ ...prone, arm: { upper: 0 } });
const seated = { hip: [-4, -60], lean: -3, head: 0, ankle: [38, -7.4] };
const pulldown = { hip: [-4, -58], lean: -12, head: -10, ankle: [34, -7.4] };
const hangLegs = { thigh: 172, shin: 205, farThigh: 166, farShin: 198 };
const kneel = { hip: [-6, -53.1], thigh: 180, shin: 270, foot: 166 };

// The hip that puts torso point (along, out) at `contact` for a lean, for poses that pivot on the
// back or shoulders (bridges, hip thrusts).
const pivotHip = (lean, contact, along, out) => {
  const q = body({ hip: [0, 0], lean }).torso(along, out);
  return [r1(contact[0] - q.x), r1(contact[1] - q.y)];
};
// The hip of a body straight from the ankle at `lean` (push-ups, planks).
const lineHip = (ankle, lean) => { const d = M.dir(lean); return [r1(ankle[0] + d.x * 93.2), r1(ankle[1] + d.y * 93.2)]; };
// The ankle of a foot turned by `foot` degrees with the ball of its sole on point `ball` (calf raises).
const ballAnkle = (ball, foot) => { const q = M.rotate(M.v(12, 7.6), foot * Math.PI / 180); return [r1(ball[0] - q.x), r1(ball[1] - q.y)]; };
// The near sole of a pose, heel to toe, for platforms under the feet.
const sole = (p, extra = 6) => {
  const l = body(p).nearLeg, r = l.foot * Math.PI / 180;
  const heel = M.add(l.ankle, M.rotate(M.v(-4.5, 2.9), r)), toe = M.add(l.ankle, M.rotate(M.v(17, 4.2), r));
  const d = M.unit(M.sub(toe, heel)), n = M.v(-d.y, d.x);
  return { from: pt(M.add(M.add(heel, M.mul(n, 4.5)), M.mul(d, -extra))), to: pt(M.add(M.add(toe, M.mul(n, 3.2)), M.mul(d, extra))) };
};
// Where hands rest on a seated calf raise's knee pad.
const kneePad = p => {
  const l = body(p).nearLeg, td = M.unit(M.sub(l.knee, l.hip));
  return pt(M.add(M.add(l.hip, M.mul(M.sub(l.knee, l.hip), 0.86)), M.mul(M.v(td.y, -td.x), 16)));
};

// Side-view setups shared by the exercises below.
const hackTop = { hip: [-40, -95], lean: -38, head: -20, ankle: [0, -14], foot: -18 };
const calfLow = ballAnkle([12, -10], -20), calfHigh = ballAnkle([12, -10], 28);
const THRUST = [-48, -47], BRIDGE = [-42, 0];
const decline = { hip: [12, -64], lean: -100, head: -96, ankle: [46, -26] };
const declineShoulder = shoulder(decline);
const lyingFaceDown = { hip: [0, -60], lean: 90, head: 75, thigh: 268 };
const oneArmRow = { hip: [-30, -98.5], lean: 80, head: 62, ankle: [-22, -7.4], farThigh: 180, farShin: 270, farFoot: 90,
  farArm: { hand: [24, -52], elbow: 1 } };
const legPress = { hip: [-20, -40], lean: -52, head: -30 };
const legExtension = { hip: [-4, -58], lean: -8, head: -4, thigh: 90 };
const seatedLegCurl = { hip: [-4, -62], lean: -12, head: -6, thigh: 88 };
const seatedCalf = { hip: [-40, -68], lean: 4, head: 0 };
const seatedCalfLow = ballAnkle([12, -14], -22), seatedCalfHigh = ballAnkle([12, -14], 30);
const chestPress = { hip: [-4, -60], lean: -4, head: 0, ankle: [38, -7.4] };
const rowSeat = { hip: [-22, -38], head: 0, ankle: [58, -32], foot: -90 };
const concentration = { hip: [-8, -58], lean: 32, head: 50, ankle: [34, -7.4] };
const floorLie = { hip: [0, -12.5], ankle: [40, -7.4] };
const PUSHUP_TOES = [-62, -20.7], PLANK_TOES = [-70, -20.7];
const pullUpBar = [{ kind: 'post', from: [-34, -2], to: [-34, -236], width: 5 }, { kind: 'post', from: [-34, -228], to: [6, -228], width: 3 },
  { kind: 'bar', at: [6, -228] }];
const dipLegs = { thigh: 192, shin: 238, farThigh: 186, farShin: 232 };
// A Smith machine's rail, behind its bar from the floor up.
const smithRail = x => ({ kind: 'post', from: [x, -2], to: [x, -204], width: 6 });
// The point above `x` that a hand reaches with its arm `reach` long from shoulder `s`.
const reachAbove = (s, x, reach = 61) => [r1(x), r1(s[1] - Math.sqrt(reach * reach - (x - s[0]) ** 2))];
const inclineBench = [{ kind: 'pad', from: [-16, SEAT], to: [22, SEAT], legs: [8] }, backPad(incline, 74, 2), postDown(on(incline, 36, -22))];
const inclinePress = { hip: [-4, -60], lean: -12, head: -4, ankle: [38, -7.4] };
// A body straight from the ankle at `lean`, as in a push-up.
const straightBody = (ankle, lean) => ({ hip: lineHip(ankle, lean), lean, thigh: 180 + lean, shin: 180 + lean, foot: lean });
const DECLINE_TOES = [-70, -67.7];
const chestDipLegs = { thigh: 200, shin: 248, farThigh: 194, farShin: 242 };
const seatedPress = { hip: [-4, -60], lean: -2, head: 0, ankle: [38, -7.4] };
const pressSeat = [{ kind: 'pad', from: [-24, -50], to: [12, -50], legs: [-6] }, backPad(seatedPress, 72, 4)];
const facingPad = { hip: [-4, -60], lean: 4, head: 2, ankle: [38, -7.4] };
const landminePress = { hip: [-6, -99.4], lean: 8, head: 4 };
// A pike: straight legs from the feet at `legs` degrees, the body folded at the hips to `lean`.
const PIKE_FEET = [-49.4, -12], PIKE_HANDS = [44.3, -5];
const pike = (legs, lean) => ({ hip: lineHip(PIKE_FEET, legs), lean, thigh: legs + 180, shin: legs + 180, foot: legs + 30 });
const preacherBench = [{ kind: 'pad', from: [-32, SEAT], to: [4, SEAT], legs: [-14] },
  { kind: 'pad', ...preacherArm, thick: 9, front: true, legs: [r1((preacherArm.from[0] + preacherArm.to[0]) / 2)] }];
// Seated, leaning forward with the forearms along the thighs and the wrists just past the knees.
const wristCurl = { hip: [-8, -58], lean: 30, head: 40, ankle: [34, -7.4] };
const WRIST_ON_KNEE = [40, -70];
// Facing away from a high cable in a split stance, leaning into it (overhead extensions).
const splitLean = { hip: [-6, -93], lean: 32, head: 22, ankle: [22, -7.4], farAnkle: [-46, -12], farFoot: 28 };
// The hip that puts the near shoulder at `s` for a lean.
const hipBelow = (s, lean) => { const q = shoulder({ hip: [0, 0], lean }); return [r1(s[0] - q[0]), r1(s[1] - q[1])]; };
// Bench dips: hands on the bench's front edge behind the hips, heels on the floor ahead, and
// the shoulders dropping until the elbows are at about a right angle.
const BENCH_DIP_HANDS = [-26, -52];
const benchDip = { ankle: [60, -9.5], foot: -45 };
// Kneeling on an assisted dip machine's pad, which rides on a lever from the machine's column.
const kneelOnPad = { thigh: 186, shin: 271, foot: 125 };
const assistedDips = [{ kind: 'post', from: [-96, -2], to: [-96, -222], width: 7 }, { kind: 'post', from: [-96, -112], to: [2, -112], width: 4 },
  { kind: 'bar', at: [2, -112] }, { kind: 'pad', on: 'shins', anchor: [-95, -82] }];
const DIAMOND_HANDS = [64, -5];
// Lat pull-downs: a close grip leans back a little more; one arm at a time stays more upright.
const pulldownSeat = [{ kind: 'pad', from: [-26, SEAT], to: [10, SEAT], legs: [-8] }, { kind: 'roller', on: 'thigh' }];
const closePulldown = { hip: [-4, -58], lean: -18, head: -14, ankle: [34, -7.4] };
const closeTop = reachAbove(shoulder(closePulldown), shoulder(closePulldown)[0] + 5, 60);
const oneArmPulldown = { hip: [-4, -58], lean: -12, head: -12, ankle: [34, -7.4] };
const oneArmTop = reachAbove(shoulder(oneArmPulldown), shoulder(oneArmPulldown)[0] + 6, 60);
// The assisted machine for pull-ups: its handles overhead, the knee pad's pivot on the column.
// Rack pulls start with the bar on a power rack's pins just above the knees.
const RACK_BAR = [16, -58];
const rackPull = { hip: hipBelow([RACK_BAR[0] + 1, RACK_BAR[1] - 6.5 - 61], 46), lean: 46, head: 26 };
const rackPins = [{ kind: 'post', from: [44, -2], to: [44, -204], width: 6 }, { kind: 'post', from: [44, -54], to: [-6, -54], width: 4 }];
// Pendlay rows start each rep from the floor with the back about level.
const pendlay = { hip: hipBelow([9, -23.5 - 61], 84), lean: 84, head: 50 };
// Seal rows lie face down on a bench high enough for the bar to hang clear of the floor.
const SEAL_BENCH = -94;
const seal = { hip: [0, SEAL_BENCH - 12.5], lean: 90, head: 70, thigh: 266, shin: 268, foot: 80 };
// One-arm cable rows standing, the working side's foot back.
const splitRow = { hip: [-6, -96], lean: 16, head: 6, ankle: [-30, -9], foot: 22, farAnkle: [22, -7.4] };
// Inverted rows: a straight body from the heels, face up, under a bar that meets the chest at the top.
const INVERTED_HEELS = [40, -9.5];
const invertedBar = on(straightBody(INVERTED_HEELS, -54), 37, 16.5);
// A farmer's carry walks: one stride and the next, the back foot on its toes.
const carryStride = { hip: [0, -99], lean: 3, head: 0, arm: { upper: 2, bend: 4 } };
const FRONT_FOOT = [16, -7.4], BACK_FOOT = [-18, -11];
const assistedPullUps = [{ kind: 'post', from: [-96, -2], to: [-96, -236], width: 7 }, { kind: 'post', from: [-96, -228], to: [6, -228], width: 3 },
  { kind: 'bar', at: [6, -228] }, { kind: 'pad', on: 'shins', anchor: [-96, -79] }];

// The front view: the figure facing the viewer. Standing hip height puts the feet on the floor.
const fstand = { hip: [0, -100.2] };
const frontBody = p => M.frontFigure(M.frontPose({ arm: { upper: 0 }, ...p }));
// A side plank pivots on the bottom elbow: given the pelvis's turn and the side bend, the hip
// that puts the bottom shoulder at `s`.
const plankHip = (p, s) => {
  const F = frontBody({ ...p, hip: [0, 0] }), sh = F.torso(48, -19.5);
  return [r1(s[0] - sh.x), r1(s[1] - sh.y)];
};
// The side bend that brings the bottom shoulder to height `y` with the pelvis on the floor at `hipY`.
const plankLean = (turn, hipY, y) => {
  let lo = -10, hi = 60;
  for (let i = 0; i < 60; i++) {
    const mid = (lo + hi) / 2, sh = frontBody({ hip: [0, hipY], turn, lean: mid }).torso(48, -19.5);
    if (sh.y > y) lo = mid; else hi = mid;
  }
  return r1((lo + hi) / 2);
};
const PLANK_SHOULDER = [-56, -40.5];
const sagTurn = -87, sagHipY = -17, sagLean = plankLean(sagTurn, sagHipY, PLANK_SHOULDER[1]);
const sagHip = plankHip({ turn: sagTurn, lean: sagLean }, PLANK_SHOULDER);
const plankHipStraight = plankHip({ turn: -71, lean: 0 }, PLANK_SHOULDER);

const motions = [
  // Biceps
  {
    key: 'dumbbell-curls', name: 'Curls', gear: [{ kind: 'dumbbells' }],
    timing: [0.1, 0.42, 0.52, 0.95], shift: -6,
    from: { ...stand, arm: { upper: 3, bend: 6 } },
    to: { ...stand, arm: { upper: 14, bend: 138 } },
  },

  // Triceps
  {
    key: 'tricep-pushdowns', name: 'Tricep Push Down', gear: [{ kind: 'cable', from: [42, -196], handle: 'rope', stack: true }],
    timing: [0.1, 0.45, 0.55, 0.95], shift: -16,
    from: { ...stand, hip: [-2, -99.4], lean: 8, head: 4, arm: { upper: 4, bend: 100 } },
    to: { ...stand, hip: [-2, -99.4], lean: 8, head: 4, arm: { upper: 2, bend: 6 } },
  },
  {
    key: 'dumbbell-overhead-tricep-extensions', name: 'Overhead Extensions', gear: [{ kind: 'dumbbells', hang: true, one: true }],
    timing: [0.1, 0.5, 0.58, 0.95], zoom: 0.84, shift: 2,
    from: { ...stand, arm: { upper: 172, bend: 4 } },
    to: { ...stand, arm: { upper: 172, bend: 128 } },
  },

  // Shoulders
  {
    key: 'dumbbell-shoulder-press', name: 'Shoulder Press', gear: [{ kind: 'dumbbells' }],
    timing: [0.1, 0.45, 0.55, 0.95], zoom: 0.84, shift: -4,
    from: { ...stand, arm: { upper: 62, bend: 118, upperLength: 0.45 } },
    to: { ...stand, arm: { upper: 172, bend: 6, upperLength: 1 } },
  },
  {
    key: 'face-pulls', name: 'Face Pulls', gear: [{ kind: 'cable', from: [100, -152], handle: 'rope', stack: true }],
    timing: [0.1, 0.45, 0.55, 0.95], shift: -34,
    from: { ...stand, lean: -3, head: -2, arm: { hand: [58, -151], elbow: -1 } },
    to: { ...stand, lean: -3, head: -2, arm: { hand: [15, -159], scale: 0.9, upperLength: 0.6, elbow: -1 } },
  },
  {
    key: 'barbell-upright-rows', name: 'Upright Rows', gear: [{ kind: 'barbell' }],
    timing: [0.1, 0.45, 0.55, 0.95], shift: -4,
    from: { ...stand, arm: { hand: [9, -85.5], elbow: -1 } },
    to: { ...stand, arm: { hand: [12, -128], upperLength: 0.42, elbow: 1 } },
  },

  // Legs
  {
    key: 'barbell-back-squats', name: 'Squats', gear: [{ kind: 'barbell', on: 'back' }],
    timing: [0.08, 0.52, 0.58, 0.94], shift: 14,
    from: { hip: [-2, -99.6], lean: 4, head: 1.8, arm: { hand: [48.6, -13], on: 'torso', scale: 0.75, elbow: -1 } },
    to: { hip: [-35, -56], lean: 43, head: 19.4, arm: { hand: [46.4, -11.2], on: 'torso', scale: 0.75, elbow: -1 } },
  },
  {
    key: 'barbell-stiff-legged-deadlift', name: 'Stiff Legged Deadlift', gear: [{ kind: 'barbell' }],
    timing: [0.08, 0.5, 0.58, 0.94], shift: 10,
    from: { hip: [-1, -98.8], lean: 3, head: 1, arm: { hand: [9, -85.5], elbow: -1 } },
    to: { hip: [-33, -94], lean: 76, head: 48, arm: { hand: [13.5, -42], elbow: -1 } },
  },
  {
    key: 'dumbbell-lunges', name: 'Lunges', gear: [{ kind: 'dumbbells', profile: true }],
    timing: [0.08, 0.5, 0.58, 0.94], shift: 4,
    from: { hip: [-4, -95], lean: 2, head: 0, ankle: [24, -7.4], farAnkle: [-31.5, -16.4], farFoot: 35, arm: { upper: 0, bend: 4 } },
    to: { hip: [-4, -55], lean: 4, head: 0, ankle: [24, -7.4], farAnkle: [-31.5, -16.4], farFoot: 35, arm: { upper: 0, bend: 4 } },
  },

  // Biceps on benches
  {
    key: 'ez-bar-preacher-curls', name: 'Preacher Curls', timing: [0.1, 0.45, 0.55, 0.95], shift: -8,
    gear: [{ kind: 'pad', from: [-32, SEAT], to: [4, SEAT], legs: [-14] },
      { kind: 'pad', ...preacherArm, thick: 9, front: true, legs: [r1((preacherArm.from[0] + preacherArm.to[0]) / 2)] },
      { kind: 'ezbar' }],
    from: { ...preacher, arm: { upper: 48, bend: 12 } },
    to: { ...preacher, arm: { upper: 48, bend: 118 } },
  },
  {
    key: 'incline-dumbbell-curls', name: 'Incline Bicep Curls', timing: [0.1, 0.45, 0.55, 0.95], shift: 6,
    gear: [{ kind: 'pad', from: [-16, SEAT], to: [22, SEAT], legs: [6] }, backPad(reclined, 70, 0),
      postDown(on(reclined, 32, -22)), { kind: 'dumbbells' }],
    from: { ...reclined, arm: { upper: 0, bend: 4 } },
    to: { ...reclined, arm: { upper: 0, bend: 122 } },
  },

  // Triceps and chest on a flat bench
  {
    key: 'ez-bar-skull-crushers', name: 'Skull-Crushers', timing: [0.1, 0.5, 0.58, 0.95], shift: 14,
    gear: [flatBench, { kind: 'ezbar' }],
    from: { ...lie, arm: { upper: 190, bend: 0 } },
    to: { ...lie, arm: { upper: 190, bend: 115 } },
  },
  {
    key: 'barbell-bench-press', name: 'Bench Press', timing: [0.1, 0.5, 0.58, 0.95], shift: 14,
    gear: [flatBench, { kind: 'barbell' }],
    from: { ...lie, arm: { hand: add(lieShoulder, [3, -61]), elbow: 1 } },
    to: { ...lie, arm: { hand: [-38, -69.5], elbow: 1 } },
  },
  {
    key: 'dumbbell-flyes', name: 'Chest Fly', timing: [0.1, 0.5, 0.58, 0.95], shift: 14,
    gear: [flatBench, { kind: 'dumbbells', profile: true }],
    from: { ...lie, arm: { hand: add(lieShoulder, [3, -59]), elbow: 1 } },
    to: { ...lie, arm: { hand: add(lieShoulder, [4, 9]), scale: 0.42, elbow: 1 } },
  },
  {
    key: 'barbell-incline-bench-press', name: 'Incline Bench', timing: [0.1, 0.5, 0.58, 0.95], shift: 4,
    gear: [{ kind: 'pad', from: [-16, SEAT], to: [22, SEAT], legs: [8] }, backPad(incline, 74, 2),
      postDown(on(incline, 36, -22)), { kind: 'barbell' }],
    from: { ...incline, arm: { hand: add(inclineShoulder, [2, -61]), elbow: 1 } },
    to: { ...incline, arm: { hand: add(inclineChest, [0, 6.5]), elbow: 1 } },
  },
  {
    key: 'pec-deck', name: 'Pec Dec', timing: [0.1, 0.45, 0.55, 0.95], shift: 0,
    gear: [{ kind: 'pad', from: [-24, -50], to: [12, -50], legs: [-6] }, backPad(seated, 66, 4),
      { kind: 'handle' }],
    from: { ...seated, arm: { hand: [-15, -110], scale: 0.4, elbow: 1 } },
    to: { ...seated, arm: { hand: [40, -112], elbow: 1 } },
  },

  // Core
  {
    key: 'lying-leg-raises', name: 'Leg Raises', timing: [0.1, 0.48, 0.56, 0.95], shift: 16,
    shadow: [-12, 0], shadowWidth: 86,
    from: { hip: [0, -14], lean: -90, thigh: 83, shin: 83, foot: -97, arm: { upper: 90, bend: 0 } },
    to: { hip: [0, -14], lean: -90, thigh: 0, shin: 0, foot: -180, arm: { upper: 90, bend: 0 } },
  },

  // Back
  {
    key: 'lat-pulldowns', name: 'Lat Pull-down', timing: [0.1, 0.45, 0.55, 0.95], zoom: 0.94, shift: 4,
    gear: [{ kind: 'pad', from: [-26, SEAT], to: [10, SEAT], legs: [-8] }, { kind: 'roller', on: 'thigh' },
      { kind: 'cable', from: [-6, -212], handle: 'bar' }],
    from: { ...pulldown, arm: { hand: [-8, -165], scale: 0.9, elbow: 1 } },
    to: { ...pulldown, arm: { hand: add(on(pulldown, 44, 17), [0, 6.5]), scale: 0.85, elbow: 1 } },
  },
  {
    key: 'pull-ups', name: 'Pull-ups', timing: [0.1, 0.45, 0.55, 0.95], zoom: 0.78, shift: 0,
    gear: [{ kind: 'post', from: [-34, -2], to: [-34, -236], width: 5 }, { kind: 'post', from: [-34, -228], to: [6, -228], width: 3 },
      { kind: 'bar', at: [6, -228] }],
    from: { hip: [11.5, -117.3], lean: -6, head: -4, ...hangLegs, arm: { hand: [6, -221.5], scale: 0.9, elbow: 1 } },
    to: { hip: [7, -168], lean: -12, head: -18, ...hangLegs, arm: { hand: [6, -221.5], scale: 0.9, elbow: 1 } },
  },
  {
    key: 'lat-prayers', name: 'Lat Prayers', timing: [0.1, 0.48, 0.56, 0.95], shift: -18,
    gear: [{ kind: 'cable', from: [64, -196], handle: 'rope', stack: true }],
    from: { ...kneel, lean: 2, head: 0, arm: { upper: 162, bend: 6 } },
    to: { ...kneel, lean: 30, curl: 18, head: 62, arm: { upper: 95, bend: 25 } },
  },
  {
    key: 'chest-supported-dumbbell-rows', name: 'Chest-Supported Rows', timing: [0.1, 0.45, 0.55, 0.95], shift: 14,
    gear: [frontPad(prone, -4, 62), postDown(on(prone, 20, 22)), { kind: 'dumbbells', profile: true }],
    from: { ...prone, arm: { hand: add(proneShoulder, [0, 61.5]), elbow: -1 } },
    to: { ...prone, arm: { hand: on(prone, 22, 6), elbow: -1 } },
  },
  {
    key: 'barbell-bent-over-rows', name: 'Bent-over Rows', gear: [{ kind: 'barbell' }],
    timing: [0.08, 0.45, 0.55, 0.95], shift: 8,
    from: { hip: [-30, -88], lean: 72, head: 40, arm: { hand: [15.5, -41], elbow: -1 } },
    to: { hip: [-30, -88], lean: 72, head: 40, arm: { hand: [2, -86.5], elbow: -1 } },
  },


  // Standing
  {
    key: 'barbell-curls', name: 'Barbell Curls', gear: [{ kind: 'barbell', radius: 15 }],
    timing: [0.1, 0.42, 0.52, 0.95], shift: -8,
    from: { ...stand, arm: { upper: 3, bend: 6 } },
    to: { ...stand, arm: { upper: 14, bend: 125 } },
  },
  {
    key: 'hammer-curls', name: 'Hammer Curls', gear: [{ kind: 'dumbbells', profile: true }],
    timing: [0.1, 0.42, 0.52, 0.95], shift: -6,
    from: { ...stand, arm: { upper: 3, bend: 6 } },
    to: { ...stand, arm: { upper: 12, bend: 132 } },
  },
  {
    key: 'cable-curls', name: 'Cable Curls', timing: [0.1, 0.42, 0.52, 0.95], shift: -18,
    gear: [{ kind: 'post', from: [50, -2], to: [50, -196], width: 7 }, { kind: 'cable', from: [50, -18], handle: 'bar' }],
    from: { ...stand, lean: -1, arm: { upper: 4, bend: 8 } },
    to: { ...stand, lean: -1, arm: { upper: 12, bend: 128 } },
  },
  {
    key: 'behind-the-back-cable-curls', name: 'Behind-the-Back Bicep Curls', timing: [0.1, 0.42, 0.52, 0.95], shift: 16,
    gear: [{ kind: 'post', from: [-62, -2], to: [-62, -196], width: 7 }, { kind: 'cable', from: [-62, -20], handle: 'single' }],
    from: { ...stand, arm: { upper: -16, bend: 4 }, farArm: { upper: 2, bend: 6 } },
    to: { ...stand, arm: { upper: -16, bend: 118 }, farArm: { upper: 2, bend: 6 } },
  },
  {
    key: 'dumbbell-front-raises', name: 'Front Raises', gear: [{ kind: 'dumbbells' }],
    timing: [0.1, 0.45, 0.55, 0.95], shift: -16,
    from: { ...stand, arm: { upper: 8, bend: 6 } },
    to: { ...stand, arm: { upper: 88, bend: 8 } },
  },
  {
    key: 'barbell-overhead-press', name: 'Overhead Press', gear: [{ kind: 'barbell', radius: 15 }],
    timing: [0.1, 0.48, 0.56, 0.95], zoom: 0.84, shift: -8,
    from: { ...stand, head: -6, arm: { upper: 50, bend: 120 } },
    to: { ...stand, arm: { upper: 176, bend: 2 } },
  },
  {
    key: 'dumbbell-tricep-kickbacks', name: 'Tricep Kickbacks', gear: [{ kind: 'dumbbells', profile: true }],
    timing: [0.1, 0.42, 0.52, 0.95], shift: 10,
    from: { hip: [-24, -93], lean: 64, head: 40, arm: { upper: -70, bend: 70 } },
    to: { hip: [-24, -93], lean: 64, head: 40, arm: { upper: -70, bend: 2 } },
  },
  {
    key: 'straight-arm-pulldowns', name: 'Straight-Arm Pulldowns', timing: [0.1, 0.45, 0.55, 0.95], shift: -28,
    gear: [{ kind: 'cable', from: [96, -206], handle: 'bar', stack: true }],
    from: { hip: [-6, -98.6], lean: 14, head: 6, arm: { upper: 118, bend: 6 } },
    to: { hip: [-6, -98.6], lean: 14, head: 6, arm: { upper: 14, bend: 6 } },
  },
  {
    key: 'cable-glute-kickbacks', name: 'Cable Kickbacks', timing: [0.1, 0.45, 0.55, 0.95], shift: 4,
    gear: [{ kind: 'post', from: [50, -2], to: [50, -196], width: 7 }, { kind: 'cable', from: [50, -14], on: 'ankle', handle: 'single' }],
    from: { hip: [0, -100.6], lean: 14, head: 6, thigh: 182, shin: 184, foot: 4, farThigh: 180, farShin: 180,
      arm: { hand: [42, -128], elbow: 1 } },
    to: { hip: [0, -100.6], lean: 18, head: 8, thigh: 222, shin: 236, foot: 30, farThigh: 180, farShin: 180,
      arm: { hand: [42, -128], elbow: 1 } },
  },

  // Hinges and pulls from the floor
  {
    key: 'barbell-deadlift', name: 'Deadlift', gear: [{ kind: 'barbell' }],
    timing: [0.1, 0.48, 0.58, 0.95], shift: 6,
    from: { hip: [-36, -59], lean: 56, head: 30, arm: { hand: [8, -23.5], elbow: -1 } },
    to: { hip: [-1, -98.8], lean: 2, head: 0, arm: { hand: [9, -91.5], elbow: -1 } },
  },
  {
    key: 'barbell-romanian-deadlift', name: 'Romanian Deadlift', gear: [{ kind: 'barbell' }],
    timing: [0.08, 0.5, 0.58, 0.94], shift: 10,
    from: { hip: [-1, -98], lean: 3, head: 1, arm: { hand: [9, -85.5], elbow: -1 } },
    to: { hip: [-30, -86], lean: 72, head: 46, arm: { hand: [13, -40], elbow: -1 } },
  },
  {
    key: 'good-mornings', name: 'Good Mornings', gear: [{ kind: 'barbell', on: 'back' }],
    timing: [0.08, 0.5, 0.58, 0.94], shift: 12,
    from: { hip: [-2, -99.2], lean: 3, head: 1, arm: { hand: [48.6, -13], on: 'torso', scale: 0.75, elbow: -1 } },
    to: { hip: [-24, -95], lean: 76, head: 50, arm: { hand: [48.6, -13], on: 'torso', scale: 0.75, elbow: -1 } },
  },
  {
    key: 't-bar-rows', name: 'T-Bar Rows', gear: [{ kind: 'tbar', anchor: [-96, -4] }],
    timing: [0.08, 0.45, 0.55, 0.95], shift: 16,
    from: { hip: [-30, -86], lean: 64, head: 36, arm: { hand: [15, -46], elbow: -1 } },
    to: { hip: [-30, -86], lean: 64, head: 36, arm: { hand: [8, -70], elbow: -1 } },
  },

  // Squats and lunges
  {
    key: 'hack-squats', name: 'Hack Squats', timing: [0.08, 0.52, 0.58, 0.94], shift: 14,
    gear: [{ kind: 'post', from: [-22, -33], to: [-102, -135], width: 6 }, { kind: 'pad', on: 'back', top: 72, bottom: -4 },
      { kind: 'pad', ...sole(hackTop), thick: 6, legs: [8] }],
    from: { ...hackTop, arm: { hand: [47, 7], on: 'torso', scale: 0.8, elbow: -1 } },
    to: { ...hackTop, hip: [-12, -60], arm: { hand: [47, 7], on: 'torso', scale: 0.8, elbow: -1 } },
  },
  {
    key: 'smith-machine-squats', name: 'Smith-Squats', gear: [{ kind: 'post', from: [-31, -2], to: [-31, -204], width: 6 }, { kind: 'barbell', on: 'back' }],
    timing: [0.08, 0.52, 0.58, 0.94], shift: 10,
    from: { hip: [-16, -98.8], lean: 4, head: 2, arm: { hand: [48.6, -13], on: 'torso', scale: 0.75, elbow: -1 } },
    to: { hip: [-42, -58], lean: 32, head: 16, arm: { hand: [46.4, -11.2], on: 'torso', scale: 0.75, elbow: -1 } },
  },
  {
    key: 'barbell-front-squats', name: 'Front Squats', gear: [{ kind: 'barbell', on: 'front' }],
    timing: [0.08, 0.52, 0.58, 0.94], shift: 10,
    from: { hip: [-2, -99.6], lean: 3, head: 2, arm: { hand: [53, 9], on: 'torso', scale: 0.85, elbow: -1 } },
    to: { hip: [-30, -52], lean: 18, head: 10, arm: { hand: [53, 9], on: 'torso', scale: 0.85, elbow: -1 } },
  },
  {
    key: 'goblet-squats', name: 'Goblet Squats', gear: [{ kind: 'dumbbells', on: 'chest' }],
    timing: [0.08, 0.52, 0.58, 0.94], shift: 8,
    from: { hip: [-2, -99.6], lean: 3, head: 2, arm: { hand: [38, 20], on: 'torso', scale: 0.8, elbow: -1 } },
    to: { hip: [-32, -50], lean: 25, head: 12, arm: { hand: [38, 20], on: 'torso', scale: 0.8, elbow: -1 } },
  },
  {
    key: 'dumbbell-bulgarian-split-squats', name: 'Bulgarian Split Squats', timing: [0.08, 0.5, 0.58, 0.94], shift: 18,
    gear: [{ kind: 'pad', from: [-96, -44], to: [-38, -44], legs: [-84, -50] }, { kind: 'dumbbells', profile: true }],
    from: { hip: [-12, -90], lean: 6, head: 2, ankle: [24, -7.4], farAnkle: [-48, -49], farFoot: 160, arm: { upper: 0, bend: 4 } },
    to: { hip: [-14, -56], lean: 10, head: 4, ankle: [24, -7.4], farAnkle: [-48, -49], farFoot: 160, arm: { upper: 0, bend: 4 } },
  },
  {
    key: 'wall-sit', name: 'Wall Sit', gear: [{ kind: 'post', from: [-37, -2], to: [-37, -196], width: 8 }],
    timing: [0.06, 0.3, 0.9, 0.98], seconds: 4,
    from: { hip: [-19, -84], lean: 0, head: 0, ankle: [26, -7.4], arm: { hand: [36, 10], on: 'torso', scale: 0.8, elbow: -1 } },
    to: { hip: [-19, -55], lean: 0, head: 0, ankle: [26, -7.4], arm: { hand: [36, 10], on: 'torso', scale: 0.8, elbow: -1 } },
  },
  {
    key: 'standing-calf-raises', name: 'Calf Raises', timing: [0.1, 0.45, 0.55, 0.92], shift: -8,
    gear: [{ kind: 'pad', from: [5, -10], to: [46, -10], thick: 10 }],
    from: { hip: add(calfLow, [-1.5, -92.6]), lean: 1.5, head: 0, ankle: calfLow, foot: -20, arm: { upper: 2, bend: 6 } },
    to: { hip: add(calfHigh, [-1.5, -92.6]), lean: 1.5, head: 0, ankle: calfHigh, foot: 28, arm: { upper: 2, bend: 6 } },
  },

  // On benches
  {
    key: 'barbell-hip-thrusts', name: 'Hip Thrusts', timing: [0.1, 0.45, 0.55, 0.95], shift: 18,
    gear: [{ kind: 'pad', from: [-118, -47], to: [-44, -47], legs: [-104, -60] }, { kind: 'barbell', on: 'hips' }],
    from: { hip: pivotHip(-55, THRUST, 40, -12.5), lean: -55, head: -30, ankle: [36, -7.4], arm: { hand: [0, 13], on: 'torso', scale: 0.9, elbow: 1 } },
    to: { hip: pivotHip(-90, THRUST, 40, -12.5), lean: -90, head: -60, ankle: [36, -7.4], arm: { hand: [0, 13], on: 'torso', scale: 0.9, elbow: 1 } },
  },
  {
    key: 'dumbbell-bench-press', name: 'Dumbbell Bench Press', timing: [0.1, 0.5, 0.58, 0.95], shift: 14,
    gear: [flatBench, { kind: 'dumbbells' }],
    from: { ...lie, arm: { hand: add(lieShoulder, [3, -61]), elbow: 1 } },
    to: { ...lie, arm: { hand: [-40, -68], elbow: 1 } },
  },
  {
    key: 'close-grip-bench-press', name: 'Close-Grip Bench Press', timing: [0.1, 0.5, 0.58, 0.95], shift: 14,
    gear: [flatBench, { kind: 'barbell' }],
    from: { ...lie, arm: { hand: add(lieShoulder, [3, -61]), elbow: 1 } },
    to: { ...lie, arm: { hand: [-33, -68.5], elbow: 1 } },
  },
  {
    key: 'incline-dumbbell-press', name: 'Incline Dumbbell Press', timing: [0.1, 0.5, 0.58, 0.95], shift: 4,
    gear: [{ kind: 'pad', from: [-16, SEAT], to: [22, SEAT], legs: [8] }, backPad(incline, 74, 2),
      postDown(on(incline, 36, -22)), { kind: 'dumbbells' }],
    from: { ...incline, arm: { hand: add(inclineShoulder, [2, -61]), elbow: 1 } },
    to: { ...incline, arm: { hand: add(inclineChest, [-2, 2]), elbow: 1 } },
  },
  {
    key: 'barbell-decline-bench-press', name: 'Decline Bench Press', timing: [0.1, 0.5, 0.58, 0.95], shift: 6,
    gear: [{ ...backPad(decline, 100, -14), legs: [-60, 4] }, { kind: 'roller', at: [52, -20] }, { kind: 'barbell' }],
    from: { ...decline, arm: { hand: add(declineShoulder, [2, -60]), elbow: 1 } },
    to: { ...decline, arm: { hand: add(on(decline, 32, 17), [0, 6.5]), elbow: 1 } },
  },
  {
    key: 'lying-leg-curls', name: 'Lying Leg Curls', timing: [0.1, 0.45, 0.55, 0.95], shift: 8,
    shadow: [10, 0], shadowWidth: 70,
    gear: [{ kind: 'pad', from: [-38, -47], to: [84, -47], legs: [-24, 70] }, { kind: 'roller', on: 'calf' }],
    from: { ...lyingFaceDown, shin: 268, foot: 88, arm: { hand: [74, -50], elbow: 1 } },
    to: { ...lyingFaceDown, shin: 390, foot: 210, arm: { hand: [74, -50], elbow: 1 } },
  },
  {
    key: 'single-arm-dumbbell-rows', name: 'Single-Arm Dumbbell Rows', timing: [0.1, 0.45, 0.55, 0.95], shift: 26,
    gear: [{ kind: 'pad', from: [-92, -47], to: [32, -47], legs: [-78, 18] }, { kind: 'dumbbells', profile: true, one: true }],
    from: { ...oneArmRow, arm: { hand: [16, -52], elbow: -1 } },
    to: { ...oneArmRow, arm: { hand: [-12, -90], elbow: -1 } },
  },

  // Machines
  {
    key: 'leg-press', name: 'Leg Press', timing: [0.1, 0.5, 0.58, 0.95], shift: -4,
    gear: [{ kind: 'post', from: [-10, -20], to: [92, -140], width: 6 }, { kind: 'pad', on: 'back', top: 66, bottom: 2 },
      { kind: 'pad', from: [-44, -27], to: [-6, -32], legs: [-26] }, { kind: 'footplate', length: 44, thick: 7 }],
    from: { ...legPress, ankle: [12, -67], foot: -130, arm: { hand: [-6, -42], elbow: 1 } },
    to: { ...legPress, ankle: [47, -96.5], foot: -130, arm: { hand: [-6, -42], elbow: 1 } },
  },
  {
    key: 'leg-extensions', name: 'Leg Extensions', timing: [0.1, 0.45, 0.55, 0.95], shift: -10,
    gear: [{ kind: 'pad', from: [-30, -48], to: [42, -48], legs: [6] }, backPad(legExtension, 66, 6), { kind: 'roller', on: 'shin' }],
    from: { ...legExtension, shin: 180, foot: 0, arm: { hand: [-14, -52], elbow: 1 } },
    to: { ...legExtension, shin: 92, foot: -88, arm: { hand: [-14, -52], elbow: 1 } },
  },
  {
    key: 'seated-leg-curls', name: 'Seated Leg Curls', timing: [0.1, 0.45, 0.55, 0.95], shift: -30,
    gear: [{ kind: 'pad', from: [-32, -52], to: [36, -52], legs: [2] }, backPad(seatedLegCurl, 66, 6),
      { kind: 'roller', on: 'thigh' }, { kind: 'roller', on: 'calf' }],
    from: { ...seatedLegCurl, shin: 92, foot: -88, arm: { hand: [-12, -56], elbow: 1 } },
    to: { ...seatedLegCurl, shin: 196, foot: 16, arm: { hand: [-12, -56], elbow: 1 } },
  },
  {
    key: 'seated-calf-raises', name: 'Seated Calf Raises', timing: [0.1, 0.45, 0.55, 0.92], shift: 14,
    gear: [{ kind: 'pad', from: [-72, -58], to: [-18, -58], legs: [-46] }, { kind: 'pad', from: [4, -14], to: [40, -14], thick: 14 },
      { kind: 'roller', on: 'knee' }],
    from: { ...seatedCalf, ankle: seatedCalfLow, foot: -22, arm: { hand: kneePad({ ...seatedCalf, ankle: seatedCalfLow, foot: -22 }), elbow: 1 } },
    to: { ...seatedCalf, ankle: seatedCalfHigh, foot: 30, arm: { hand: kneePad({ ...seatedCalf, ankle: seatedCalfHigh, foot: 30 }), elbow: 1 } },
  },
  {
    key: 'machine-chest-press', name: 'Machine Chest Press', timing: [0.1, 0.45, 0.55, 0.95], shift: -10,
    gear: [{ kind: 'pad', from: [-24, -50], to: [12, -50], legs: [-6] }, backPad(chestPress, 66, 4), { kind: 'handle' }],
    from: { ...chestPress, arm: { hand: [14, -104], elbow: 1 } },
    to: { ...chestPress, arm: { hand: [50, -106], elbow: 1 } },
  },
  {
    key: 'seated-cable-rows', name: 'Seated Cable Rows', timing: [0.1, 0.45, 0.55, 0.95], shift: -22,
    gear: [{ kind: 'pad', from: [-74, -28], to: [16, -28], legs: [-60, 2] }, { kind: 'pad', from: [66, -14], to: [66, -62], thick: 6 },
      { kind: 'post', from: [80, -2], to: [80, -110], width: 7 }, { kind: 'cable', from: [80, -44], handle: 'bar' }],
    from: { ...rowSeat, lean: 10, arm: { hand: [46, -72], elbow: -1 } },
    to: { ...rowSeat, lean: -6, arm: { hand: on({ ...rowSeat, lean: -6 }, 16, 22), elbow: -1 } },
  },
  {
    key: 'concentration-curls', name: 'Concentration Curls', timing: [0.1, 0.42, 0.52, 0.95], shift: -4,
    gear: [{ kind: 'pad', from: [-40, -48], to: [10, -48], legs: [-16] }, { kind: 'dumbbells', one: true }],
    from: { ...concentration, arm: { upper: 6, bend: 4 }, farArm: { hand: [30, -66], elbow: 1 } },
    to: { ...concentration, arm: { upper: 6, bend: 134 }, farArm: { hand: [30, -66], elbow: 1 } },
  },

  // Floor and bars
  {
    key: 'sit-ups', name: 'Sit-ups', timing: [0.1, 0.48, 0.56, 0.95], shift: 6, shadow: [-6, 0], shadowWidth: 70,
    from: { ...floorLie, lean: -90, head: -88, arm: { hand: [38, 10], on: 'torso', scale: 0.8, elbow: -1 } },
    to: { ...floorLie, lean: -24, curl: 18, head: -2, arm: { hand: [38, 10], on: 'torso', scale: 0.8, elbow: -1 } },
  },
  {
    key: 'crunches', name: 'Crunches', timing: [0.1, 0.45, 0.55, 0.95], shift: 6, shadow: [-6, 0], shadowWidth: 70,
    from: { ...floorLie, lean: -90, head: -88, arm: { hand: [38, 10], on: 'torso', scale: 0.8, elbow: -1 } },
    to: { ...floorLie, lean: -90, curl: 34, head: -40, arm: { hand: [38, 10], on: 'torso', scale: 0.8, elbow: -1 } },
  },
  {
    key: 'glute-bridges', name: 'Glute Bridges', timing: [0.1, 0.45, 0.55, 0.95], shift: 10,
    shadow: [-6, 0], shadowWidth: 70,
    from: { hip: pivotHip(-90, BRIDGE, 42, -12.5), lean: -90, head: -92, ankle: [36, -7.4], arm: { upper: 90, bend: 0 } },
    to: { hip: pivotHip(-118, BRIDGE, 42, -12.5), lean: -118, head: -80, ankle: [36, -7.4], arm: { upper: 90, bend: 0 } },
  },
  {
    key: 'push-ups', name: 'Push-ups', timing: [0.1, 0.5, 0.58, 0.95], shift: -8, shadow: [10, 0], shadowWidth: 80,
    from: { hip: lineHip(PUSHUP_TOES, 72), lean: 72, thigh: 252, shin: 252, foot: 72, arm: { hand: [74, -5], elbow: 1 } },
    to: { hip: lineHip(PUSHUP_TOES, 88), lean: 88, thigh: 268, shin: 268, foot: 88, arm: { hand: [74, -5], elbow: 1 } },
  },
  {
    key: 'plank', name: 'Plank', timing: [0.06, 0.28, 0.9, 0.98], seconds: 4, shift: -2, shadow: [4, 0], shadowWidth: 82,
    from: { hip: [25.1, -23.7], lean: 68.5, thigh: 248.4, shin: 300, foot: 160, arm: { upper: 0, bend: 90 } },
    to: { hip: lineHip(PLANK_TOES, 82.1), lean: 82.1, thigh: 262.1, shin: 262.1, foot: 82.1, arm: { upper: 0, bend: 90 } },
  },
  {
    key: 'cable-crunches', name: 'Cable Crunches', timing: [0.1, 0.45, 0.55, 0.95], shift: -12,
    gear: [{ kind: 'cable', from: [60, -204], handle: 'rope', stack: true }],
    from: { ...kneel, lean: 12, head: 10, arm: { hand: [54, 10], on: 'torso', scale: 0.85, elbow: 1 } },
    to: { ...kneel, lean: 42, curl: 40, head: 90, arm: { hand: [54, 10], on: 'torso', scale: 0.85, elbow: 1 } },
  },
  {
    key: 'ab-wheel-rollouts', name: 'Ab Wheel Rollouts', timing: [0.1, 0.48, 0.56, 0.95], shift: -40, shadow: [40, 0], shadowWidth: 96,
    gear: [{ kind: 'wheel', radius: 8 }],
    from: { hip: [-21.9, -50.3], kneeAt: [-6, -6.6], lean: 60, head: 70, thigh: 160, shin: 270, foot: 166, arm: { hand: [25.5, -14.5], elbow: 1 } },
    to: { hip: [36.1, -26.3], kneeAt: [-6, -6.6], lean: 78, head: 84, thigh: 245, shin: 270, foot: 166, arm: { hand: [130, -10.6], elbow: 1 } },
  },
  {
    key: 'chin-ups', name: 'Chin-ups', timing: [0.1, 0.45, 0.55, 0.95], zoom: 0.78, shift: 0,
    gear: pullUpBar,
    from: { hip: [11.5, -117.3], lean: -6, head: -4, ...hangLegs, arm: { hand: [6, -221.5], scale: 0.9, elbow: 1 } },
    to: { hip: [7, -168], lean: -12, head: -18, ...hangLegs, arm: { hand: [6, -221.5], scale: 0.9, elbow: 1 } },
  },
  {
    key: 'hanging-leg-raises', name: 'Hanging Leg Raises', timing: [0.1, 0.48, 0.56, 0.95], zoom: 0.78, shift: -34,
    gear: pullUpBar,
    from: { hip: [11.5, -117.3], lean: -6, head: -4, thigh: 182, shin: 182, foot: 2, arm: { hand: [6, -221.5], scale: 0.9, elbow: 1 } },
    to: { hip: [6, -120.5], lean: -16, head: -8, thigh: 92, shin: 94, foot: -86, arm: { hand: [6, -221.5], scale: 0.9, elbow: 1 } },
  },
  {
    key: 'dips', name: 'Dips', timing: [0.1, 0.48, 0.56, 0.95], zoom: 0.86, shift: 14,
    gear: [{ kind: 'post', from: [2, -2], to: [2, -112], width: 5 }, { kind: 'bar', at: [2, -112] }],
    from: { hip: [-8.7, -128.5], lean: 8, head: 4, ...dipLegs, arm: { hand: [1, -118.5], elbow: -1 } },
    to: { hip: [-20.8, -100.3], lean: 18, head: 10, ...dipLegs, arm: { hand: [1, -118.5], elbow: -1 } },
  },

  // Front view
  {
    key: 'dumbbell-lateral-raises', name: 'Lateral Raises', view: 'front', gear: [{ kind: 'dumbbells' }],
    timing: [0.1, 0.45, 0.55, 0.95],
    from: { ...fstand, arm: { upper: 7, bend: 6 } },
    to: { ...fstand, arm: { upper: 86, bend: 12 } },
  },
  {
    key: 'cable-lateral-raises', name: 'Cable Lateral Raises', view: 'front', timing: [0.1, 0.45, 0.55, 0.95], shift: -10,
    gear: [{ kind: 'post', from: [-64, -2], to: [-64, -200], width: 7 }, { kind: 'cable', from: [-64, -18], side: 1 }],
    from: { ...fstand, arm: { hand: [-4, -101], elbow: 1 }, otherArm: { hand: [-55, -128], elbow: 1 } },
    to: { ...fstand, arm: { hand: [76, -150], elbow: 1 }, otherArm: { hand: [-55, -128], elbow: 1 } },
  },
  {
    key: 'dumbbell-shrugs', name: 'Shrugs', view: 'front', gear: [{ kind: 'dumbbells' }],
    timing: [0.1, 0.4, 0.55, 0.92],
    from: { ...fstand, arm: { upper: 5, bend: 2 } },
    to: { ...fstand, shrug: 9, arm: { upper: 5, bend: 2 } },
  },
  {
    key: 'arnold-press', name: 'Arnold Press', view: 'front', legsFront: true, gear: [{ kind: 'pad', from: [-24, -46], to: [24, -46], legs: [0] },
      { kind: 'dumbbells', profile: true }], timing: [0.1, 0.5, 0.58, 0.95],
    from: { hip: [0, -55], thigh: 90, thighLength: 0.36, shin: 2, arm: { upper: -26, bend: 206, upperLength: 0.58 } },
    to: { hip: [0, -55], thigh: 90, thighLength: 0.36, shin: 2, arm: { upper: 168, bend: 4 } },
  },
  {
    key: 'dumbbell-rear-delt-flyes', name: 'Rear Delt Flyes', view: 'front', gear: [{ kind: 'dumbbells' }],
    timing: [0.1, 0.45, 0.55, 0.95],
    from: { hip: [0, -98.7], torsoLength: 0.5, headHeight: -1.3, thighLength: 0.97, arm: { upper: 4, bend: 8 } },
    to: { hip: [0, -98.7], torsoLength: 0.5, headHeight: -1.3, thighLength: 0.97, arm: { upper: 84, bend: 14 } },
  },
  {
    key: 'cable-crossovers', name: 'Cable Crossovers', view: 'front', timing: [0.1, 0.45, 0.55, 0.95],
    gear: [{ kind: 'cable', from: [76, -206], side: 1, stack: true }, { kind: 'cable', from: [-76, -206], side: -1, stack: true }],
    from: { ...fstand, arm: { hand: [54, -170], elbow: -1 } },
    to: { ...fstand, arm: { hand: [6, -112], elbow: -1 } },
  },
  {
    key: 'machine-hip-abductions', name: 'Hip Abductions', view: 'front', legsFront: true, timing: [0.1, 0.45, 0.55, 0.95],
    gear: [{ kind: 'pad', from: [-24, -44], to: [24, -44], legs: [0] }, { kind: 'kneepads' }, { kind: 'handle' }],
    from: { hip: [0, -55], thigh: 90, thighLength: 0.17, shin: 1, arm: { hand: [30, -50], elbow: -1 } },
    to: { hip: [0, -55], thigh: 90, thighLength: 0.7, shin: 1, arm: { hand: [30, -50], elbow: -1 } },
  },
  {
    key: 'side-plank', name: 'Side Plank', view: 'front', timing: [0.08, 0.32, 0.88, 0.97], seconds: 4,
    shift: -6, shadow: [14, 0], shadowWidth: 92,
    from: { hip: sagHip, turn: sagTurn, lean: sagLean, thigh: -1, shin: 0,
      arm: { hand: [2, 15], on: 'torso', elbow: -1 }, otherArm: { upper: 0, bend: -90, foreLength: 0.3 } },
    to: { hip: plankHipStraight, turn: -71, lean: 0, thigh: -1, shin: 0,
      arm: { hand: [2, 15], on: 'torso', elbow: -1 }, otherArm: { upper: 0, bend: -90, foreLength: 0.3 } },
  },
  {
    key: 'russian-twists', name: 'Russian Twists', view: 'front', legsFront: true, gear: [{ kind: 'plate', radius: 10 }],
    timing: [0.08, 0.45, 0.55, 0.92], seconds: 2.6, zoom: 1.35, shadowWidth: 52, shadow: [0, 0],
    from: { hip: [0, -16], torsoLength: 0.8, lean: 8, thigh: 158, thighLength: 0.62, shin: 8, shinLength: 0.62,
      arm: { hand: [24, -30], elbow: 1 }, otherArm: { hand: [24, -30], elbow: 1 } },
    to: { hip: [0, -16], torsoLength: 0.8, lean: -8, thigh: 158, thighLength: 0.62, shin: 8, shinLength: 0.62,
      arm: { hand: [-24, -30], elbow: 1 }, otherArm: { hand: [-24, -30], elbow: 1 } },
  },

  // The library's chest exercises
  {
    key: 'decline-dumbbell-press', name: 'Decline Dumbbell Press', timing: [0.1, 0.5, 0.58, 0.95], shift: 6,
    gear: [{ ...backPad(decline, 100, -14), legs: [-60, 4] }, { kind: 'roller', at: [52, -20] }, { kind: 'dumbbells' }],
    from: { ...decline, arm: { hand: add(declineShoulder, [2, -60]), elbow: 1 } },
    to: { ...decline, arm: { hand: add(on(decline, 32, 17), [-2, 2]), elbow: 1 } },
  },
  {
    key: 'smith-machine-bench-press', name: 'Smith Machine Bench Press', timing: [0.1, 0.5, 0.58, 0.95], shift: 14,
    gear: [smithRail(-38), flatBench, { kind: 'barbell' }],
    from: { ...lie, arm: { hand: reachAbove(lieShoulder, -38), elbow: 1 } },
    to: { ...lie, arm: { hand: [-38, -69.5], elbow: 1 } },
  },
  {
    key: 'smith-machine-incline-press', name: 'Smith Machine Incline Press', timing: [0.1, 0.5, 0.58, 0.95], shift: 4,
    gear: [smithRail(inclineChest[0]), ...inclineBench, { kind: 'barbell' }],
    from: { ...incline, arm: { hand: reachAbove(inclineShoulder, inclineChest[0]), elbow: 1 } },
    to: { ...incline, arm: { hand: add(inclineChest, [0, 6.5]), elbow: 1 } },
  },
  {
    key: 'incline-machine-press', name: 'Incline Machine Press', timing: [0.1, 0.45, 0.55, 0.95], shift: -10,
    gear: [{ kind: 'pad', from: [-24, -50], to: [12, -50], legs: [-6] }, backPad(inclinePress, 66, 4), { kind: 'handle' }],
    from: { ...inclinePress, arm: { hand: add(shoulder(inclinePress), [18, -4]), elbow: 1 } },
    to: { ...inclinePress, arm: { hand: add(shoulder(inclinePress), [44, -40]), elbow: 1 } },
  },
  {
    key: 'incline-dumbbell-flyes', name: 'Incline Dumbbell Flyes', timing: [0.1, 0.5, 0.58, 0.95], shift: 4,
    gear: [...inclineBench, { kind: 'dumbbells', profile: true }],
    from: { ...incline, arm: { hand: add(inclineShoulder, [3, -57]), elbow: 1 } },
    to: { ...incline, arm: { hand: add(inclineShoulder, [3, 10]), scale: 0.42, elbow: 1 } },
  },
  {
    key: 'incline-push-ups', name: 'Incline Push-ups', timing: [0.1, 0.5, 0.58, 0.95], shift: -20, shadow: [10, 0], shadowWidth: 80,
    gear: [{ kind: 'pad', from: [56, BENCH], to: [112, BENCH], legs: [66, 102] }],
    from: { ...straightBody(PUSHUP_TOES, 51.2), arm: { hand: [63.4, -52], elbow: 1 } },
    to: { ...straightBody(PUSHUP_TOES, 67.6), arm: { hand: [63.4, -52], elbow: 1 } },
  },
  {
    key: 'decline-push-ups', name: 'Decline Push-ups', timing: [0.1, 0.5, 0.58, 0.95], shift: -8, shadow: [10, 0], shadowWidth: 80,
    gear: [{ kind: 'pad', from: [-112, BENCH], to: [-56, BENCH], legs: [-102, -66] }],
    from: { ...straightBody(DECLINE_TOES, 91.9), arm: { hand: [60, -5], elbow: 1 } },
    to: { ...straightBody(DECLINE_TOES, 107.3), arm: { hand: [60, -5], elbow: 1 } },
  },
  {
    key: 'chest-dips', name: 'Chest Dips', timing: [0.1, 0.48, 0.56, 0.95], zoom: 0.86, shift: 24,
    gear: [{ kind: 'post', from: [2, -2], to: [2, -112], width: 5 }, { kind: 'bar', at: [2, -112] }],
    from: { hip: [-24.1, -133.9], lean: 24, head: 14, ...chestDipLegs, arm: { hand: [1, -118.5], elbow: -1 } },
    to: { hip: [-54.7, -108.5], lean: 42, head: 26, ...chestDipLegs, arm: { hand: [1, -118.5], elbow: -1 } },
  },
  {
    key: 'low-to-high-cable-flyes', name: 'Low-to-High Cable Flyes', view: 'front', timing: [0.1, 0.45, 0.55, 0.95],
    gear: [{ kind: 'post', from: [76, -2], to: [76, -206], width: 7 }, { kind: 'post', from: [-76, -2], to: [-76, -206], width: 7 },
      { kind: 'cable', from: [76, -24], side: 1 }, { kind: 'cable', from: [-76, -24], side: -1 }],
    from: { ...fstand, arm: { hand: [52, -88], elbow: -1 } },
    to: { ...fstand, arm: { hand: [10, -136], elbow: -1 } },
  },

  // The library's shoulder exercises
  {
    key: 'seated-barbell-shoulder-press', name: 'Seated Barbell Shoulder Press', timing: [0.1, 0.48, 0.56, 0.95], zoom: 0.9, shift: -4,
    gear: [...pressSeat, { kind: 'barbell', radius: 15 }],
    from: { ...seatedPress, head: -6, arm: { upper: 50, bend: 120 } },
    to: { ...seatedPress, arm: { upper: 176, bend: 2 } },
  },
  {
    key: 'machine-shoulder-press', name: 'Machine Shoulder Press', timing: [0.1, 0.45, 0.55, 0.95], zoom: 0.9, shift: -4,
    gear: [...pressSeat, { kind: 'handle' }],
    from: { ...seatedPress, arm: { upper: 62, bend: 118, upperLength: 0.45 } },
    to: { ...seatedPress, arm: { upper: 172, bend: 6, upperLength: 1 } },
  },
  {
    key: 'smith-machine-shoulder-press', name: 'Smith Machine Shoulder Press', timing: [0.1, 0.48, 0.56, 0.95], zoom: 0.9, shift: -4,
    gear: [smithRail(24), ...pressSeat, { kind: 'barbell', radius: 15 }],
    from: { ...seatedPress, head: -6, arm: { hand: [24, -114], elbow: 1 } },
    to: { ...seatedPress, arm: { hand: reachAbove(shoulder(seatedPress), 24), elbow: 1 } },
  },
  {
    key: 'barbell-push-press', name: 'Push Press', gear: [{ kind: 'barbell', radius: 15 }],
    timing: [0.1, 0.4, 0.55, 0.95], zoom: 0.84, shift: -8,
    from: { hip: [-6, -91], lean: 3, head: -6, arm: { upper: 50, bend: 120 } },
    to: { ...stand, arm: { upper: 176, bend: 2 } },
  },
  {
    key: 'landmine-press', name: 'Landmine Press', gear: [{ kind: 'tbar', anchor: [118, -4], radius: 12 }],
    timing: [0.1, 0.45, 0.55, 0.95], zoom: 0.84, shift: -30,
    from: { ...landminePress, arm: { hand: [12.2, -143], elbow: 1 }, farArm: { upper: 4, bend: 8 } },
    to: { ...landminePress, arm: { hand: [55.3, -167], elbow: 1 }, farArm: { upper: 4, bend: 8 } },
  },
  {
    key: 'pike-push-ups', name: 'Pike Push-ups', timing: [0.1, 0.5, 0.58, 0.95], shift: -2, shadow: [0, 0], shadowWidth: 76,
    from: { ...pike(32, 127.7), arm: { hand: PIKE_HANDS, elbow: 1 } },
    to: { ...pike(46, 156), arm: { hand: PIKE_HANDS, elbow: 1 } },
  },
  {
    key: 'cable-front-raises', name: 'Cable Front Raises', timing: [0.1, 0.45, 0.55, 0.95], shift: 4,
    gear: [{ kind: 'post', from: [-54, -2], to: [-54, -196], width: 7 }, { kind: 'cable', from: [-54, -18], handle: 'bar' }],
    from: { ...stand, arm: { upper: 8, bend: 6 } },
    to: { ...stand, arm: { upper: 88, bend: 8 } },
  },
  {
    key: 'plate-front-raises', name: 'Plate Front Raises', gear: [{ kind: 'plate', radius: 12 }],
    timing: [0.1, 0.45, 0.55, 0.95], shift: -16,
    from: { ...stand, arm: { upper: 10, bend: 6 } },
    to: { ...stand, arm: { upper: 90, bend: 8 } },
  },
  {
    key: 'reverse-pec-deck', name: 'Reverse Pec Deck', timing: [0.1, 0.45, 0.55, 0.95], shift: -6,
    gear: [{ kind: 'pad', from: [-24, -50], to: [12, -50], legs: [-6] }, frontPad(facingPad, 18, 52), { kind: 'handle' }],
    from: { ...facingPad, arm: { hand: [40, -112], elbow: 1 } },
    to: { ...facingPad, arm: { hand: [-16, -114], scale: 0.4, elbow: 1 } },
  },
  {
    key: 'dumbbell-upright-rows', name: 'Dumbbell Upright Rows', gear: [{ kind: 'dumbbells' }],
    timing: [0.1, 0.45, 0.55, 0.95], shift: -4,
    from: { ...stand, arm: { hand: [9, -85.5], elbow: -1 } },
    to: { ...stand, arm: { hand: [12, -128], upperLength: 0.42, elbow: 1 } },
  },
  {
    key: 'cable-upright-rows', name: 'Cable Upright Rows', timing: [0.1, 0.45, 0.55, 0.95], shift: -20,
    gear: [{ kind: 'post', from: [50, -2], to: [50, -196], width: 7 }, { kind: 'cable', from: [50, -18], handle: 'bar' }],
    from: { ...stand, arm: { hand: [9, -85.5], elbow: -1 } },
    to: { ...stand, arm: { hand: [12, -128], upperLength: 0.42, elbow: 1 } },
  },
  {
    key: 'machine-lateral-raises', name: 'Machine Lateral Raises', view: 'front', legsFront: true, timing: [0.1, 0.45, 0.55, 0.95],
    gear: [{ kind: 'pad', from: [-24, -46], to: [24, -46], legs: [0] }, { kind: 'handle' }],
    from: { hip: [0, -55], thigh: 90, thighLength: 0.36, shin: 2, arm: { upper: 10, bend: 20 } },
    to: { hip: [0, -55], thigh: 90, thighLength: 0.36, shin: 2, arm: { upper: 88, bend: 20 } },
  },
  {
    key: 'cable-rear-delt-flyes', name: 'Cable Rear Delt Flyes', view: 'front', timing: [0.1, 0.45, 0.55, 0.95],
    gear: [{ kind: 'post', from: [76, -2], to: [76, -206], width: 7 }, { kind: 'post', from: [-76, -2], to: [-76, -206], width: 7 },
      { kind: 'cable', from: [76, -150], side: -1 }, { kind: 'cable', from: [-76, -150], side: 1 }],
    from: { ...fstand, arm: { hand: [-8, -146], elbow: 1, upperLength: 0.5, foreLength: 0.5 } },
    to: { ...fstand, arm: { hand: [80, -150], elbow: 1 } },
  },
  {
    key: 'band-pull-aparts', name: 'Band Pull-Aparts', view: 'front', timing: [0.1, 0.45, 0.55, 0.95],
    gear: [{ kind: 'band' }],
    from: { ...fstand, arm: { hand: [12, -142], elbow: 1, upperLength: 0.45, foreLength: 0.45 } },
    to: { ...fstand, arm: { hand: [80, -150], elbow: 1 } },
  },

  // The library's biceps and forearm exercises
  {
    key: 'ez-bar-curls', name: 'EZ-Bar Curls', gear: [{ kind: 'ezbar' }],
    timing: [0.1, 0.42, 0.52, 0.95], shift: -8,
    from: { ...stand, arm: { upper: 3, bend: 6 } },
    to: { ...stand, arm: { upper: 14, bend: 125 } },
  },
  {
    key: 'dumbbell-preacher-curls', name: 'Dumbbell Preacher Curls', timing: [0.1, 0.45, 0.55, 0.95], shift: -8,
    gear: [...preacherBench, { kind: 'dumbbells' }],
    from: { ...preacher, arm: { upper: 48, bend: 12 } },
    to: { ...preacher, arm: { upper: 48, bend: 118 } },
  },
  {
    key: 'machine-preacher-curls', name: 'Machine Preacher Curls', timing: [0.1, 0.45, 0.55, 0.95], shift: -8,
    gear: [...preacherBench, { kind: 'handle' }],
    from: { ...preacher, arm: { upper: 48, bend: 12 } },
    to: { ...preacher, arm: { upper: 48, bend: 118 } },
  },
  {
    key: 'dumbbell-spider-curls', name: 'Spider Curls', timing: [0.1, 0.42, 0.52, 0.95], shift: 14,
    gear: [frontPad(prone, -4, 44), postDown(on(prone, 20, 22)), { kind: 'dumbbells' }],
    from: { ...prone, arm: { upper: 0, bend: 6 } },
    to: { ...prone, arm: { upper: 6, bend: 130 } },
  },
  {
    key: 'dumbbell-zottman-curls', name: 'Zottman Curls', gear: [{ kind: 'dumbbells' }],
    timing: [0.1, 0.42, 0.52, 0.95], shift: -6,
    from: { ...stand, arm: { upper: 3, bend: 6 } },
    to: { ...stand, arm: { upper: 14, bend: 136 } },
  },
  {
    key: 'cable-hammer-curls', name: 'Cable Hammer Curls', timing: [0.1, 0.42, 0.52, 0.95], shift: -18,
    gear: [{ kind: 'post', from: [50, -2], to: [50, -196], width: 7 }, { kind: 'cable', from: [50, -18], handle: 'rope' }],
    from: { ...stand, lean: -1, arm: { upper: 3, bend: 6 } },
    to: { ...stand, lean: -1, arm: { upper: 12, bend: 132 } },
  },
  {
    key: 'barbell-reverse-curls', name: 'Reverse Curls', gear: [{ kind: 'barbell', radius: 15 }],
    timing: [0.1, 0.42, 0.52, 0.95], shift: -8,
    from: { ...stand, arm: { upper: 3, bend: 6 } },
    to: { ...stand, arm: { upper: 12, bend: 118 } },
  },
  {
    key: 'dumbbell-wrist-curls', name: 'Wrist Curls', timing: [0.1, 0.42, 0.52, 0.95], shift: -10,
    gear: [{ kind: 'pad', from: [-40, -48], to: [10, -48], legs: [-16] }, { kind: 'dumbbells' }],
    from: { ...wristCurl, arm: { hand: WRIST_ON_KNEE, elbow: 1, flex: -50 } },
    to: { ...wristCurl, arm: { hand: WRIST_ON_KNEE, elbow: 1, flex: 45 } },
  },
  {
    key: 'dumbbell-reverse-wrist-curls', name: 'Reverse Wrist Curls', timing: [0.1, 0.42, 0.52, 0.95], shift: -10,
    gear: [{ kind: 'pad', from: [-40, -48], to: [10, -48], legs: [-16] }, { kind: 'dumbbells' }],
    from: { ...wristCurl, arm: { hand: WRIST_ON_KNEE, elbow: 1, flex: -45 } },
    to: { ...wristCurl, arm: { hand: WRIST_ON_KNEE, elbow: 1, flex: 35 } },
  },

  // The library's triceps exercises
  {
    key: 'straight-bar-pushdowns', name: 'Straight-Bar Pushdowns', gear: [{ kind: 'cable', from: [42, -196], handle: 'bar', stack: true }],
    timing: [0.1, 0.45, 0.55, 0.95], shift: -16,
    from: { ...stand, hip: [-2, -99.4], lean: 8, head: 4, arm: { upper: 4, bend: 100 } },
    to: { ...stand, hip: [-2, -99.4], lean: 8, head: 4, arm: { upper: 2, bend: 6 } },
  },
  {
    key: 'cable-overhead-tricep-extensions', name: 'Cable Overhead Extensions', timing: [0.1, 0.5, 0.58, 0.95], zoom: 0.86, shift: 16,
    gear: [{ kind: 'cable', from: [-72, -208], handle: 'rope', stack: true }],
    from: { ...splitLean, arm: { upper: 140, bend: 122 } },
    to: { ...splitLean, arm: { upper: 140, bend: 4 } },
  },
  {
    key: 'ez-bar-overhead-tricep-extensions', name: 'EZ-Bar Overhead Extensions', gear: [{ kind: 'ezbar', radius: 11 }],
    timing: [0.1, 0.5, 0.58, 0.95], zoom: 0.84, shift: 2,
    from: { ...stand, arm: { upper: 172, bend: 4 } },
    to: { ...stand, arm: { upper: 172, bend: 128 } },
  },
  {
    key: 'dumbbell-skull-crushers', name: 'Dumbbell Skull-Crushers', timing: [0.1, 0.5, 0.58, 0.95], shift: 14,
    gear: [flatBench, { kind: 'dumbbells', profile: true }],
    from: { ...lie, arm: { upper: 190, bend: 0 } },
    to: { ...lie, arm: { upper: 190, bend: 120 } },
  },
  {
    key: 'cable-tricep-kickbacks', name: 'Cable Tricep Kickbacks', timing: [0.1, 0.42, 0.52, 0.95], shift: -14,
    gear: [{ kind: 'post', from: [64, -2], to: [64, -196], width: 7 }, { kind: 'cable', from: [64, -18], handle: 'single' }],
    from: { hip: [-24, -93], lean: 64, head: 40, arm: { upper: -70, bend: 70 }, farArm: { hand: [2, -68], elbow: 1 } },
    to: { hip: [-24, -93], lean: 64, head: 40, arm: { upper: -70, bend: 2 }, farArm: { hand: [2, -68], elbow: 1 } },
  },
  {
    key: 'bench-dips', name: 'Bench Dips', timing: [0.1, 0.48, 0.56, 0.95], shift: 6,
    gear: [{ kind: 'pad', from: [-96, BENCH], to: [-22, BENCH], legs: [-86, -32] }],
    from: { hip: hipBelow([-20, -112.5], -10), lean: -10, head: -6, ...benchDip, arm: { hand: BENCH_DIP_HANDS, elbow: 1 } },
    to: { hip: hipBelow([-2.7, -88], 4), lean: 4, head: 4, ...benchDip, arm: { hand: BENCH_DIP_HANDS, elbow: 1 } },
  },
  {
    key: 'assisted-dips', name: 'Assisted Dips', timing: [0.1, 0.48, 0.56, 0.95], zoom: 0.86, shift: 30,
    gear: assistedDips,
    from: { hip: [-8.7, -128.5], lean: 8, head: 4, ...kneelOnPad, arm: { hand: [1, -118.5], elbow: -1 } },
    to: { hip: [-20.8, -100.3], lean: 18, head: 10, ...kneelOnPad, arm: { hand: [1, -118.5], elbow: -1 } },
  },
  {
    key: 'diamond-push-ups', name: 'Diamond Push-ups', timing: [0.1, 0.5, 0.58, 0.95], shift: -8, shadow: [10, 0], shadowWidth: 80,
    from: { ...straightBody(PUSHUP_TOES, 71.5), arm: { hand: DIAMOND_HANDS, elbow: 1 } },
    to: { ...straightBody(PUSHUP_TOES, 86), arm: { hand: DIAMOND_HANDS, elbow: 1 } },
  },

  // The library's lats exercises
  {
    key: 'close-grip-lat-pulldowns', name: 'Close-Grip Lat Pull-down', timing: [0.1, 0.45, 0.55, 0.95], zoom: 0.94, shift: 4,
    gear: [...pulldownSeat, { kind: 'cable', from: [closeTop[0], -212], handle: 'bar' }],
    from: { ...closePulldown, arm: { hand: closeTop, elbow: 1 } },
    to: { ...closePulldown, arm: { hand: add(on(closePulldown, 38, 18), [0, 6.5]), elbow: 1 } },
  },
  {
    key: 'single-arm-lat-pulldowns', name: 'Single-Arm Lat Pull-down', timing: [0.1, 0.45, 0.55, 0.95], zoom: 0.94, shift: 4,
    gear: [...pulldownSeat, { kind: 'cable', from: [oneArmTop[0], -212], handle: 'single' }],
    from: { ...oneArmPulldown, arm: { hand: oneArmTop, elbow: 1 }, farArm: { hand: [24.5, -64], elbow: 1 } },
    to: { ...oneArmPulldown, arm: { hand: add(on(oneArmPulldown, 42, 15), [0, 6.5]), elbow: 1 }, farArm: { hand: [24.5, -64], elbow: 1 } },
  },
  {
    key: 'assisted-pull-ups', name: 'Assisted Pull-ups', timing: [0.1, 0.45, 0.55, 0.95], zoom: 0.78, shift: 30,
    gear: assistedPullUps,
    from: { hip: [11.5, -117.3], lean: -6, head: -4, ...kneelOnPad, arm: { hand: [6, -221.5], scale: 0.9, elbow: 1 } },
    to: { hip: [7, -168], lean: -12, head: -18, ...kneelOnPad, arm: { hand: [6, -221.5], scale: 0.9, elbow: 1 } },
  },
  {
    key: 'neutral-grip-pull-ups', name: 'Neutral-Grip Pull-ups', timing: [0.1, 0.45, 0.55, 0.95], zoom: 0.78, shift: 0,
    // Parallel handles run front to back, so from the side the hands hold a short bar along the view.
    gear: [{ kind: 'post', from: [-34, -2], to: [-34, -236], width: 5 }, { kind: 'post', from: [-34, -228], to: [-4, -228], width: 3 },
      { kind: 'post', from: [-4, -227.5], to: [16, -227.5], width: 4.5 }],
    from: { hip: [11.5, -117.3], lean: -6, head: -4, ...hangLegs, arm: { hand: [6, -221.5], scale: 0.9, elbow: 1 } },
    to: { hip: [7, -168], lean: -12, head: -18, ...hangLegs, arm: { hand: [6, -221.5], scale: 0.9, elbow: 1 } },
  },
  {
    key: 'dumbbell-pullovers', name: 'Dumbbell Pullovers', timing: [0.1, 0.5, 0.58, 0.95], shift: 26,
    gear: [flatBench, { kind: 'dumbbells', hang: true, one: true }],
    from: { ...lie, arm: { upper: 180, bend: 12 } },
    to: { ...lie, arm: { upper: 255, bend: 16 } },
  },

  // The library's back exercises
  {
    key: 'barbell-rack-pulls', name: 'Rack Pulls', timing: [0.1, 0.45, 0.55, 0.95], shift: 0,
    gear: [...rackPins, { kind: 'barbell' }],
    from: { ...rackPull, arm: { hand: [RACK_BAR[0], RACK_BAR[1] - 6.5], elbow: -1 } },
    to: { hip: [-1, -98.8], lean: 2, head: 0, arm: { hand: [9, -91.5], elbow: -1 } },
  },
  {
    key: 'dumbbell-bent-over-rows', name: 'Dumbbell Bent-over Rows', gear: [{ kind: 'dumbbells', profile: true }],
    timing: [0.08, 0.45, 0.55, 0.95], shift: 8,
    from: { hip: [-30, -88], lean: 72, head: 40, arm: { hand: [15.5, -41], elbow: -1 } },
    to: { hip: [-30, -88], lean: 72, head: 40, arm: { hand: [-1, -86], elbow: -1 } },
  },
  {
    key: 'barbell-pendlay-rows', name: 'Pendlay Rows', gear: [{ kind: 'barbell' }],
    timing: [0.12, 0.32, 0.42, 0.8], shift: 10,
    from: { ...pendlay, arm: { hand: [9, -23.5], elbow: -1 } },
    to: { ...pendlay, arm: { hand: on(pendlay, 30, 11.3), elbow: -1 } },
  },
  {
    key: 'barbell-seal-rows', name: 'Seal Rows', timing: [0.1, 0.45, 0.55, 0.95], shift: 14,
    gear: [{ kind: 'pad', from: [-104, SEAL_BENCH], to: [58, SEAL_BENCH], legs: [-90, 44] }, { kind: 'barbell' }],
    from: { ...seal, arm: { hand: [48, -46], elbow: -1 } },
    to: { ...seal, arm: { hand: [42, -88], elbow: -1 } },
  },
  {
    key: 'machine-rows', name: 'Machine Rows', timing: [0.1, 0.45, 0.55, 0.95], shift: -10,
    gear: [{ kind: 'pad', from: [-24, -50], to: [12, -50], legs: [-6] }, frontPad(facingPad, 18, 52), { kind: 'handle' }],
    from: { ...facingPad, arm: { hand: [58, -105], elbow: -1 } },
    to: { ...facingPad, arm: { hand: [8, -100], elbow: -1 } },
  },
  {
    key: 'single-arm-cable-rows', name: 'Single-Arm Cable Rows', timing: [0.1, 0.45, 0.55, 0.95], shift: -24,
    gear: [{ kind: 'post', from: [104, -2], to: [104, -196], width: 7 }, { kind: 'cable', from: [104, -124], handle: 'single' }],
    from: { ...splitRow, arm: { hand: [64, -126], elbow: -1 }, farArm: { upper: 8, bend: 20 } },
    to: { ...splitRow, arm: { hand: on(splitRow, 30, 14), elbow: -1 }, farArm: { upper: 8, bend: 20 } },
  },
  {
    key: 'inverted-rows', name: 'Inverted Rows', timing: [0.1, 0.45, 0.55, 0.95], shift: 26,
    gear: [smithRail(invertedBar[0]), { kind: 'bar', at: invertedBar }],
    from: { ...straightBody(INVERTED_HEELS, -74), arm: { hand: add(invertedBar, [0, 6.5]), elbow: 1 } },
    to: { ...straightBody(INVERTED_HEELS, -54), arm: { hand: add(invertedBar, [0, 6.5]), elbow: 1 } },
  },

  // The library's traps exercises
  {
    key: 'barbell-shrugs', name: 'Barbell Shrugs', gear: [{ kind: 'barbell' }],
    timing: [0.1, 0.4, 0.55, 0.92], shift: 4,
    from: { hip: [-1, -98.8], lean: 2, head: 0, arm: { hand: [9, -91.5], elbow: -1 } },
    to: { hip: [-1, -98.8], lean: 2, head: 0, shrug: 10, arm: { hand: [9.3, -101.5], elbow: -1 } },
  },
  {
    key: 'dumbbell-farmers-carry', name: "Farmer's Carry", gear: [{ kind: 'dumbbells', profile: true }],
    timing: [0, 0.5, 0.5, 1], seconds: 1.3, shift: 0,
    from: { ...carryStride, ankle: FRONT_FOOT, foot: 0, farAnkle: BACK_FOOT, farFoot: 26 },
    to: { ...carryStride, ankle: BACK_FOOT, foot: 26, farAnkle: FRONT_FOOT, farFoot: 0 },
  },
];

const out = process.argv[2] || require('path').join(__dirname, '../../Kitae/Exercises/Motion/ExerciseMotions.json');
fs.writeFileSync(out, JSON.stringify(motions, null, 1) + '\n');
console.log(motions.length, 'motions');
