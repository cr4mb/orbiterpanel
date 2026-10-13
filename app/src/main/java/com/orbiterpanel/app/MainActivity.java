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
    private volatile String userAgent;

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
        try { userAgent = s.getUserAgentString(); } catch (Exception ignored) { }
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

    /**
     * Runs on the WebView's background thread, so blocking network calls are fine here.
     * Only a clean 2xx answer is handed back. Anything else (an error page, a redirect, a block,
     * no connection) returns null, so the WebView retries the request with its own network stack.
     */
    private WebResourceResponse fetchData(String url, boolean market) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(12000);
            c.setReadTimeout(20000);
            c.setRequestProperty("Accept", "application/json");
            // Look like the WebView itself; some hosts block unknown app identities.
            if (userAgent != null) c.setRequestProperty("User-Agent", userAgent);
            if (market) {
                c.setRequestProperty("Platform", "pc");
                c.setRequestProperty("Language", "en");
                c.setRequestProperty("Crossplay", "true");
            }
            int code = c.getResponseCode();
            if (code < 200 || code > 299) {
                c.disconnect();
                return null;
            }
            InputStream in = c.getInputStream();
            String reason = c.getResponseMessage();
            if (reason == null || reason.trim().isEmpty()) reason = "OK";
            Map<String, String> headers = new HashMap<>();
            headers.put("Access-Control-Allow-Origin", "*");
            headers.put("Cache-Control", "no-store");
            return new WebResourceResponse("application/json", "utf-8", code, reason, headers, in);
        } catch (Exception e) {
            if (c != null) c.disconnect();
            return null;
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
