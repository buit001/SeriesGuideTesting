
package com.battlelancer.seriesguide.sync;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import com.battlelancer.seriesguide.SgApp;
import timber.log.Timber;

/**
 * {@link Service} which executes a {@link SgSyncAdapter} to sync the show
 * database.
 */
public class SgSyncService extends Service {

    private static final Object syncAdapterLock = new Object();
    private static SgSyncAdapter syncAdapter = null;

    @Override
    public void onCreate() {
        Timber.d("Creating sync service");
        synchronized (syncAdapterLock) {
            if (syncAdapter == null) {
                syncAdapter = new SgSyncAdapter((SgApp) getApplication(), true);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Timber.d("Binding sync adapter");
        return syncAdapter.getSyncAdapterBinder();
    }

}
