package com.orbiterpanel.app;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Exposed to the page as window.OrbiterApp. Checks the GitHub repository's latest release
 * (tagged build-N, where N matches the app's versionCode), downloads the APK and hands it to
 * Android's installer. Android only accepts it if it is signed with the same key as this app.
 */
public class Updater {
    static final String REPO = "cr4mb/orbiterpanel";
    private static final String RELEASES_API = "https://api.github.com/repos/" + REPO + "/releases/latest";
    private static final String DOWNLOAD_PREFIX = "https://github.com/" + REPO + "/releases/download/";
    /** The repository's old name; GitHub still redirects it, so links from older builds keep working. */
    private static final String OLD_DOWNLOAD_PREFIX = "https://github.com/cr4mb/companion/releases/download/";

    private static boolean ourDownload(String url) {
        return url != null && (url.startsWith(DOWNLOAD_PREFIX) || url.startsWith(OLD_DOWNLOAD_PREFIX));
    }

    private final Activity activity;
    private final WebView web;
    private volatile boolean busy;

    Updater(Activity activity, WebView web) {
        this.activity = activity;
        this.web = web;
    }

    @JavascriptInterface
    public int versionCode() {
        try {
            PackageInfo p = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= 28) return (int) p.getLongVersionCode();
            return legacyVersionCode(p);
        } catch (Exception e) {
            return 0;
        }
    }

    @SuppressWarnings("deprecation")
    private static int legacyVersionCode(PackageInfo p) {
        return p.versionCode;
    }

    /** The page sends a small world-state snapshot after each refresh; widgets draw from it. */
    @JavascriptInterface
    public void saveWidgetSnapshot(String json) {
        try {
            WidgetData.saveSnapshot(activity, new JSONObject(json));
            OrbiterWidget.updateAll(activity);
        } catch (Exception ignored) { }
    }

    /** Tab a widget asked to open, read once by the page at start-up. */
    @JavascriptInterface
    public String startTab() {
        String t = pendingTab;
        pendingTab = null;
        return t == null ? "" : t;
    }

    volatile String pendingTab;

    @JavascriptInterface
    public void checkForUpdate() {
        new Thread(() -> {
            try {
                HttpURLConnection c = open(RELEASES_API);
                c.setRequestProperty("Accept", "application/vnd.github+json");
                int code = c.getResponseCode();
                if (code == 404) {
                    send(json("error", "msg", "Can't see the app's releases. The GitHub repository (" + REPO + ") has to be public for the app to find new builds."));
                    return;
                }
                if (code != 200) {
                    send(json("error", "msg", "GitHub answered with an error (" + code + "). Try again in a few minutes."));
                    return;
                }
                JSONObject rel = new JSONObject(readAll(c.getInputStream()));
                String tag = rel.optString("tag_name", "");
                int latest = 0;
                try { latest = Integer.parseInt(tag.replaceAll("[^0-9]", "")); } catch (NumberFormatException ignored) { }
                String apk = "";
                JSONArray assets = rel.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject a = assets.getJSONObject(i);
                        if (a.optString("name", "").toLowerCase().endsWith(".apk")) {
                            apk = a.optString("browser_download_url", "");
                            break;
                        }
                    }
                }
                JSONObject r = new JSONObject();
                r.put("current", versionCode());
                r.put("latest", latest);
                r.put("url", apk);
                if (latest > versionCode() && ourDownload(apk)) r.put("state", "available");
                else r.put("state", "latest");
                send(r);
            } catch (Exception e) {
                send(json("error", "msg", "Couldn't reach GitHub. Check the internet connection and try again."));
            }
        }).start();
    }

    @JavascriptInterface
    public void installUpdate(String url) {
        if (busy) return;
        if (!ourDownload(url)) {
            send(json("error", "msg", "That download link doesn't belong to this app's repository."));
            return;
        }
        if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
            activity.runOnUiThread(() -> {
                try {
                    activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + activity.getPackageName())));
                } catch (Exception ignored) { }
            });
            send(json("permission", null, null));
            return;
        }
        busy = true;
        new Thread(() -> {
            try {
                File dir = new File(activity.getCacheDir(), "updates");
                if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("no cache dir");
                File apk = new File(dir, "OrbiterPanel.apk");
                HttpURLConnection c = open(url);
                c.setInstanceFollowRedirects(true);
                if (c.getResponseCode() != 200) throw new IllegalStateException("HTTP " + c.getResponseCode());
                long total = c.getContentLengthLong();
                try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(apk)) {
                    byte[] buf = new byte[32 * 1024];
                    long done = 0;
                    int n, lastPct = -1;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        done += n;
                        if (total > 0) {
                            int pct = (int) (done * 100 / total);
                            if (pct >= lastPct + 5) {
                                lastPct = pct;
                                JSONObject p = new JSONObject();
                                p.put("state", "downloading");
                                p.put("pct", pct);
                                send(p);
                            }
                        }
                    }
                }
                Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".files", apk);
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(uri, "application/vnd.android.package-archive");
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.runOnUiThread(() -> activity.startActivity(i));
                send(json("installing", null, null));
            } catch (Exception e) {
                send(json("error", "msg", "The download didn't finish. Check the connection and try again."));
            } finally {
                busy = false;
            }
        }).start();
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", "OrbiterPanel-Updater");
        return c;
    }

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        in.close();
        return b.toString("UTF-8");
    }

    private static JSONObject json(String state, String key, String value) {
        JSONObject o = new JSONObject();
        try {
            o.put("state", state);
            if (key != null) o.put(key, value);
        } catch (Exception ignored) { }
        return o;
    }

    private void send(JSONObject o) {
        final String js = "window.__orbiterUpdate && window.__orbiterUpdate(" + o.toString() + ")";
        web.post(() -> web.evaluateJavascript(js, null));
    }
}
