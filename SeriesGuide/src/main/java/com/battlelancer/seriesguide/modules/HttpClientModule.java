package com.battlelancer.seriesguide.modules;

import android.app.Application;
import android.os.Build;
import android.os.StatFs;
import com.battlelancer.seriesguide.SgApp;
import com.battlelancer.seriesguide.thetvdbapi.SgTheTvdbInterceptor;
import com.battlelancer.seriesguide.tmdbapi.SgTmdbInterceptor;
import com.battlelancer.seriesguide.traktapi.SgTraktInterceptor;
import com.battlelancer.seriesguide.util.AllApisAuthenticator;
import dagger.Module;
import dagger.Provides;
import java.io.File;
import java.util.concurrent.TimeUnit;
import javax.inject.Singleton;
import okhttp3.Cache;
import okhttp3.OkHttpClient;

@Module
public class HttpClientModule {

    private static final int CONNECT_TIMEOUT_MILLIS = 15 * 1000; // 15s
    private static final int READ_TIMEOUT_MILLIS = 20 * 1000; // 20s

    private static final String API_CACHE = "api-cache";
    private static final int MIN_DISK_API_CACHE_SIZE = 2 * 1024 * 1024; // 2MB
    private static final int MAX_DISK_API_CACHE_SIZE = 20 * 1024 * 1024; // 20MB

    /**
     * Returns this apps {@link OkHttpClient} with enabled response cache. Should be used with API
     * calls.
     */
    @Provides
    @Singleton
    OkHttpClient provideOkHttpClient(SgApp app, Cache cache) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.connectTimeout(CONNECT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        builder.readTimeout(READ_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        builder.addInterceptor(new SgTmdbInterceptor());
        builder.addNetworkInterceptor(new SgTheTvdbInterceptor(app));
        builder.addNetworkInterceptor(new SgTraktInterceptor(app));
        builder.authenticator(new AllApisAuthenticator(app));
        builder.cache(cache);
        return builder.build();
    }

    @Provides
    @Singleton
    Cache provideOkHttpCache(Application application) {
        File cacheDir = createApiCacheDir(application, API_CACHE);
        return new Cache(cacheDir, calculateApiDiskCacheSize(cacheDir));
    }

    private static File createApiCacheDir(Application application, String directoryName) {
        File cache = new File(application.getCacheDir(), directoryName);
        if (!cache.exists()) {
            cache.mkdirs();
        }
        return cache;
    }

    private static long calculateApiDiskCacheSize(File dir) {
        long size = MIN_DISK_API_CACHE_SIZE;

        try {
            StatFs statFs = new StatFs(dir.getAbsolutePath());
            long available;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                available = statFs.getBlockCountLong() * statFs.getBlockSizeLong();
            } else {
                //noinspection deprecation
                available = ((long) statFs.getBlockCount()) * statFs.getBlockSize();
            }
            // Target 2% of the total space.
            size = available / 50;
        } catch (IllegalArgumentException ignored) {
        }

        // Bound inside min/max size for disk cache.
        return Math.max(Math.min(size, MAX_DISK_API_CACHE_SIZE), MIN_DISK_API_CACHE_SIZE);
    }
}
