// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later
'use strict';
// node zoom.js FILE OUT key:t key:t ...  (t = 0..1 or "still"), drawn at 2x.
const { load, card, render, THEMES } = require('./motion');
const [file, out, ...picks] = process.argv.slice(2);
const motions = load(file), W = 220, H = 230, G = 8;
let defs = '', body = `<rect width="${G + picks.length * (W + G)}" height="${H + 2 * G}" fill="${THEMES.light.page}"/>`;
picks.forEach((p, i) => {
  const [key, t] = p.split(':'), m = motions.find(x => x.key === key);
  const k = card(m, t === 'still' ? 0 : Number(t), 'light', G + i * (W + G), G, W, H, t === 'still');
  defs += k.defs; body += k.body;
});
render(`<svg xmlns="http://www.w3.org/2000/svg" width="${G + picks.length * (W + G)}" height="${H + 2 * G}"><defs>${defs}</defs>${body}</svg>`, out, 2);
