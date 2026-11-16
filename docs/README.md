# Kamae's website

The home page for Kamae, the workout tracker for Android and iPhone, at [kamae.fromkoyomi.org](https://kamae.fromkoyomi.org). GitHub Pages serves it from this folder on `main`. It is plain HTML and CSS: no build step, no JavaScript, no cookies and no requests to other sites. Until November 2026 it was Kitae's, at kitae.fromkoyomi.org, from its own repository, koyomi-apps/kitae, which now sends visitors here.

- `index.html` and `style.css` are the page. `koyomi.css` holds the styles every Koyomi site shares (fonts, paper and ink, the theme switch, header, footer and the common sections); it is the same file on fromkoyomi.org and tabe.fromkoyomi.org, so change it in one repository and copy it to the others.
- `fonts/` holds Darker Grotesque and Space Mono as WOFF2, with their SIL Open Font License texts.
- `favicon.svg` and `apple-touch-icon.png` are the barbell from Kamae's icon.
- `screens/` holds the app screenshots as 720 px WebP, in light and dark. `scripts/make_web_screens.py` makes them from the iPhone's screenshots in `screenshots/` and Kamae for Android's screen tests (see the script). Run it again when the apps' screens change.
- `screenshots/` holds the iPhone app's full-size screenshots, which the repository's README shows too.
- `CNAME` is the site's address, and `.nojekyll` tells GitHub Pages to serve the files as they are.

The colours and fonts are Kamae's own, and the page follows the phone's light or dark setting. The header has a switch that flips it, with no script. Each colour is written once as `light-dark(light, dark)`, and the switch flips the page by changing `color-scheme`. That needs Safari 17.5, Chrome 123 or Firefox 121 or newer.

The screenshots on the page follow the light or dark theme, including the header's switch, with no script. They are lazy-loaded, so only the theme being shown is downloaded.

## Preview

Serve the folder and open it in a browser:

```bash
python3 -m http.server 8000 --directory docs
```

## Keeping it private

The `Content-Security-Policy` tag in `index.html` only allows files from this site. If you add something that loads from somewhere else (a font service, an analytics script, an embed), the browser will block it. That is on purpose.
