package com.orbiterpanel.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Home screen widgets. Each widget has its own settings (see WidgetConfigActivity). Countdowns under
 * ten hours tick live with a Chronometer, so the widget only needs redrawing when something ends.
 */
public class OrbiterWidget extends AppWidgetProvider {
    static final String ACTION_TICK = "com.orbiterpanel.app.WIDGET_TICK";
    private static final String WORK = "orbiter-widget-refresh";
    private static final long LIVE_LIMIT = 10 * WidgetData.HOUR;

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) render(ctx, mgr, id);
        scheduleNext(ctx);
        ensureWork(ctx);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context ctx, AppWidgetManager mgr, int id, Bundle options) {
        render(ctx, mgr, id);
    }

    @Override
    public void onDeleted(Context ctx, int[] ids) {
        for (int id : ids) WidgetData.deleteConfig(ctx, id);
    }

    @Override
    public void onEnabled(Context ctx) {
        ensureWork(ctx);
    }

    @Override
    public void onDisabled(Context ctx) {
        WorkManager.getInstance(ctx).cancelUniqueWork(WORK);
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(tickIntent(ctx));
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (ACTION_TICK.equals(intent.getAction())) {
            updateAll(ctx);
            return;
        }
        super.onReceive(ctx, intent);
    }

    static void updateAll(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, OrbiterWidget.class));
        for (int id : ids) render(ctx, mgr, id);
        if (ids.length > 0) scheduleNext(ctx);
    }

    static void ensureWork(Context ctx) {
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(WidgetRefreshWorker.class, 15, TimeUnit.MINUTES).build();
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req);
    }

    // ------------------------------------------------------------------ redraw timing

    private static PendingIntent tickIntent(Context ctx) {
        Intent i = new Intent(ctx, OrbiterWidget.class).setAction(ACTION_TICK);
        return PendingIntent.getBroadcast(ctx, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Redraw when the soonest shown countdown ends or drops under the live-ticking limit. */
    static void scheduleNext(Context ctx) {
        long now = System.currentTimeMillis(), next = Long.MAX_VALUE;
        for (long t : upcoming(ctx, now)) {
            if (t > now && t < next) next = t;
            long live = t - LIVE_LIMIT;
            if (live > now && live < next) next = live;
        }
        if (next == Long.MAX_VALUE) return;
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long at = next + 2000;
        // An inexact window keeps this free of the exact-alarm permission; a minute of slack is fine here.
        am.setWindow(AlarmManager.RTC, at, 60_000L, tickIntent(ctx));
    }

    private static List<Long> upcoming(Context ctx, long now) {
        List<Long> out = new ArrayList<>();
        JSONObject snap = WidgetData.snapshot(ctx);
        for (Object[] d : WidgetData.CYCLES) {
            WidgetData.Cycle c = WidgetData.cycle(snap, (String) d[0], now);
            if (c != null) out.add(c.end);
        }
        out.add(WidgetData.nextDaily(now));
        out.add(WidgetData.nextWeekly(now));
        if (snap != null) {
            out.add(WidgetData.rollForward(snap.optLong("sortieEnd"), WidgetData.DAY, now));
            out.add(WidgetData.rollForward(snap.optLong("archonEnd"), WidgetData.WEEK, now));
            out.add(snap.optLong("baroStart"));
            out.add(snap.optLong("baroEnd"));
            JSONArray f = snap.optJSONArray("fissures");
            if (f != null) for (int i = 0; i < f.length(); i++) out.add(f.optJSONObject(i).optLong("end"));
        }
        return out;
    }

    // ------------------------------------------------------------------ drawing

    private static final class Style {
        int accent, text, muted;
        float scale;
        boolean frame;
        int bgAlpha;
    }

    private static Style style(JSONObject cfg) {
        Style s = new Style();
        switch (cfg.optString("accent", "gold")) {
            case "void": s.accent = 0xFF82E4FF; break;
            case "white": s.accent = 0xFFFFFFFF; break;
            default: s.accent = 0xFFE2BB64;
        }
        s.text = 0xFFF2F6FA;
        s.muted = 0xFFB4C2CF;
        switch (cfg.optString("size", "m")) {
            case "s": s.scale = 0.86f; break;
            case "l": s.scale = 1.2f; break;
            default: s.scale = 1f;
        }
        s.frame = cfg.optBoolean("frame", true);
        s.bgAlpha = Math.max(0, Math.min(255, Math.round(cfg.optInt("bg", 40) * 2.55f)));
        return s;
    }

    static void render(Context ctx, AppWidgetManager mgr, int id) {
        try {
            JSONObject cfg = WidgetData.config(ctx, id);
            mgr.updateAppWidget(id, build(ctx, mgr, id, cfg));
        } catch (Exception e) {
            // A broken widget should never take the launcher down; leave the last good drawing.
        }
    }

    static RemoteViews build(Context ctx, AppWidgetManager mgr, int id, JSONObject cfg) {
        Style st = style(cfg);
        String type = cfg.optString("type", "cycles");
        long now = System.currentTimeMillis();
        JSONObject snap = WidgetData.snapshot(ctx);

        RemoteViews root;
        if ("single".equals(type)) {
            root = new RemoteViews(ctx.getPackageName(), R.layout.widget_single);
            fillSingle(ctx, root, snap, cfg.optString("single", "cetusCycle"), st, now);
        } else {
            root = new RemoteViews(ctx.getPackageName(), R.layout.widget_root);
            root.removeAllViews(R.id.rows);
            int maxRows = Math.max(1, Math.min(8, cfg.optInt("rows", 4)));
            int fit = rowsThatFit(mgr, id, st, cfg.optBoolean("title", true));
            int limit = Math.min(maxRows, fit);
            String title;
            int added;
            switch (type) {
                case "resets": title = "Resets"; added = addResets(ctx, root, snap, cfg, st, now, limit); break;
                case "fissures": title = fissureTitle(cfg); added = addFissures(ctx, root, snap, cfg, st, now, limit); break;
                default: title = "Open worlds"; added = addCycles(ctx, root, snap, cfg, st, now, limit);
            }
            if (added == 0) {
                RemoteViews empty = row(ctx, st, st.muted, snap == null ? "Open Orbiter Panel once" : "Nothing to show",
                        snap == null ? "The widget fills in after the app loads data" : "Change this widget's settings", -1, now);
                root.addView(R.id.rows, empty);
            }
            if (cfg.optBoolean("title", true)) {
                root.setViewVisibility(R.id.title, View.VISIBLE);
                root.setTextViewText(R.id.title, title.toUpperCase());
                root.setTextColor(R.id.title, st.accent);
                root.setTextViewTextSize(R.id.title, TypedValue.COMPLEX_UNIT_SP, 10.5f * st.scale);
            } else {
                root.setViewVisibility(R.id.title, View.GONE);
            }
        }
        root.setInt(R.id.bg, "setImageAlpha", st.bgAlpha);
        root.setViewVisibility(R.id.frame, st.frame ? View.VISIBLE : View.GONE);
        root.setOnClickPendingIntent(R.id.root, openApp(ctx, id, tabFor(type)));
        return root;
    }

    private static String tabFor(String type) {
        switch (type) {
            case "fissures": return "fissures";
            case "resets": return "world";
            default: return "world";
        }
    }

    private static PendingIntent openApp(Context ctx, int id, String tab) {
        Intent i = new Intent(ctx, MainActivity.class)
                .setAction("com.orbiterpanel.app.OPEN_" + id)
                .putExtra("tab", tab)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(ctx, id, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static int rowsThatFit(AppWidgetManager mgr, int id, Style st, boolean title) {
        Bundle o = mgr.getAppWidgetOptions(id);
        int h = o == null ? 0 : o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
        if (h <= 0) return 8;
        float rowDp = 34f * st.scale, chrome = 20f + (title ? 18f * st.scale : 0f);
        return Math.max(1, (int) ((h - chrome) / rowDp));
    }

    /** One line: colored diamond, a label with a sub line, and a countdown on the right. */
    private static RemoteViews row(Context ctx, Style st, int color, String label, String sub, long end, long now) {
        RemoteViews r = new RemoteViews(ctx.getPackageName(), R.layout.widget_row);
        r.setTextColor(R.id.dot, color);
        r.setTextViewTextSize(R.id.dot, TypedValue.COMPLEX_UNIT_SP, 9f * st.scale);
        r.setTextViewText(R.id.label, label);
        r.setTextColor(R.id.label, st.text);
        r.setTextViewTextSize(R.id.label, TypedValue.COMPLEX_UNIT_SP, 13.5f * st.scale);
        if (sub == null || sub.isEmpty()) {
            r.setViewVisibility(R.id.sub, View.GONE);
        } else {
            r.setTextViewText(R.id.sub, sub);
            r.setTextColor(R.id.sub, st.muted);
            r.setTextViewTextSize(R.id.sub, TypedValue.COMPLEX_UNIT_SP, 10.5f * st.scale);
        }
        setTimer(r, R.id.timer, R.id.timer_static, st, color == st.muted ? st.text : st.text, 14f * st.scale, end, now);
        return r;
    }

    private static void setTimer(RemoteViews r, int chrono, int text, Style st, int color, float sp, long end, long now) {
        long left = end - now;
        if (end <= 0) {
            r.setViewVisibility(chrono, View.GONE);
            r.setViewVisibility(text, View.GONE);
        } else if (left > 0 && left < LIVE_LIMIT) {
            r.setViewVisibility(text, View.GONE);
            r.setViewVisibility(chrono, View.VISIBLE);
            r.setChronometer(chrono, SystemClock.elapsedRealtime() + left, null, true);
            r.setChronometerCountDown(chrono, true);
            r.setTextColor(chrono, color);
            r.setTextViewTextSize(chrono, TypedValue.COMPLEX_UNIT_SP, sp);
        } else {
            r.setViewVisibility(chrono, View.GONE);
            r.setViewVisibility(text, View.VISIBLE);
            r.setTextViewText(text, WidgetData.shortDur(left));
            r.setTextColor(text, color);
            r.setTextViewTextSize(text, TypedValue.COMPLEX_UNIT_SP, sp);
        }
    }

    private static int addCycles(Context ctx, RemoteViews root, JSONObject snap, JSONObject cfg, Style st, long now, int limit) {
        int n = 0;
        for (String key : WidgetData.list(cfg.optJSONArray("cycles"))) {
            if (n >= limit) break;
            WidgetData.Cycle c = WidgetData.cycle(snap, key, now);
            if (c == null) continue;
            String sub = c.label + "  →  " + c.next + " " + WidgetData.clock(ctx, c.end);
            root.addView(R.id.rows, row(ctx, st, WidgetData.stateColor(c.id), c.place, sub, c.end, now));
            n++;
        }
        return n;
    }

    private static int addResets(Context ctx, RemoteViews root, JSONObject snap, JSONObject cfg, Style st, long now, int limit) {
        int n = 0;
        for (String key : WidgetData.list(cfg.optJSONArray("resets"))) {
            if (n >= limit) break;
            String label = null, sub = null;
            long end = 0;
            switch (key) {
                case "daily": label = "Daily reset"; end = WidgetData.nextDaily(now); break;
                case "weekly": label = "Weekly reset"; end = WidgetData.nextWeekly(now); break;
                case "sortie":
                    if (snap != null && snap.optLong("sortieEnd") > 0) { label = "Sortie"; end = WidgetData.rollForward(snap.optLong("sortieEnd"), WidgetData.DAY, now); }
                    break;
                case "archon":
                    if (snap != null && snap.optLong("archonEnd") > 0) {
                        label = "Archon Hunt"; end = WidgetData.rollForward(snap.optLong("archonEnd"), WidgetData.WEEK, now);
                        sub = snap.optString("archonBoss", "");
                    }
                    break;
                case "baro":
                    if (snap != null && snap.optLong("baroStart") > 0) {
                        long a = snap.optLong("baroStart"), e = snap.optLong("baroEnd");
                        if (now < a) { label = "Baro arrives"; end = a; }
                        else if (now < e) { label = "Baro leaves"; end = e; }
                        sub = snap.optString("baroLoc", "");
                    }
                    break;
                default: break;
            }
            if (label == null || end <= now) continue;
            if (sub == null || sub.isEmpty()) sub = end - now < 20 * WidgetData.HOUR ? WidgetData.clock(ctx, end) : WidgetData.dayClock(ctx, end);
            root.addView(R.id.rows, row(ctx, st, st.accent, label, sub, end, now));
            n++;
        }
        return n;
    }

    private static String fissureTitle(JSONObject cfg) {
        switch (cfg.optString("fisMode", "normal")) {
            case "hard": return "Steel Path fissures";
            case "storm": return "Void Storms";
            case "all": return "Fissures";
            default: return "Fissures";
        }
    }

    private static final Set<String> FAST = new HashSet<>();
    static { Collections.addAll(FAST, "Capture", "Extermination", "Sabotage", "Rescue"); }

    private static int addFissures(Context ctx, RemoteViews root, JSONObject snap, JSONObject cfg, Style st, long now, int limit) {
        if (snap == null) return 0;
        JSONArray all = snap.optJSONArray("fissures");
        if (all == null) return 0;
        String mode = cfg.optString("fisMode", "normal");
        Set<String> tiers = new HashSet<>(WidgetData.list(cfg.optJSONArray("fisTiers")));
        boolean fast = cfg.optBoolean("fisFast", false);
        List<JSONObject> list = new ArrayList<>();
        for (int i = 0; i < all.length(); i++) {
            JSONObject f = all.optJSONObject(i);
            if (f == null || f.optLong("end") <= now) continue;
            boolean storm = f.optBoolean("storm"), hard = f.optBoolean("hard");
            String m = storm ? "storm" : hard ? "hard" : "normal";
            if (!"all".equals(mode) && !mode.equals(m)) continue;
            if (!tiers.isEmpty() && !tiers.contains(f.optString("tier"))) continue;
            if (fast && !FAST.contains(f.optString("type"))) continue;
            list.add(f);
        }
        Collections.sort(list, (a, b) -> a.optInt("n", 9) != b.optInt("n", 9)
                ? Integer.compare(a.optInt("n", 9), b.optInt("n", 9))
                : Long.compare(a.optLong("end"), b.optLong("end")));
        int n = 0;
        for (JSONObject f : list) {
            if (n >= limit) break;
            String tier = f.optString("tier");
            String extra = "all".equals(mode) ? (f.optBoolean("hard") ? " · SP" : f.optBoolean("storm") ? " · Storm" : "") : "";
            String label = ("Requiem".equals(tier) ? "Req" : tier) + "  " + f.optString("type") + extra;
            root.addView(R.id.rows, row(ctx, st, WidgetData.tierColor(tier), label, f.optString("node"), f.optLong("end"), now));
            n++;
        }
        return n;
    }

    private static void fillSingle(Context ctx, RemoteViews r, JSONObject snap, String key, Style st, long now) {
        WidgetData.Cycle c = WidgetData.cycle(snap, key, now);
        String place = null;
        for (Object[] d : WidgetData.CYCLES) if (d[0].equals(key)) place = (String) d[1];
        r.setTextViewText(R.id.place, (place == null ? "Open world" : place).toUpperCase());
        r.setTextColor(R.id.place, st.accent);
        r.setTextViewTextSize(R.id.place, TypedValue.COMPLEX_UNIT_SP, 10.5f * st.scale);
        if (c == null) {
            r.setTextViewText(R.id.state, "—");
            r.setTextColor(R.id.state, st.muted);
            r.setTextViewText(R.id.next, snap == null ? "Open Orbiter Panel once to load data" : "No data");
            r.setTextColor(R.id.next, st.muted);
            r.setViewVisibility(R.id.timer, View.GONE);
            r.setViewVisibility(R.id.timer_static, View.GONE);
            return;
        }
        int color = WidgetData.stateColor(c.id);
        r.setTextViewText(R.id.state, c.label);
        r.setTextColor(R.id.state, color);
        r.setTextViewTextSize(R.id.state, TypedValue.COMPLEX_UNIT_SP, 26f * st.scale);
        setTimer(r, R.id.timer, R.id.timer_static, st, st.text, 18f * st.scale, c.end, now);
        r.setTextViewText(R.id.next, c.next + " at " + WidgetData.clock(ctx, c.end));
        r.setTextColor(R.id.next, st.muted);
        r.setTextViewTextSize(R.id.next, TypedValue.COMPLEX_UNIT_SP, 11f * st.scale);
    }
}
