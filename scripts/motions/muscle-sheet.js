// Kitae for iPhone
// SPDX-License-Identifier: GPL-3.0-or-later
//
// Draws muscle maps as a picture for checking, like the app draws them.
//
//     node muscle-sheet.js ExerciseMuscles.json out.png light|dark [keys, "all-muscles" or "blank"]

const fs = require('fs');
const path = require('path');
const { Resvg } = require('@resvg/resvg-js');

// The app's colours: MuscleMapBase, MuscleMapIdle, MuscleMapSecondary and Olive, on Card.
const THEMES = {
    light: { card: '#F4F0E6', page: '#FDF9F0', base: '#D8D4C6', muscle: '#BAB6A6', secondary: '#9B984C',
             primary: '#63611E', text: '#1C1C16', soft: '#8F8C7A' },
    dark: { card: '#1E1E18', page: '#14140E', base: '#45443B', muscle: '#6D6A5F', secondary: '#9A975E',
            primary: '#CECA75', text: '#E6E2D9', soft: '#7D7A6B' },
};

const d = (flat) => {
    let s = `M${flat[0]} ${flat[1]}`;
    for (let i = 2; i < flat.length; i += 6) s += `C${flat.slice(i, i + 6).join(' ')}`;
    return s + 'Z';
};

function figure(map, view, work, c, x, y, scale, id) {
    const body = map.body.map(d).join('');
    const parts = [];
    for (const [name, shapes] of Object.entries(map[view])) {
        const fill = work.primary.includes(name) ? c.primary : work.secondary.includes(name) ? c.secondary : c.muscle;
        parts.push(`<path d="${shapes.map(d).join('')}" fill="${fill}" stroke="${c.base}" stroke-width="1.1" stroke-linejoin="round"/>`);
    }
    return `<g transform="translate(${x} ${y}) scale(${scale})">
      <clipPath id="clip${id}"><path d="${body}"/></clipPath>
      <path d="${body}" fill="${c.base}"/>
      <g clip-path="url(#clip${id})">${parts.join('')}</g></g>`;
}

function card(map, work, c, name, x, y, w, h, id) {
    const scale = (h - 28) / map.height;
    const fw = map.width * scale;
    const label = (t, ty, color, size = 11, font = 'Space Mono', weight = 400) =>
        `<text x="${x + 2 * fw + 34}" y="${ty}" font-family="${font}" font-size="${size}" font-weight="${weight}" fill="${color}">${t}</text>`;
    const names = (list) => list.map((m) => NAMES[m]);
    let ty = y + 34;
    const lines = [];
    lines.push(label(name, ty, c.text, 15, 'Darker Grotesque', 700)); ty += 24;
    if (work.primary.length) {
        lines.push(`<rect x="${x + 2 * fw + 34}" y="${ty - 9}" width="9" height="9" rx="2.5" fill="${c.primary}"/>`);
        lines.push(`<text x="${x + 2 * fw + 48}" y="${ty}" font-family="Space Mono" font-size="10" fill="${c.soft}">.primary</text>`);
        ty += 17;
        for (const n of names(work.primary)) { lines.push(label(n, ty, c.text, 13, 'Darker Grotesque', 600)); ty += 16; }
        ty += 8;
    }
    if (work.secondary.length) {
        lines.push(`<rect x="${x + 2 * fw + 34}" y="${ty - 9}" width="9" height="9" rx="2.5" fill="${c.secondary}"/>`);
        lines.push(`<text x="${x + 2 * fw + 48}" y="${ty}" font-family="Space Mono" font-size="10" fill="${c.soft}">.secondary</text>`);
        ty += 17;
        for (const n of names(work.secondary)) { lines.push(label(n, ty, c.text, 13, 'Darker Grotesque', 600)); ty += 16; }
    }
    return `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="24" fill="${c.card}"/>
      ${figure(map, 'front', work, c, x + 14, y + 14, scale, `${id}f`)}
      ${figure(map, 'back', work, c, x + 22 + fw, y + 14, scale, `${id}b`)}
      ${lines.join('')}`;
}

const NAMES = {
    chest: 'Chest', frontDelts: 'Front delts', sideDelts: 'Side delts', rearDelts: 'Rear delts',
    biceps: 'Biceps', triceps: 'Triceps', forearms: 'Forearms', traps: 'Traps', upperBack: 'Upper back',
    lats: 'Lats', lowerBack: 'Lower back', abs: 'Abs', obliques: 'Obliques', glutes: 'Glutes',
    abductors: 'Abductors', adductors: 'Adductors', quads: 'Quads', hamstrings: 'Hamstrings', calves: 'Calves',
};

function main() {
    const [file, out, theme = 'light', which = ''] = process.argv.slice(2);
    const map = JSON.parse(fs.readFileSync(file, 'utf8'));
    const c = THEMES[theme];
    let items;
    if (which === 'all-muscles') {
        const all = [...new Set([...Object.keys(map.front), ...Object.keys(map.back)])];
        items = all.map((m) => ({ name: NAMES[m], work: { primary: [m], secondary: [] } }));
    } else if (which === 'blank') {
        items = [{ name: 'Blank', work: { primary: [], secondary: [] } }];
    } else {
        const byKey = new Map(map.exercises.map((e) => [e.key, e]));
        const keys = which ? which.split(',') : [...byKey.keys()];
        items = keys.map((key) => ({ name: byKey.get(key).name, work: byKey.get(key) }));
    }
    const w = 370, h = 236, cols = Math.min(3, items.length), gap = 14;
    const rows = Math.ceil(items.length / cols);
    const W = cols * w + (cols + 1) * gap, Hh = rows * h + (rows + 1) * gap;
    const cards = items.map((it, i) =>
        card(map, it.work, c, it.name, gap + (i % cols) * (w + gap), gap + Math.floor(i / cols) * (h + gap), w, h, i));
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${Hh}" viewBox="0 0 ${W} ${Hh}">
      <rect width="100%" height="100%" fill="${c.page}"/>${cards.join('')}</svg>`;
    const fonts = ['SpaceMono-Regular.ttf', 'DarkerGrotesque-Bold.ttf', 'DarkerGrotesque-SemiBold.ttf']
        .map((f) => path.join(__dirname, '../../Shared/Fonts', f));
    const png = new Resvg(svg, { fitTo: { mode: 'zoom', value: 2 }, font: { fontFiles: fonts, loadSystemFonts: false, defaultFontFamily: 'Space Mono' } }).render().asPng();
    fs.writeFileSync(out, png);
    console.log(`${out}: ${items.length} maps`);
}

main();
