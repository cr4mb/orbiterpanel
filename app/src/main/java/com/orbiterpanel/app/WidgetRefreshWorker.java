package com.orbiterpanel.app;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONObject;

/** Runs about every 30 minutes while widgets exist: fetch fresh data, then redraw every widget. */
public class WidgetRefreshWorker extends Worker {
    public WidgetRefreshWorker(@NonNull Context ctx, @NonNull WorkerParameters params) {
        super(ctx, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        JSONObject snap = WidgetData.fetchSnapshot();
        if (snap != null) WidgetData.saveSnapshot(ctx, snap);
        // Redraw either way: cycles and resets roll forward on their own even without new data.
        OrbiterWidget.updateAll(ctx);
        return Result.success();
    }
}
