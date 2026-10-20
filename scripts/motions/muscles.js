// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later
//
// Writes Kitae/Exercises/Muscles/ExerciseMuscles.json: the muscle map Kitae draws on an exercise's
// page (a body from the front and from the back, each muscle a shape on it) and, for every starter
// exercise, the muscles it works most (primary) and the ones that help (secondary).
//
//     node muscles.js [out.json]
//
// Shapes are written as points that a closed Catmull-Rom curve passes through, then turned into
// cubic Béziers, so the app only draws curves. The picture is 100 units wide and 222 tall, y down,
// facing the viewer; shapes are given for the right half of the picture and mirrored.

const fs = require('fs');
const path = require('path');

const W = 100, H = 222;

// Points ---------------------------------------------------------------------------------------

const mirror = (pts) => pts.map(([x, y]) => [W - x, y]).reverse();
const area = (pts) => pts.reduce((s, [x, y], i) => {
    const [x2, y2] = pts[(i + 1) % pts.length];
    return s + x * y2 - x2 * y;
}, 0) / 2;
// Every shape winds the same way (clockwise on screen), so overlapping parts of the body fill as one.
const clockwise = (pts) => (area(pts) < 0 ? pts.slice().reverse() : pts);

function ellipse(cx, cy, rx, ry, turn = 0, n = 14) {
    const a = (turn * Math.PI) / 180;
    return Array.from({ length: n }, (_, i) => {
        const t = (i / n) * Math.PI * 2;
        const x = Math.cos(t) * rx, y = Math.sin(t) * ry;
        return [cx + x * Math.cos(a) - y * Math.sin(a), cy + x * Math.sin(a) + y * Math.cos(a)];
    });
}

// A limb from a to b, its half-widths on each side ([t, left, right] along it) and round ends.
function limb(a, b, widths) {
    const dx = b[0] - a[0], dy = b[1] - a[1], len = Math.hypot(dx, dy);
    const ux = dx / len, uy = dy / len, lx = uy, ly = -ux; // left of the direction a → b
    const at = (t, side, r) => [a[0] + dx * t + lx * r * side, a[1] + dy * t + ly * r * side];
    const pts = [];
    for (const [t, l] of widths) pts.push(at(t, 1, l));
    const [, le, re] = widths[widths.length - 1], end = (le + re) / 2;
    for (const k of [0.5, 1, 1.5].map((x) => x / 2)) {
        const ang = Math.PI * k; // around the end, from the left side to the right
        pts.push([b[0] + lx * Math.cos(ang) * end + ux * Math.sin(ang) * end,
                  b[1] + ly * Math.cos(ang) * end + uy * Math.sin(ang) * end]);
    }
    for (const [t, , r] of widths.slice().reverse()) pts.push(at(t, -1, r));
    const [, ls, rs] = widths[0], start = (ls + rs) / 2;
    for (const k of [0.5, 1, 1.5].map((x) => x / 2)) {
        const ang = Math.PI * k;
        pts.push([a[0] - lx * Math.cos(ang) * start - ux * Math.sin(ang) * start,
                  a[1] - ly * Math.cos(ang) * start - uy * Math.sin(ang) * start]);
    }
    return pts;
}

// A closed Catmull-Rom curve through the points, as [x0, y0, then c1x, c1y, c2x, c2y, x, y per point].
function curve(input, k = 1) {
    const p = clockwise(input), n = p.length, out = [p[0][0], p[0][1]];
    for (let i = 0; i < n; i++) {
        const p0 = p[(i - 1 + n) % n], p1 = p[i], p2 = p[(i + 1) % n], p3 = p[(i + 2) % n];
        out.push(p1[0] + ((p2[0] - p0[0]) * k) / 6, p1[1] + ((p2[1] - p0[1]) * k) / 6,
                 p2[0] - ((p3[0] - p1[0]) * k) / 6, p2[1] - ((p3[1] - p1[1]) * k) / 6, p2[0], p2[1]);
    }
    return out.map((v) => Math.round(v * 100) / 100);
}

const pair = (pts) => [curve(pts), curve(mirror(pts))];
const pairs = (...shapes) => shapes.flatMap(pair);

// The body ---------------------------------------------------------------------------------------

const torso = [
    [50, 32.5], [56, 33], [63.5, 36.5], [70.5, 40], [74, 45], [72.6, 54], [70.4, 61], [68.8, 70],
    [67.2, 80], [66.4, 88], [67.6, 96], [69.4, 104], [69.4, 110], [62, 114.5], [54, 116.8], [50, 117.2],
];
const body = [
    curve(ellipse(50, 16.4, 9.2, 11.9)),
    curve([[45, 21], [55, 21], [56.4, 36], [43.6, 36]]),
    curve([...torso.slice(0, -1), ...mirror(torso.slice(1))]),
    ...pair(ellipse(73.4, 50, 6.6, 9.4, -14)),                                                // shoulder
    ...pair(limb([75.2, 51], [78.2, 87], [[0, 5.8, 5.8], [0.45, 6, 5.6], [1, 4.4, 4.2]])),   // upper arm
    ...pair(limb([78.2, 87], [80.6, 119], [[0, 4.4, 4.2], [0.28, 5.2, 4.8], [1, 3, 2.9]])),  // forearm
    ...pair(ellipse(81.2, 125.6, 3.5, 6.6, -6)),                                               // hand
    ...pair(limb([59.6, 106], [61.4, 160], [[0, 9.8, 10.4], [0.3, 9.2, 9.6], [0.75, 7, 6.6], [1, 5.6, 5.6]])),
    ...pair(limb([61.4, 161], [60.6, 202], [[0, 5.4, 5.6], [0.24, 6.4, 6.6], [0.62, 4.4, 4.6], [1, 2.8, 2.8]])),
    ...pair([[57.4, 202], [63.6, 202], [66.2, 211], [64.6, 215.6], [57.2, 215.8], [55.6, 211.6]]), // foot
];

// The muscles, from the front ------------------------------------------------------------------

const front = {
    traps: pairs([[56.2, 33.6], [62.5, 36.2], [69.2, 39.6], [66.8, 41.4], [59.4, 40], [55.8, 37.8]]),
    chest: pairs([[51.2, 42], [58, 39.8], [66, 41.2], [70.8, 46.4], [71.2, 52.6], [68, 59.4], [60.2, 62.4],
                  [53.2, 61.6], [51.2, 57]]),
    frontDelts: pairs([[70.4, 41.8], [73.6, 42.4], [75.2, 48], [75.6, 56], [75.8, 61.6], [73.2, 57.8],
                       [71, 52], [69.9, 46]]),
    sideDelts: pairs([[74.6, 42.4], [77.6, 44.6], [79.6, 49.6], [79.6, 55.4], [77.6, 60.8], [76.6, 61.6],
                      [76.2, 56], [75.8, 48]]),
    biceps: pairs([[73.8, 61.2], [76.6, 59.8], [79.2, 63], [80.2, 70.4], [80, 78.6], [78.6, 84.2],
                   [76.6, 83.8], [74.6, 77.6], [73.4, 68.8]]),
    forearms: pairs([[75.4, 90], [79.2, 88.6], [82.4, 92], [83.6, 99], [82.8, 107], [81.4, 113.6],
                     [79.6, 112.8], [77.6, 105], [76, 97]]),
    abs: pairs(
        [[51, 63.5], [56.4, 63], [57.8, 68], [57.6, 73.6], [51, 74.4]],
        [[51, 76.2], [57.6, 75.6], [57.8, 81.6], [57.4, 86], [51, 86.4]],
        [[51, 88.2], [57.4, 87.8], [57.2, 93.6], [56.6, 97.6], [51, 97.8]],
        [[51, 99.6], [56.4, 99.4], [55.6, 106.5], [53.6, 112.4], [51, 114.6]],
    ),
    obliques: pairs([[59.6, 63.6], [65.6, 61.6], [68, 70], [66.8, 80], [66, 88], [66.6, 94.6], [63, 99.6],
                     [59.6, 96], [59.4, 84], [60, 73]]),
    abductors: pairs([[66.8, 99.8], [68.8, 98.8], [69.6, 104], [69, 109.6], [66.8, 107]]),
    quads: pairs(
        [[60.6, 111.5], [64.2, 110.6], [66.2, 120], [66, 133], [64.5, 147], [62, 153.5], [59.6, 146],
         [58.6, 132], [59, 120]],
        [[66.4, 108.4], [68.8, 107.6], [69.6, 114], [68.8, 128], [67.6, 142], [66.2, 151.6], [64.8, 150],
         [66, 139], [66.6, 124]],
        [[57.4, 134.5], [59.4, 140.5], [61, 150.4], [59.6, 156], [56.6, 154], [55.2, 146.6], [55.9, 139]],
    ),
    adductors: pairs([[52.6, 115.4], [56.6, 113.8], [58.4, 120], [58, 130], [56, 136.2], [54, 131], [52.8, 123]]),
    calves: pairs(
        [[56.4, 165.2], [58.6, 164.4], [59.2, 173], [58.2, 182], [56.6, 184.4], [55.4, 176]],
        [[64.6, 165.2], [66.6, 168], [66.6, 178], [65.2, 184.2], [63.8, 176]],
    ),
};

// The muscles, from the back ---------------------------------------------------------------------

const back = {
    traps: pairs([[50.8, 27.4], [54.6, 28.6], [57, 34], [64, 37.6], [70.4, 41], [68.8, 44], [62, 46],
                  [57.4, 52], [55, 62], [52.6, 72], [50.8, 76.6]]),
    rearDelts: pairs([[70.4, 42.4], [73.8, 42.6], [75.8, 48], [76, 56], [75.8, 61.6], [73.4, 57.6],
                      [71, 51], [69.8, 46]]),
    sideDelts: front.sideDelts,
    upperBack: pairs([[57.6, 53.2], [62.4, 47.4], [68.8, 47.8], [70.6, 53.4], [68, 59.4], [62, 63],
                      [57.2, 62.6], [56, 58]]),
    lats: pairs([[70.2, 58.6], [68.6, 68], [67.2, 78], [66.2, 86], [63.4, 92.4], [59, 95.6], [57.2, 92],
                 [57, 84], [57.6, 74], [58.6, 66.6], [62.4, 64.6], [67.4, 60]]),
    lowerBack: pairs([[50.8, 78.6], [54.2, 77.6], [55.8, 86], [56, 94], [55.4, 101.4], [52.6, 103.4],
                      [50.8, 98.6]]),
    triceps: pairs([[73.6, 60.4], [77, 58.6], [80, 62.4], [80.6, 70], [80, 78], [78.4, 83.6], [76.2, 82.8],
                    [74.2, 75.6], [73.2, 67.6]]),
    forearms: front.forearms,
    abductors: pairs([[60.6, 94.8], [65.8, 93.2], [68.2, 96.4], [68.6, 101], [66, 100.6], [61.4, 98.4]]),
    glutes: pairs([[51, 101.6], [57, 99.4], [65, 101.2], [68.8, 106], [69, 114], [65.6, 120.6], [58.6, 123.6],
                   [52.6, 122], [51, 112]]),
    adductors: pairs([[52.6, 124.4], [55, 125.6], [54.8, 133], [53.2, 137.4], [52.2, 130]]),
    hamstrings: pairs(
        [[61.6, 126.8], [66.4, 125.2], [68, 134], [67, 144], [64.6, 151.6], [62.6, 148], [61.4, 138]],
        [[55.2, 127.2], [60, 127.6], [60.4, 137], [60, 146], [57.6, 152.4], [55.6, 145], [54.6, 136]],
    ),
    calves: pairs(
        [[55.8, 164.2], [60, 163.6], [60.6, 173], [59.6, 184], [57, 186.2], [55.2, 177]],
        [[61.6, 164.2], [65.6, 165.6], [66.6, 174], [64.6, 183.4], [62, 184.4], [61.2, 174]],
    ),
};

// What each starter exercise works: its name (checked against Seed.json), the muscles it works
// most, and the ones that help, by its library key (Seed.json's `key`, the database's
// `exercises.libraryKey`).

const EXERCISES = {
    'dumbbell-curls': ['Curls', 'biceps', 'forearms'],
    'barbell-curls': ['Barbell Curls', 'biceps', 'forearms'],
    'ez-bar-preacher-curls': ['Preacher Curls', 'biceps', 'forearms'],
    'incline-dumbbell-curls': ['Incline Bicep Curls', 'biceps', 'forearms'],
    'tricep-pushdowns': ['Tricep Push Down', 'triceps', ''],
    'ez-bar-skull-crushers': ['Skull-Crushers', 'triceps', ''],
    'dumbbell-overhead-tricep-extensions': ['Overhead Extensions', 'triceps', ''],
    'dumbbell-lateral-raises': ['Lateral Raises', 'sideDelts', 'frontDelts traps'],
    'dumbbell-shoulder-press': ['Shoulder Press', 'frontDelts', 'sideDelts triceps traps'],
    'face-pulls': ['Face Pulls', 'rearDelts', 'upperBack traps'],
    'barbell-back-squats': ['Squats', 'quads glutes', 'adductors hamstrings lowerBack'],
    'leg-press': ['Leg Press', 'quads', 'glutes adductors'],
    'hack-squats': ['Hack Squats', 'quads', 'glutes adductors'],
    'barbell-stiff-legged-deadlift': ['Stiff Legged Deadlift', 'hamstrings glutes', 'lowerBack forearms'],
    'lying-leg-curls': ['Lying Leg Curls', 'hamstrings', 'calves'],
    'standing-calf-raises': ['Calf Raises', 'calves', ''],
    'barbell-hip-thrusts': ['Hip Thrusts', 'glutes', 'hamstrings quads'],
    'dumbbell-lunges': ['Lunges', 'glutes quads', 'hamstrings adductors'],
    'sit-ups': ['Sit-ups', 'abs', 'obliques'],
    'lying-leg-raises': ['Leg Raises', 'abs', 'obliques'],
    'barbell-bench-press': ['Bench Press', 'chest', 'frontDelts triceps'],
    'barbell-incline-bench-press': ['Incline Bench', 'chest', 'frontDelts triceps'],
    'pec-deck': ['Pec Dec', 'chest', 'frontDelts'],
    'dumbbell-flyes': ['Chest Fly', 'chest', 'frontDelts'],
    'dumbbell-shrugs': ['Shrugs', 'traps', 'forearms'],
    'lat-pulldowns': ['Lat Pull-down', 'lats', 'biceps upperBack rearDelts'],
    'pull-ups': ['Pull-ups', 'lats', 'biceps upperBack forearms'],
    'lat-prayers': ['Lat Prayers', 'lats', 'triceps abs'],
    'barbell-bent-over-rows': ['Bent-over Rows', 'lats upperBack', 'traps rearDelts biceps lowerBack'],
    'chest-supported-dumbbell-rows': ['Chest-Supported Rows', 'upperBack lats', 'traps rearDelts biceps'],
    'leg-extensions': ['Leg Extensions', 'quads', ''],
    'behind-the-back-cable-curls': ['Behind-the-Back Bicep Curls', 'biceps', 'forearms'],
    'smith-machine-squats': ['Smith-Squats', 'quads glutes', 'adductors hamstrings'],
    'cable-lateral-raises': ['Cable Lateral Raises', 'sideDelts', 'frontDelts traps'],
    'barbell-upright-rows': ['Upright Rows', 'sideDelts traps', 'frontDelts biceps'],
    'dumbbell-bench-press': ['Dumbbell Bench Press', 'chest', 'frontDelts triceps'],
    'incline-dumbbell-press': ['Incline Dumbbell Press', 'chest', 'frontDelts triceps'],
    'barbell-decline-bench-press': ['Decline Bench Press', 'chest', 'triceps frontDelts'],
    'cable-crossovers': ['Cable Crossovers', 'chest', 'frontDelts'],
    'push-ups': ['Push-ups', 'chest', 'frontDelts triceps abs'],
    'machine-chest-press': ['Machine Chest Press', 'chest', 'frontDelts triceps'],
    'chin-ups': ['Chin-ups', 'lats biceps', 'upperBack forearms'],
    'straight-arm-pulldowns': ['Straight-Arm Pulldowns', 'lats', 'rearDelts triceps'],
    'seated-cable-rows': ['Seated Cable Rows', 'upperBack lats', 'traps rearDelts biceps'],
    'single-arm-dumbbell-rows': ['Single-Arm Dumbbell Rows', 'lats', 'upperBack rearDelts biceps'],
    't-bar-rows': ['T-Bar Rows', 'upperBack lats', 'traps rearDelts biceps lowerBack'],
    'barbell-deadlift': ['Deadlift', 'glutes hamstrings lowerBack', 'quads traps lats forearms'],
    'barbell-overhead-press': ['Overhead Press', 'frontDelts', 'sideDelts triceps traps'],
    'arnold-press': ['Arnold Press', 'frontDelts', 'sideDelts triceps'],
    'dumbbell-front-raises': ['Front Raises', 'frontDelts', 'sideDelts chest'],
    'dumbbell-rear-delt-flyes': ['Rear Delt Flyes', 'rearDelts', 'upperBack traps'],
    'hammer-curls': ['Hammer Curls', 'biceps forearms', ''],
    'cable-curls': ['Cable Curls', 'biceps', 'forearms'],
    'concentration-curls': ['Concentration Curls', 'biceps', 'forearms'],
    'dips': ['Dips', 'triceps', 'chest frontDelts'],
    'close-grip-bench-press': ['Close-Grip Bench Press', 'triceps', 'chest frontDelts'],
    'dumbbell-tricep-kickbacks': ['Tricep Kickbacks', 'triceps', ''],
    'barbell-front-squats': ['Front Squats', 'quads', 'glutes adductors abs'],
    'dumbbell-bulgarian-split-squats': ['Bulgarian Split Squats', 'quads glutes', 'adductors hamstrings'],
    'goblet-squats': ['Goblet Squats', 'quads glutes', 'adductors abs'],
    'wall-sit': ['Wall Sit', 'quads', 'glutes'],
    'barbell-romanian-deadlift': ['Romanian Deadlift', 'hamstrings glutes', 'lowerBack forearms'],
    'seated-leg-curls': ['Seated Leg Curls', 'hamstrings', 'calves'],
    'good-mornings': ['Good Mornings', 'hamstrings', 'glutes lowerBack'],
    'glute-bridges': ['Glute Bridges', 'glutes', 'hamstrings'],
    'cable-glute-kickbacks': ['Cable Kickbacks', 'glutes', 'hamstrings'],
    'machine-hip-abductions': ['Hip Abductions', 'abductors', 'glutes'],
    'seated-calf-raises': ['Seated Calf Raises', 'calves', ''],
    'plank': ['Plank', 'abs', 'obliques frontDelts'],
    'side-plank': ['Side Plank', 'obliques', 'abs abductors'],
    'crunches': ['Crunches', 'abs', 'obliques'],
    'cable-crunches': ['Cable Crunches', 'abs', 'obliques'],
    'russian-twists': ['Russian Twists', 'obliques', 'abs'],
    'hanging-leg-raises': ['Hanging Leg Raises', 'abs', 'obliques forearms'],
    'ab-wheel-rollouts': ['Ab Wheel Rollouts', 'abs', 'obliques lats frontDelts'],
    // The library's later exercises
    'decline-dumbbell-press': ['Decline Dumbbell Press', 'chest', 'triceps frontDelts'],
    'smith-machine-bench-press': ['Smith Machine Bench Press', 'chest', 'frontDelts triceps'],
    'smith-machine-incline-press': ['Smith Machine Incline Press', 'chest', 'frontDelts triceps'],
    'incline-machine-press': ['Incline Machine Press', 'chest', 'frontDelts triceps'],
    'incline-dumbbell-flyes': ['Incline Dumbbell Flyes', 'chest', 'frontDelts'],
    'low-to-high-cable-flyes': ['Low-to-High Cable Flyes', 'chest', 'frontDelts'],
    'incline-push-ups': ['Incline Push-ups', 'chest', 'frontDelts triceps'],
    'decline-push-ups': ['Decline Push-ups', 'chest', 'frontDelts triceps abs'],
    'chest-dips': ['Chest Dips', 'chest', 'triceps frontDelts'],
    'seated-barbell-shoulder-press': ['Seated Barbell Shoulder Press', 'frontDelts', 'sideDelts triceps traps'],
    'machine-shoulder-press': ['Machine Shoulder Press', 'frontDelts', 'sideDelts triceps traps'],
    'smith-machine-shoulder-press': ['Smith Machine Shoulder Press', 'frontDelts', 'sideDelts triceps traps'],
    'barbell-push-press': ['Push Press', 'frontDelts', 'sideDelts triceps traps quads glutes'],
    'landmine-press': ['Landmine Press', 'frontDelts', 'chest triceps'],
    'pike-push-ups': ['Pike Push-ups', 'frontDelts', 'triceps sideDelts traps'],
    'machine-lateral-raises': ['Machine Lateral Raises', 'sideDelts', 'frontDelts traps'],
    'cable-front-raises': ['Cable Front Raises', 'frontDelts', 'sideDelts chest'],
    'plate-front-raises': ['Plate Front Raises', 'frontDelts', 'sideDelts chest'],
    'reverse-pec-deck': ['Reverse Pec Deck', 'rearDelts', 'upperBack traps'],
    'cable-rear-delt-flyes': ['Cable Rear Delt Flyes', 'rearDelts', 'upperBack traps'],
    'band-pull-aparts': ['Band Pull-Aparts', 'rearDelts', 'upperBack traps'],
    'dumbbell-upright-rows': ['Dumbbell Upright Rows', 'sideDelts traps', 'frontDelts biceps'],
    'cable-upright-rows': ['Cable Upright Rows', 'sideDelts traps', 'frontDelts biceps'],
    'ez-bar-curls': ['EZ-Bar Curls', 'biceps', 'forearms'],
    'dumbbell-preacher-curls': ['Dumbbell Preacher Curls', 'biceps', 'forearms'],
    'machine-preacher-curls': ['Machine Preacher Curls', 'biceps', 'forearms'],
    'dumbbell-spider-curls': ['Spider Curls', 'biceps', 'forearms'],
    'dumbbell-zottman-curls': ['Zottman Curls', 'biceps forearms', ''],
    'cable-hammer-curls': ['Cable Hammer Curls', 'biceps forearms', ''],
    'barbell-reverse-curls': ['Reverse Curls', 'forearms', 'biceps'],
    'dumbbell-wrist-curls': ['Wrist Curls', 'forearms', ''],
    'dumbbell-reverse-wrist-curls': ['Reverse Wrist Curls', 'forearms', ''],
    'straight-bar-pushdowns': ['Straight-Bar Pushdowns', 'triceps', ''],
    'cable-overhead-tricep-extensions': ['Cable Overhead Extensions', 'triceps', ''],
    'ez-bar-overhead-tricep-extensions': ['EZ-Bar Overhead Extensions', 'triceps', ''],
    'dumbbell-skull-crushers': ['Dumbbell Skull-Crushers', 'triceps', ''],
    'cable-tricep-kickbacks': ['Cable Tricep Kickbacks', 'triceps', ''],
    'bench-dips': ['Bench Dips', 'triceps', 'frontDelts chest'],
    'assisted-dips': ['Assisted Dips', 'triceps', 'chest frontDelts'],
    'diamond-push-ups': ['Diamond Push-ups', 'triceps', 'chest frontDelts'],
    'close-grip-lat-pulldowns': ['Close-Grip Lat Pull-down', 'lats', 'biceps upperBack'],
    'single-arm-lat-pulldowns': ['Single-Arm Lat Pull-down', 'lats', 'biceps upperBack'],
    'assisted-pull-ups': ['Assisted Pull-ups', 'lats', 'biceps upperBack'],
    'neutral-grip-pull-ups': ['Neutral-Grip Pull-ups', 'lats', 'biceps forearms upperBack'],
    'dumbbell-pullovers': ['Dumbbell Pullovers', 'lats chest', 'triceps'],
};

const MUSCLES = [...new Set([...Object.keys(front), ...Object.keys(back)])];
const list = (s) => s.split(' ').filter(Boolean);
const exercises = Object.entries(EXERCISES).map(([key, [name, primary, secondary]]) => {
    const p = list(primary), sec = list(secondary);
    for (const m of [...p, ...sec]) if (!MUSCLES.includes(m)) throw new Error(`${name}: no muscle ${m}`);
    if (p.some((m) => sec.includes(m))) throw new Error(`${name}: a muscle is both primary and secondary`);
    return { key, name, primary: p, secondary: sec };
});

module.exports = { W, H, body, front, back, exercises, MUSCLES };

if (require.main === module) {
    const out = process.argv[2] || path.join(__dirname, '../../Kitae/Exercises/Muscles/ExerciseMuscles.json');
    const json = { width: W, height: H, body, front, back, exercises };
    fs.mkdirSync(path.dirname(out), { recursive: true });
    fs.writeFileSync(out, JSON.stringify(json) + '\n');
    console.log(`wrote ${out}`);
}
