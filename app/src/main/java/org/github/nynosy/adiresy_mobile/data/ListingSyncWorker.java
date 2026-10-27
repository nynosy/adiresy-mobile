package org.github.nynosy.adiresy_mobile.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

/**
 * Background sync of the directory (listings + categories) into Room, via
 * {@link AdiresyRepository#syncListingsBlocking()}. Unlike the offline map
 * tile downloads (tens to hundreds of MB, Wi-Fi-only by default per the
 * README's design constraints), a delta sync of listing JSON is small enough
 * that gating it to Wi-Fi isn't worth the staleness it would add — it runs on
 * any connected network.
 */
public class ListingSyncWorker extends Worker {

    private static final String UNIQUE_PERIODIC_NAME = "listing_sync_periodic";
    private static final String UNIQUE_IMMEDIATE_NAME = "listing_sync_immediate";
    private static final long PERIODIC_INTERVAL_HOURS = 12;

    public ListingSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        boolean ok = AdiresyRepository.getInstance(getApplicationContext())
                .syncListingsBlocking();
        return ok ? Result.success() : Result.failure();
    }

    /** Call once at app startup (see AdiresyApplication). Enqueues an immediate
     *  one-off sync (so a fresh install/update doesn't wait for the first
     *  periodic window, which WorkManager doesn't guarantee runs right away)
     *  plus the ongoing periodic job. Both use KEEP policies, so calling this
     *  on every app launch doesn't pile up duplicate work. */
    public static void scheduleImmediateAndPeriodic(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        WorkManager wm = WorkManager.getInstance(context);

        wm.enqueueUniqueWork(
                UNIQUE_IMMEDIATE_NAME,
                ExistingWorkPolicy.KEEP,
                new OneTimeWorkRequest.Builder(ListingSyncWorker.class)
                        .setConstraints(constraints)
                        .build());

        wm.enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                new PeriodicWorkRequest.Builder(
                        ListingSyncWorker.class, PERIODIC_INTERVAL_HOURS, TimeUnit.HOURS)
                        .setConstraints(constraints)
                        .build());
    }
}
