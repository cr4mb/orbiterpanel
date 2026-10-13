package com.orbiterpanel.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * The data behind the home screen widgets: a small snapshot of the world state saved by the app
 * (or by the background refresh), plus the maths to roll cycles and resets forward on their own.
 */
final class WidgetData {
    static final String PREFS = "orbiter_widgets";
    private static final String KEY_SNAPSHOT = "snapshot";
    static final long MIN = 60_000L, HOUR = 60 * MIN, DAY = 24 * HOUR, WEEK = 7 * DAY;

    /** key, place, then phases as {id, label, seconds}. Mirrors the CYCLES table in the web app. */
    static final Object[][] CYCLES = {
            {"cetusCycle", "Cetus", new Object[][]{{"day", "Day", 6000}, {"night", "Night", 3000}}},
            {"vallisCycle", "Orb Vallis", new Object[][]{{"warm", "Warm", 400}, {"cold", "Cold", 1200}}},
            {"cambionCycle", "Cambion Drift", new Object[][]{{"fass", "Fass", 6000}, {"vome", "Vome", 3000}}},
            {"zarimanCycle", "Zariman", new Object[][]{{"corpus", "Corpus", 9000}, {"grineer", "Grineer", 9000}}},
            {"earthCycle", "Earth", new Object[][]{{"day", "Day", 14400}, {"night", "Night", 14400}}},
            {"duviriCycle", "Duviri", new Object[][]{{"sorrow", "Sorrow", 7200}, {"fear", "Fear", 7200}, {"joy", "Joy", 7200}, {"anger", "Anger", 7200}, {"envy", "Envy", 7200}}},
    };

    static int stateColor(String id) {
        switch (id) {
            case "day": return 0xFFFFD27A;
            case "night": return 0xFFA6B0FF;
            case "warm": return 0xFFFFA45E;
            case "cold": return 0xFF9FDCFF;
            case "fass": return 0xFFFF8A5C;
            case "vome": return 0xFF6EE0CF;
            case "corpus": return 0xFF7CB6FF;
            case "grineer": return 0xFFF07B55;
            case "joy": return 0xFFFFDD66;
            case "anger": return 0xFFFF6555;
            case "envy": return 0xFF90DE74;
            case "sorrow": return 0xFF86A3EE;
            case "fear": return 0xFFC592EA;
            default: return 0xFFECF2F7;
        }
    }

    static int tierColor(String tier) {
        switch (tier == null ? "" : tier) {
            case "Lith": return 0xFFD39B68;
            case "Meso": return 0xFFC3CBD4;
            case "Neo": return 0xFFE6CD80;
            case "Axi": return 0xFFF4EEDB;
            case "Requiem": return 0xFFEF5B52;
            case "Omnia": return 0xFFC39CFF;
            default: return 0xFFECF2F7;
        }
    }

    /** A cycle's state right now, rolled forward if the saved one has ended. */
    static final class Cycle {
        String key, place, id, label, next;
        long start, end;
    }

    static Cycle cycle(JSONObject snap, String key, long now) {
        Object[] def = null;
        for (Object[] d : CYCLES) if (d[0].equals(key)) def = d;
        if (def == null || snap == null) return null;
        JSONObject c = snap.optJSONObject("cycles") == null ? null : snap.optJSONObject("cycles").optJSONObject(key);
        if (c == null) return null;
        Object[][] phases = (Object[][]) def[2];
        String state = c.optString("state", "").toLowerCase(Locale.ROOT);
        long end = c.optLong("end", 0);
        int i = -1;
        for (int k = 0; k < phases.length; k++) if (phases[k][0].equals(state)) i = k;
        if (i < 0 || end <= 0) return null;
        if (end <= now) {
            long period = 0;
            for (Object[] p : phases) period += ((Integer) p[2]) * 1000L;
            end += ((now - end) / period) * period;
            int guard = 0;
            while (end <= now && guard++ < 50) {
                i = (i + 1) % phases.length;
                end += ((Integer) phases[i][2]) * 1000L;
            }
        }
        Cycle out = new Cycle();
        out.key = key;
        out.place = (String) def[1];
        out.id = (String) phases[i][0];
        out.label = (String) phases[i][1];
        out.next = (String) phases[(i + 1) % phases.length][1];
        out.end = end;
        out.start = end - ((Integer) phases[i][2]) * 1000L;
        return out;
    }

    static long nextDaily(long now) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.setTimeInMillis(now);
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis() + DAY;
    }

    static long nextWeekly(long now) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.setTimeInMillis(now);
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        int dow = c.get(Calendar.DAY_OF_WEEK); // Sunday = 1 ... Saturday = 7
        int sinceMonday = (dow + 5) % 7;
        return c.getTimeInMillis() - sinceMonday * DAY + WEEK;
    }

    static long rollForward(long end, long step, long now) {
        if (end <= 0) return 0;
        if (end > now) return end;
        return end + ((now - end) / step + 1) * step;
    }

    /** "4d 11h", "13h 05m", "42m". Used where a live ticking clock would be too long. */
    static String shortDur(long ms) {
        if (ms <= 0) return "now";
        long s = ms / 1000, d = s / 86400, h = (s % 86400) / 3600, m = (s % 3600) / 60;
        if (d > 0) return d + "d " + h + "h";
        if (h > 0) return h + "h " + (m < 10 ? "0" : "") + m + "m";
        return m + "m";
    }

    static String clock(Context ctx, long ms) {
        boolean h24 = android.text.format.DateFormat.is24HourFormat(ctx);
        return new SimpleDateFormat(h24 ? "HH:mm" : "h:mm a", Locale.getDefault()).format(ms);
    }

    static String dayClock(Context ctx, long ms) {
        boolean h24 = android.text.format.DateFormat.is24HourFormat(ctx);
        return new SimpleDateFormat(h24 ? "EEE HH:mm" : "EEE h:mm a", Locale.getDefault()).format(ms);
    }

    // ---------------------------------------------------------------- storage

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static JSONObject snapshot(Context ctx) {
        try {
            String s = prefs(ctx).getString(KEY_SNAPSHOT, null);
            return s == null ? null : new JSONObject(s);
        } catch (Exception e) {
            return null;
        }
    }

    static void saveSnapshot(Context ctx, JSONObject snap) {
        JSONObject old = snapshot(ctx);
        // never replace newer data with older data
        if (old != null && old.optLong("t", 0) > snap.optLong("t", 0)) return;
        prefs(ctx).edit().putString(KEY_SNAPSHOT, snap.toString()).apply();
    }

    static JSONObject config(Context ctx, int widgetId) {
        try {
            String s = prefs(ctx).getString("w_" + widgetId, null);
            return s == null ? defaultConfig() : new JSONObject(s);
        } catch (Exception e) {
            return defaultConfig();
        }
    }

    static void saveConfig(Context ctx, int widgetId, JSONObject cfg) {
        prefs(ctx).edit().putString("w_" + widgetId, cfg.toString()).apply();
    }

    static void deleteConfig(Context ctx, int widgetId) {
        prefs(ctx).edit().remove("w_" + widgetId).apply();
    }

    static JSONObject defaultConfig() {
        try {
            JSONObject c = new JSONObject();
            c.put("type", "cycles");
            c.put("cycles", new JSONArray().put("cetusCycle").put("vallisCycle").put("cambionCycle").put("zarimanCycle"));
            c.put("single", "cetusCycle");
            c.put("resets", new JSONArray().put("daily").put("weekly").put("sortie").put("archon").put("baro"));
            c.put("fisMode", "normal");
            c.put("fisTiers", new JSONArray());
            c.put("fisFast", false);
            c.put("rows", 4);
            c.put("bg", 40);
            c.put("frame", true);
            c.put("size", "m");
            c.put("accent", "gold");
            c.put("title", true);
            return c;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    // ---------------------------------------------------------------- background refresh

    /** Fetch the world state from warframestat.us and turn it into a widget snapshot. Null on failure. */
    static JSONObject fetchSnapshot() {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL("https://api.warframestat.us/pc?language=en").openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) OrbiterPanel-Widget");
            if (c.getResponseCode() != 200) return null;
            InputStream in = c.getInputStream();
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            in.close();
            return fromWorldState(new JSONObject(b.toString("UTF-8")));
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static long iso(String s) {
        if (s == null || s.isEmpty()) return 0;
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            return f.parse(s.length() >= 19 ? s.substring(0, 19) : s).getTime();
        } catch (Exception e) {
            return 0;
        }
    }

    /** Same snapshot shape the web app sends through OrbiterApp.saveWidgetSnapshot. */
    static JSONObject fromWorldState(JSONObject ws) throws Exception {
        JSONObject snap = new JSONObject();
        snap.put("t", System.currentTimeMillis());
        JSONObject cycles = new JSONObject();
        for (Object[] d : CYCLES) {
            JSONObject c = ws.optJSONObject((String) d[0]);
            if (c == null) continue;
            cycles.put((String) d[0], new JSONObject().put("state", c.optString("state")).put("end", iso(c.optString("expiry"))));
        }
        snap.put("cycles", cycles);
        JSONObject sortie = ws.optJSONObject("sortie");
        if (sortie != null) snap.put("sortieEnd", iso(sortie.optString("expiry")));
        JSONObject archon = ws.optJSONObject("archonHunt");
        if (archon != null) {
            snap.put("archonEnd", iso(archon.optString("expiry")));
            snap.put("archonBoss", archon.optString("boss"));
        }
        JSONObject baro = ws.optJSONObject("voidTrader");
        if (baro != null) {
            snap.put("baroStart", iso(baro.optString("activation")));
            snap.put("baroEnd", iso(baro.optString("expiry")));
            snap.put("baroLoc", baro.optString("location"));
        }
        JSONArray out = new JSONArray();
        JSONArray fis = ws.optJSONArray("fissures");
        if (fis != null) for (int i = 0; i < fis.length(); i++) {
            JSONObject f = fis.getJSONObject(i);
            out.put(new JSONObject()
                    .put("tier", f.optString("tier")).put("n", f.optInt("tierNum", 9))
                    .put("type", f.optString("missionType")).put("node", f.optString("node"))
                    .put("enemy", f.optString("enemy")).put("end", iso(f.optString("expiry")))
                    .put("storm", f.optBoolean("isStorm")).put("hard", f.optBoolean("isHard")));
        }
        snap.put("fissures", out);
        return snap;
    }

    static List<String> list(JSONArray a) {
        List<String> l = new ArrayList<>();
        if (a != null) for (int i = 0; i < a.length(); i++) l.add(a.optString(i));
        return l;
    }

    private WidgetData() { }
}
