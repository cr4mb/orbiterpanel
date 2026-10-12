# Orbiter Panel

A Warframe second-screen app for the AYN Thor's bottom screen: open-world cycles, resets, fissures,
sortie, Archon Hunt, invasions, Nightwave, Baro, Teshin and Darvo, from the community warframestat.us API.

## Getting the APK

Every push to `main` builds a new APK on GitHub's servers (about 3–5 minutes).

1. Open this repository's **Releases** page (or the **Actions** tab for the latest run).
2. Download **OrbiterPanel.apk** on the Thor.
3. Open it. If Android asks, allow your browser or file manager to **install unknown apps**.
4. Long-press the **Orbiter Panel** icon and choose **Bottom Screen**.

Later builds install over the old one and keep your settings, because every build is signed with
the same key (`app/orbiter.keystore`). That key only identifies this personal app; it is not a secret.

## Layout

- `web/` is the app itself (HTML, item-picture index, icons). It is bundled into the APK.
- `app/` is a small Android wrapper: a full-screen WebView that serves `web/` from inside the APK and
  fetches live data natively.
- `.github/workflows/build-apk.yml` builds and publishes the APK.

To change the app, edit files in `web/` and push; a new APK appears in Releases.

## Notes

- Game images load from cdn.warframestat.us and are Digital Extremes' art, used non-commercially under the
  Warframe Content Policy (https://www.warframe.com/contentpolicy). Not affiliated with Digital Extremes.
- Settings → Text size scales the interface. Nightwave ticks and settings stay on the device.
