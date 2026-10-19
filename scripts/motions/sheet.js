// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later
'use strict';
// A compact sheet for checking poses: per exercise the start, the middle of the move, the end
// and the Reduce Motion picture. node sheet.js FILE OUT [light|dark] [keys]
const { load, card, render, THEMES } = require('./motion');
const [file, out, theme = 'light', only] = process.argv.slice(2);
let motions = load(file);
if (only) { motions = only.split(',').map(key => motions.find(m => m.key === key)).filter(Boolean); }
const W = 150, H = 150, G = 6, cols = 4;
const width = G + cols * (W + G), height = G + motions.length * (H + G);
let defs = '', body = `<rect width="${width}" height="${height}" fill="${THEMES[theme].page}"/>`;
motions.forEach((m, r) => {
  const k = m.timing, ts = [0, k[0] + (k[1] - k[0]) / 2, (k[1] + k[2]) / 2, 'still'];
  ts.forEach((t, c) => {
    const x = card(m, t === 'still' ? 0 : t, theme, G + c * (W + G), G + r * (H + G), W, H, t === 'still');
    defs += x.defs; body += x.body;
  });
  body += `<text x="${G + 8}" y="${G + r * (H + G) + 14}" font-size="10" font-family="sans-serif" fill="#888">${m.key}</text>`;
});
render(`<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}"><defs>${defs}</defs>${body}</svg>`, out);
