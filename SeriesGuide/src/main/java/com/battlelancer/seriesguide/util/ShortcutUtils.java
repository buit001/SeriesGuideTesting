package com.battlelancer.seriesguide.util;

import android.content.Context;
import android.content.Intent;
import android.content.Intent.ShortcutIconResource;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.v4.os.AsyncTaskCompat;
import com.battlelancer.seriesguide.R;
import com.battlelancer.seriesguide.thetvdbapi.TvdbImageTools;
import com.battlelancer.seriesguide.ui.OverviewActivity;
import com.squareup.picasso.MemoryPolicy;
import com.squareup.picasso.NetworkPolicy;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Transformation;
import java.io.IOException;
import timber.log.Timber;

import static android.graphics.Shader.TileMode;

/**
 * Helpers for creating launcher shortcuts
 */
public final class ShortcutUtils {

    /** {@link Intent} action used to create the shortcut */
    private static final String ACTION_INSTALL_SHORTCUT
            = "com.android.launcher.action.INSTALL_SHORTCUT";

    /** This class is never initialized */
    private ShortcutUtils() {
    }

    /**
     * Adds a shortcut from the overview page of the given show to the Home screen.
     *
     * @param showTitle The name of the shortcut.
     * @param posterPath A TVDb show poster path.
     * @param showTvdbId The TVDb ID of the show.
     */
    public static void createShortcut(Context localContext, final String showTitle,
            final String posterPath, final int showTvdbId) {
        // do not pass activity reference to AsyncTask, activity might leak if destroyed
        final Context context = localContext.getApplicationContext();

        AsyncTask<Void, Void, Intent> shortCutTask = new AsyncTask<Void, Void, Intent>() {

            @Override
            protected Intent doInBackground(Void... unused) {
                // Try to get the show poster
                Bitmap posterBitmap = null;

                try {
                    final String posterUrl = TvdbImageTools.smallSizeUrl(posterPath);
                    if (posterUrl != null) {
                        posterBitmap = Picasso.with(context)
                                .load(posterUrl)
                                .centerCrop()
                                .memoryPolicy(MemoryPolicy.NO_STORE)
                                .networkPolicy(NetworkPolicy.NO_STORE)
                                .resizeDimen(R.dimen.show_poster_width_default,
                                        R.dimen.show_poster_height_default)
                                .transform(new RoundedCornerTransformation(posterUrl, 10f))
                                .get();
                    }
                } catch (IOException e) {
                    Timber.e(e, "Could not load show poster for shortcut %s", posterPath);
                    posterBitmap = null;
                }

                // Intent used when the icon is touched
                final Intent shortcutIntent = new Intent(context, OverviewActivity.class);
                shortcutIntent.putExtra(OverviewActivity.EXTRA_INT_SHOW_TVDBID, showTvdbId);
                shortcutIntent.setAction(Intent.ACTION_MAIN);
                shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                // Intent that actually creates the shortcut
                final Intent intent = new Intent();
                intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
                intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, showTitle);
                if (posterBitmap == null) {
                    // Fall back to the app icon
                    intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                            ShortcutIconResource.fromContext(context, R.drawable.ic_launcher));
                } else {
                    intent.putExtra(Intent.EXTRA_SHORTCUT_ICON, posterBitmap);
                }
                intent.setAction(ACTION_INSTALL_SHORTCUT);
                context.sendBroadcast(intent);

                return null;
            }
        };
        // Do all the above async
        AsyncTaskCompat.executeParallel(shortCutTask);
    }

    /** A {@link Transformation} used to draw a {@link Bitmap} with round corners */
    private static final class RoundedCornerTransformation implements Transformation {

        /** A key used to uniquely identify this {@link Transformation} */
        private final String mKey;
        /** The corner radius */
        private final float mRadius;

        /** Constructor for {@code RoundedCornerTransformation} */
        private RoundedCornerTransformation(@NonNull String key, float radius) {
            mKey = key;
            mRadius = radius;
        }

        @Override
        public Bitmap transform(Bitmap source) {
            final int w = source.getWidth();
            final int h = source.getHeight();

            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
            p.setShader(new BitmapShader(source, TileMode.CLAMP, TileMode.CLAMP));

            final Bitmap transformed = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(transformed);
            c.drawRoundRect(new RectF(0f, 0f, w, h), mRadius, mRadius, p);

            // Picasso requires the original Bitmap to be recycled if we aren't returning it
            source.recycle();
            //noinspection UnusedAssignment
            source = null;

            // Release any references to avoid memory leaks
            p.setShader(null);
            c.setBitmap(null);
            //noinspection UnusedAssignment
            p = null;
            //noinspection UnusedAssignment
            c = null;
            return transformed;
        }

        @Override
        public String key() {
            return mKey;
        }
    }
}
