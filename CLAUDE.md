# Orbiter Panel

Unofficial Warframe second-screen companion for Android, designed for the AYN Thor's bottom screen
(3.92", 1240×1080, roughly 31:27). Built with AI assistance; not affiliated with Digital Extremes.

## Layout

- `web/` – the app itself. Bundled into the APK as assets and served from `https://appassets.androidplatform.net/assets/`.
  - `index.html` – the whole app (HTML, CSS, JS in one file). Tabs: World, Fissures, Missions, Events, Weekly, Vendors, Search.
  - `event-fx.js` – canvas animations per event theme (`window.EventFX`), matched by event tag/name.
  - `widget.html` – settings screen for home screen widgets (talks to `window.OrbiterWidget`).
  - `items.json` – item name → image index (`{v:2, c:[categories], i:[[name, image, catIdx, location?]]}`), built from `@wfcd/items`.
  - `worldstate-parser.js` – bundled WFCD `warframe-worldstate-parser` (English only), used when warframestat.us is down.
  - `sw.js`, `manifest.webmanifest`, icons – for the installable web version.
- `app/` – Android wrapper (Java, minSdk 26, targetSdk 34).
  - `MainActivity` – full-screen WebView; fetches api.warframestat.us, api.warframe.market and api.warframe.com natively (CORS-free); falls back to the WebView's own network on any non-2xx.
  - `Updater` – `window.OrbiterApp` bridge: version, check/download/install updates from GitHub Releases, widget snapshot, start tab.
  - `OrbiterWidget`, `WidgetData`, `WidgetRefreshWorker`, `WidgetConfigActivity` – home screen widgets (RemoteViews, Chronometer countdowns, WorkManager refresh every 15 min, inexact alarm at next expiry).
- `.github/workflows/build-apk.yml` – every push to `main` builds a signed APK and publishes it as release `build-N` (N = versionCode = run number).

## Data sources

- World state: `https://api.warframestat.us/pc?language=en` (all platforms redirect to pc since crossplay).
  When it fails or is stale (it 404s "WorldState Not Found" during outages, or serves an old copy: older than 15 min or no live fissures) the app reads `https://api.warframe.com/cdn/worldState.php` and parses it with `worldstate-parser.js`.
- Drops: `https://api.warframestat.us/drops/search/{query}`.
- Prices: warframe.market v2 – `/v2/items`, `/v2/orders/item/{slug}/top` (headers Platform, Language, Crossplay).
- Images: `https://cdn.warframestat.us/img/{imageName}`.
- Updates: GitHub Releases of `cr4mb/orbiterpanel` (renamed from `cr4mb/companion`). The repo must be public for the in-app updater to see releases.

## Conventions

- Keep the app a single `index.html` plus the few helper files; no build step for the web app.
- Look: dark OLED, translucent navy panels, hairline frames with corner ticks, Rajdhani headings, Saira body, IBM Plex Mono numbers, gold `#e2bb64` accent, Void `#82e4ff`.
- Type scales with the screen (`html` font-size uses vw/vh); test layouts at 620×540 @2x, 516×450 @2.4x and 413×360 @3x (Thor bottom-screen viewports) – the World tab should fit with no scrolling.
- Per-device state lives in `localStorage` under `orbiter.*` keys, always wrapped in try/catch.
- Respect `prefers-reduced-motion`.
- The Android project can't be compiled without the Android SDK; push to `main` and let GitHub Actions build it, then check the run.
- The signing key `app/orbiter.keystore` is intentionally public (so builds install over each other). Forks should make their own.
- Commit messages describe the change in plain language.
