package com.orbiterpanel.app;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

import org.json.JSONObject;

/** The widget settings screen, shown when a widget is added or reconfigured. Built as a page in assets/widget.html. */
public class WidgetConfigActivity extends Activity {
    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private WebView web;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        // If the person backs out, the launcher drops the new widget.
        setResult(RESULT_CANCELED);
        Bundle extras = getIntent().getExtras();
        if (extras != null) widgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        web = new WebView(this);
        web.setBackgroundColor(0xFF03050A);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        web.addJavascriptInterface(new Bridge(), "OrbiterWidget");

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                return loader.shouldInterceptRequest(req.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                return !"appassets.androidplatform.net".equals(u.getHost());
            }
        });
        web.loadUrl("https://appassets.androidplatform.net/assets/widget.html");
    }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }

    private final class Bridge {
        @JavascriptInterface
        public String config() {
            return WidgetData.config(WidgetConfigActivity.this, widgetId).toString();
        }

        @JavascriptInterface
        public String snapshot() {
            JSONObject s = WidgetData.snapshot(WidgetConfigActivity.this);
            return s == null ? "" : s.toString();
        }

        @JavascriptInterface
        public void save(String json) {
            runOnUiThread(() -> {
                try {
                    JSONObject cfg = new JSONObject(json);
                    WidgetData.saveConfig(WidgetConfigActivity.this, widgetId, cfg);
                    AppWidgetManager mgr = AppWidgetManager.getInstance(WidgetConfigActivity.this);
                    OrbiterWidget.render(WidgetConfigActivity.this, mgr, widgetId);
                    OrbiterWidget.scheduleNext(WidgetConfigActivity.this);
                    OrbiterWidget.ensureWork(WidgetConfigActivity.this);
                    setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId));
                } catch (Exception ignored) {
                }
                finish();
            });
        }

        @JavascriptInterface
        public void cancel() {
            runOnUiThread(WidgetConfigActivity.this::finish);
        }
    }
}
