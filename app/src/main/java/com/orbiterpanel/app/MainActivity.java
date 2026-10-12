package com.orbiterpanel.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Full-screen WebView around the bundled Orbiter Panel web app.
 * The app files ship inside the APK (assets/) and are served from https://appassets.androidplatform.net,
 * so they load instantly and work offline. Live world-state requests are fetched natively,
 * which avoids any cross-origin limits on the data service.
 */
public class MainActivity extends Activity {
    private static final String HOME = "https://appassets.androidplatform.net/assets/index.html";
    private static final String APP_HOST = "appassets.androidplatform.net";
    private static final String DATA_HOST = "api.warframestat.us";
    private static final String MARKET_HOST = "api.warframe.market";

    private WebView web;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        web = new WebView(this);
        web.setBackgroundColor(0xFF03050A);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        web.addJavascriptInterface(new Updater(this, web), "OrbiterApp");

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                String host = u.getHost();
                if ("GET".equals(req.getMethod()) && (DATA_HOST.equals(host) || MARKET_HOST.equals(host))) {
                    WebResourceResponse r = fetchData(u.toString(), MARKET_HOST.equals(host));
                    if (r != null) return r;
                }
                return loader.shouldInterceptRequest(u);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (APP_HOST.equals(u.getHost())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception ignored) {
                }
                return true;
            }
        });

        if (state != null) web.restoreState(state);
        if (web.getUrl() == null) web.loadUrl(HOME);
        hideSystemBars();
    }

    /** Runs on the WebView's background thread, so blocking network calls are fine here. */
    private WebResourceResponse fetchData(String url, boolean market) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(12000);
            c.setReadTimeout(15000);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("User-Agent", "OrbiterPanel/1.0 (Android companion app)");
            if (market) {
                c.setRequestProperty("Platform", "pc");
                c.setRequestProperty("Language", "en");
                c.setRequestProperty("Crossplay", "true");
            }
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            if (in == null) in = new ByteArrayInputStream(new byte[0]);
            String reason = c.getResponseMessage();
            if (reason == null || reason.trim().isEmpty()) reason = code >= 400 ? "Error" : "OK";
            Map<String, String> headers = new HashMap<>();
            headers.put("Access-Control-Allow-Origin", "*");
            headers.put("Cache-Control", "no-store");
            return new WebResourceResponse("application/json", "utf-8", code, reason, headers, in);
        } catch (IOException e) {
            return null; // let the WebView try the request itself; the page shows its offline message
        }
    }

    @SuppressWarnings("deprecation")
    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController ic = getWindow().getInsetsController();
            if (ic != null) {
                ic.hide(WindowInsets.Type.systemBars());
                ic.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onPause() {
        web.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }
}
