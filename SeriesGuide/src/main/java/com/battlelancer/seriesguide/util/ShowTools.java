package com.battlelancer.seriesguide.util;

import android.annotation.SuppressLint;
import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.os.AsyncTaskCompat;
import android.support.v4.util.SparseArrayCompat;
import android.text.TextUtils;
import android.widget.TextView;
import android.widget.Toast;
import com.battlelancer.seriesguide.R;
import com.battlelancer.seriesguide.SgApp;
import com.battlelancer.seriesguide.backend.HexagonTools;
import com.battlelancer.seriesguide.backend.settings.HexagonSettings;
import com.battlelancer.seriesguide.enums.NetworkResult;
import com.battlelancer.seriesguide.enums.Result;
import com.battlelancer.seriesguide.items.SearchResult;
import com.battlelancer.seriesguide.provider.SeriesGuideContract;
import com.battlelancer.seriesguide.sync.SgSyncAdapter;
import com.battlelancer.seriesguide.util.tasks.AddShowToWatchlistTask;
import com.battlelancer.seriesguide.util.tasks.RemoveShowFromWatchlistTask;
import com.google.api.client.util.DateTime;
import com.uwetrottmann.androidutils.AndroidUtils;
import com.uwetrottmann.seriesguide.backend.shows.Shows;
import com.uwetrottmann.seriesguide.backend.shows.model.Show;
import com.uwetrottmann.seriesguide.backend.shows.model.ShowList;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import timber.log.Timber;

/**
 * Common activities and tools useful when interacting with shows.
 */
public class ShowTools {

    public static class ShowChangedEvent {
        public int showTvdbId;

        public ShowChangedEvent(int showTvdbId) {
            this.showTvdbId = showTvdbId;
        }
    }

    /**
     * Show status valued as stored in the database in {@link com.battlelancer.seriesguide.provider.SeriesGuideContract.Shows#STATUS}.
     */
    public interface Status {
        int CONTINUING = 1;
        int ENDED = 0;
        int UNKNOWN = -1;
    }

    private final SgApp app;

    public ShowTools(SgApp app) {
        this.app = app;
    }

    /**
     * Removes a show and its seasons and episodes, including all images. Sends isRemoved flag to
     * Hexagon.
     *
     * @return One of {@link com.battlelancer.seriesguide.enums.NetworkResult}.
     */
    public int removeShow(int showTvdbId) {
        if (HexagonSettings.isEnabled(app)) {
            if (!AndroidUtils.isNetworkConnected(app)) {
                return NetworkResult.OFFLINE;
            }
            // send to cloud
            sendIsRemoved(showTvdbId);
        }

        // remove database entries in stages, so if an earlier stage fails, user can at least try again
        // also saves memory by applying batches early

        // SEARCH DATABASE ENTRIES
        final Cursor episodes = app.getContentResolver().query(
                SeriesGuideContract.Episodes.buildEpisodesOfShowUri(showTvdbId), new String[] {
                        SeriesGuideContract.Episodes._ID
                }, null, null, null
        );
        if (episodes == null) {
            // failed
            return Result.ERROR;
        }
        List<String> episodeTvdbIds = new LinkedList<>(); // need those for search entries
        while (episodes.moveToNext()) {
            episodeTvdbIds.add(episodes.getString(0));
        }
        episodes.close();

        // remove episode search database entries
        final ArrayList<ContentProviderOperation> batch = new ArrayList<>();
        for (String episodeTvdbId : episodeTvdbIds) {
            batch.add(ContentProviderOperation.newDelete(
                    SeriesGuideContract.EpisodeSearch.buildDocIdUri(episodeTvdbId)).build());
        }
        try {
            DBUtils.applyInSmallBatches(app, batch);
        } catch (OperationApplicationException e) {
            Timber.e(e, "Removing episode search entries failed");
            return Result.ERROR;
        }
        batch.clear();

        // ACTUAL ENTITY ENTRIES
        // remove episodes, seasons and show
        batch.add(ContentProviderOperation.newDelete(
                SeriesGuideContract.Episodes.buildEpisodesOfShowUri(showTvdbId)).build());
        batch.add(ContentProviderOperation.newDelete(
                SeriesGuideContract.Seasons.buildSeasonsOfShowUri(showTvdbId)).build());
        batch.add(ContentProviderOperation.newDelete(
                SeriesGuideContract.Shows.buildShowUri(showTvdbId)).build());
        try {
            DBUtils.applyInSmallBatches(app, batch);
        } catch (OperationApplicationException e) {
            Timber.e(e, "Removing episodes, seasons and show failed");
            return Result.ERROR;
        }

        // make sure other loaders (activity, overview, details, search) are notified
        app.getContentResolver().notifyChange(
                SeriesGuideContract.Episodes.CONTENT_URI_WITHSHOW, null);
        app.getContentResolver().notifyChange(
                SeriesGuideContract.Shows.CONTENT_URI_FILTER, null);

        return Result.SUCCESS;
    }

    /**
     * Adds the show on Hexagon. Or if it does already exist, clears the isRemoved flag and updates
     * the language, so the show will be auto-added on other connected devices.
     */
    public void sendIsAdded(int showTvdbId, @NonNull String language) {
        Show show = new Show();
        show.setTvdbId(showTvdbId);
        show.setLanguage(language);
        show.setIsRemoved(false);
        uploadShowAsync(show);
    }

    /**
     * Sets the isRemoved flag of the given show on Hexagon, so the show will not be auto-added on
     * any device connected to Hexagon.
     */
    public void sendIsRemoved(int showTvdbId) {
        Show show = new Show();
        show.setTvdbId(showTvdbId);
        show.setIsRemoved(true);
        uploadShowAsync(show);
    }

    /**
     * Saves new favorite flag to the local database and, if signed in, up into the cloud as well.
     */
    public void storeIsFavorite(int showTvdbId, boolean isFavorite) {
        if (HexagonSettings.isEnabled(app)) {
            if (Utils.isNotConnected(app, true)) {
                return;
            }
            // send to cloud
            Show show = new Show();
            show.setTvdbId(showTvdbId);
            show.setIsFavorite(isFavorite);
            uploadShowAsync(show);
        }

        // save to local database
        ContentValues values = new ContentValues();
        values.put(SeriesGuideContract.Shows.FAVORITE, isFavorite);
        app.getContentResolver().update(
                SeriesGuideContract.Shows.buildShowUri(showTvdbId), values, null, null);

        // also notify URIs used by search and lists
        app.getContentResolver()
                .notifyChange(SeriesGuideContract.Shows.CONTENT_URI_FILTER, null);
        app.getContentResolver()
                .notifyChange(SeriesGuideContract.ListItems.CONTENT_WITH_DETAILS_URI, null);

        // favorite status may determine eligibility for notifications
        Utils.runNotificationService(app);

        Toast.makeText(app, app.getString(isFavorite ?
                R.string.favorited : R.string.unfavorited), Toast.LENGTH_SHORT).show();
    }

    /**
     * Saves new hidden flag to the local database and, if signed in, up into the cloud as well.
     */
    public void storeIsHidden(int showTvdbId, boolean isHidden) {
        if (HexagonSettings.isEnabled(app)) {
            if (Utils.isNotConnected(app, true)) {
                return;
            }
            // send to cloud
            Show show = new Show();
            show.setTvdbId(showTvdbId);
            show.setIsHidden(isHidden);
            uploadShowAsync(show);
        }

        // save to local database
        ContentValues values = new ContentValues();
        values.put(SeriesGuideContract.Shows.HIDDEN, isHidden);
        app.getContentResolver().update(
                SeriesGuideContract.Shows.buildShowUri(showTvdbId), values, null, null);

        // also notify filter URI used by search
        app.getContentResolver()
                .notifyChange(SeriesGuideContract.Shows.CONTENT_URI_FILTER, null);

        Toast.makeText(app, app.getString(isHidden ?
                R.string.hidden : R.string.unhidden), Toast.LENGTH_SHORT).show();
    }

    public void storeLanguage(final int showTvdbId, final String languageCode) {
        if (HexagonSettings.isEnabled(app)) {
            if (Utils.isNotConnected(app, true)) {
                return;
            }
            // send to cloud
            Show show = new Show();
            show.setTvdbId(showTvdbId);
            show.setLanguage(languageCode);
            uploadShowAsync(show);
        }

        // schedule database update and sync
        Runnable runnable = new Runnable() {
            public void run() {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);

                // change language
                ContentValues values = new ContentValues();
                values.put(SeriesGuideContract.Shows.LANGUAGE, languageCode);
                app.getContentResolver()
                        .update(SeriesGuideContract.Shows.buildShowUri(showTvdbId), values, null,
                                null);
                // reset episode last edit time so all get updated
                values = new ContentValues();
                values.put(SeriesGuideContract.Episodes.LAST_EDITED, 0);
                app.getContentResolver()
                        .update(SeriesGuideContract.Episodes.buildEpisodesOfShowUri(showTvdbId),
                                values, null, null);
                // trigger update
                SgSyncAdapter.requestSyncImmediate(app, SgSyncAdapter.SyncType.SINGLE,
                        showTvdbId, false);
            }
        };
        AsyncTask.THREAD_POOL_EXECUTOR.execute(runnable);

        // show immediate feedback, also if offline and sync won't go through
        if (AndroidUtils.isNetworkConnected(app)) {
            // notify about upcoming sync
            Toast.makeText(app, R.string.update_scheduled, Toast.LENGTH_SHORT).show();
        } else {
            // offline
            Toast.makeText(app, R.string.update_no_connection, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Saves new notify flag to the local database and, if signed in, up into the cloud as well.
     */
    public void storeNotify(int showTvdbId, boolean notify) {
        if (HexagonSettings.isEnabled(app)) {
            if (Utils.isNotConnected(app, true)) {
                return;
            }
            // send to cloud
            Show show = new Show();
            show.setTvdbId(showTvdbId);
            show.setNotify(notify);
            uploadShowAsync(show);
        }

        // save to local database
        ContentValues values = new ContentValues();
        values.put(SeriesGuideContract.Shows.NOTIFY, notify);
        app.getContentResolver().update(
                SeriesGuideContract.Shows.buildShowUri(showTvdbId), values, null, null);

        // new notify setting may determine eligibility for notifications
        Utils.runNotificationService(app);
    }

    /**
     * Add a show to the users trakt watchlist.
     */
    public static void addToWatchlist(SgApp app, int showTvdbId) {
        AsyncTaskCompat.executeParallel(new AddShowToWatchlistTask(app, showTvdbId));
    }

    /**
     * Remove a show from the users trakt watchlist.
     */
    public static void removeFromWatchlist(SgApp app, int showTvdbId) {
        AsyncTaskCompat.executeParallel(new RemoveShowFromWatchlistTask(app, showTvdbId));
    }

    private void uploadShowAsync(Show show) {
        AsyncTaskCompat.executeParallel(
                new ShowsUploadTask(app, show)
        );
    }

    private static class ShowsUploadTask extends AsyncTask<Void, Void, Void> {

        private final SgApp app;

        private final Show mShow;

        public ShowsUploadTask(SgApp app, Show show) {
            this.app = app;
            mShow = show;
        }

        @Override
        protected Void doInBackground(Void... params) {
            List<Show> shows = new LinkedList<>();
            shows.add(mShow);

            Upload.toHexagon(app, shows);

            return null;
        }
    }

    public static class Upload {

        /**
         * Uploads all local shows to Hexagon.
         */
        public static boolean toHexagon(SgApp app) {
            Timber.d("toHexagon: uploading all shows");
            List<Show> shows = buildShowList(app);
            if (shows == null) {
                Timber.e("toHexagon: show query was null");
                return false;
            }
            if (shows.size() == 0) {
                Timber.d("toHexagon: no shows to upload");
                // nothing to upload
                return true;
            }
            return toHexagon(app, shows);
        }

        /**
         * Uploads the given list of shows to Hexagon.
         *
         * @return One of {@link com.battlelancer.seriesguide.enums.Result}.
         */
        public static boolean toHexagon(SgApp app, List<Show> shows) {
            // wrap into helper object
            ShowList showList = new ShowList();
            showList.setShows(shows);

            // upload shows
            try {
                Shows showsService = app.getHexagonTools().getShowsService();
                if (showsService == null) {
                    return false;
                }
                showsService.save(showList).execute();
            } catch (IOException e) {
                HexagonTools.trackFailedRequest(app, "save shows", e);
                return false;
            }

            return true;
        }

        private static List<Show> buildShowList(Context context) {
            List<Show> shows = new LinkedList<>();

            Cursor query = context.getContentResolver()
                    .query(SeriesGuideContract.Shows.CONTENT_URI, new String[] {
                            SeriesGuideContract.Shows._ID, // 0
                            SeriesGuideContract.Shows.FAVORITE, // 1
                            SeriesGuideContract.Shows.NOTIFY, // 2
                            SeriesGuideContract.Shows.HIDDEN, // 3
                            SeriesGuideContract.Shows.LANGUAGE // 4
                    }, null, null, null);
            if (query == null) {
                return null;
            }

            while (query.moveToNext()) {
                Show show = new Show();
                show.setTvdbId(query.getInt(0));
                show.setIsFavorite(query.getInt(1) == 1);
                show.setNotify(query.getInt(2) == 1);
                show.setIsHidden(query.getInt(3) == 1);
                show.setLanguage(query.getString(4));
                shows.add(show);
            }

            query.close();

            return shows;
        }
    }

    public static class Download {

        /**
         * Downloads shows from Hexagon and updates existing shows with new property values. Any
         * shows not yet in the local database, determined by the given TVDb id set, will be added
         * to the given map.
         */
        @SuppressLint("ApplySharedPref")
        public static boolean fromHexagon(SgApp app, HashSet<Integer> existingShows,
                HashMap<Integer, SearchResult> newShows, boolean hasMergedShows) {
            List<Show> shows;
            boolean hasMoreShows = true;
            String cursor = null;
            long currentTime = System.currentTimeMillis();
            DateTime lastSyncTime = new DateTime(HexagonSettings.getLastShowsSyncTime(app));

            if (hasMergedShows) {
                Timber.d("fromHexagon: downloading changed shows since %s", lastSyncTime);
            } else {
                Timber.d("fromHexagon: downloading all shows");
            }

            while (hasMoreShows) {
                // abort if connection is lost
                if (!AndroidUtils.isNetworkConnected(app)) {
                    Timber.e("fromHexagon: no network connection");
                    return false;
                }

                try {
                    Shows showsService = app.getHexagonTools().getShowsService();
                    if (showsService == null) {
                        return false;
                    }

                    Shows.Get request = showsService.get(); // use default server limit
                    if (hasMergedShows) {
                        // only get changed shows (otherwise returns all)
                        request.setUpdatedSince(lastSyncTime);
                    }
                    if (!TextUtils.isEmpty(cursor)) {
                        request.setCursor(cursor);
                    }

                    ShowList response = request.execute();
                    if (response == null) {
                        // we're done
                        Timber.d("fromHexagon: response was null, done here");
                        break;
                    }

                    shows = response.getShows();

                    // check for more items
                    if (response.getCursor() != null) {
                        cursor = response.getCursor();
                    } else {
                        hasMoreShows = false;
                    }
                } catch (IOException e) {
                    HexagonTools.trackFailedRequest(app, "get shows", e);
                    return false;
                }

                if (shows == null || shows.size() == 0) {
                    // nothing to do here
                    break;
                }

                // update all received shows, ContentProvider will ignore those not added locally
                ArrayList<ContentProviderOperation> batch = buildShowUpdateOps(shows, existingShows,
                        newShows, !hasMergedShows);

                try {
                    DBUtils.applyInSmallBatches(app, batch);
                } catch (OperationApplicationException e) {
                    Timber.e(e, "fromHexagon: applying show updates failed");
                    return false;
                }
            }

            if (hasMergedShows) {
                // set new last sync time
                PreferenceManager.getDefaultSharedPreferences(app)
                        .edit()
                        .putLong(HexagonSettings.KEY_LAST_SYNC_SHOWS, currentTime)
                        .commit();
            }

            return true;
        }

        private static ArrayList<ContentProviderOperation> buildShowUpdateOps(List<Show> shows,
                HashSet<Integer> existingShows, HashMap<Integer, SearchResult> newShows,
                boolean mergeValues) {
            ArrayList<ContentProviderOperation> batch = new ArrayList<>();

            ContentValues values = new ContentValues();
            for (Show show : shows) {
                // schedule to add shows not in local database
                if (!existingShows.contains(show.getTvdbId())) {
                    // ...but do NOT add shows marked as removed
                    if (show.getIsRemoved() != null && show.getIsRemoved()) {
                        continue;
                    }

                    if (!newShows.containsKey(show.getTvdbId())) {
                        SearchResult item = new SearchResult();
                        item.tvdbid = show.getTvdbId();
                        item.language = show.getLanguage();
                        item.title = "";
                        newShows.put(show.getTvdbId(), item);
                    }

                    continue;
                }

                buildShowPropertyValues(show, values, mergeValues);

                // build update op
                if (values.size() > 0) {
                    ContentProviderOperation op = ContentProviderOperation
                            .newUpdate(SeriesGuideContract.Shows.buildShowUri(show.getTvdbId()))
                            .withValues(values).build();
                    batch.add(op);

                    // clean up for re-use
                    values.clear();
                }
            }

            return batch;
        }

        /**
         * @param mergeValues If set, only overwrites property if remote show property has a certain
         * value.
         */
        private static void buildShowPropertyValues(Show show, ContentValues values,
                boolean mergeValues) {
            if (show.getIsFavorite() != null) {
                // when merging, favorite shows, but never unfavorite them
                if (!mergeValues || show.getIsFavorite()) {
                    values.put(SeriesGuideContract.Shows.FAVORITE, show.getIsFavorite());
                }
            }
            if (show.getNotify() != null) {
                // when merging, enable notifications, but never disable them
                if (!mergeValues || show.getNotify()) {
                    values.put(SeriesGuideContract.Shows.NOTIFY, show.getNotify());
                }
            }
            if (show.getIsHidden() != null) {
                // when merging, un-hide shows, but never hide them
                if (!mergeValues || !show.getIsHidden()) {
                    values.put(SeriesGuideContract.Shows.HIDDEN, show.getIsHidden());
                }
            }
            if (!TextUtils.isEmpty(show.getLanguage())) {
                // always overwrite with hexagon language value
                values.put(SeriesGuideContract.Shows.LANGUAGE, show.getLanguage());
            }
        }
    }

    public static boolean addLastWatchedUpdateOpIfNewer(Context context,
            ArrayList<ContentProviderOperation> batch, int showTvdbId, long lastWatchedMsNew) {
        Uri uri = SeriesGuideContract.Shows.buildShowUri(showTvdbId);
        Cursor query = context.getContentResolver().query(uri, new String[] {
                SeriesGuideContract.Shows.LASTWATCHED_MS }, null, null, null);
        if (query == null) {
            Timber.e("addLastWatchedTimeUpdateOpIfNewer: query was null.");
            return false;
        }
        if (!query.moveToFirst()) {
            Timber.e("addLastWatchedTimeUpdateOpIfNewer: query has no results.");
            query.close();
            return false;
        }
        long lastWatchedMs = query.getLong(0);
        query.close();

        if (lastWatchedMs < lastWatchedMsNew) {
            batch.add(ContentProviderOperation.newUpdate(uri)
                    .withValue(SeriesGuideContract.Shows.LASTWATCHED_MS, lastWatchedMsNew)
                    .build());
        }
        return true;
    }

    /**
     * Returns the trakt id of a show. Returns {@code null} if the query failed, there is no trakt
     * id or if it is invalid.
     */
    @Nullable
    public static Integer getShowTraktId(@NonNull Context context, int showTvdbId) {
        Cursor traktIdQuery = context.getContentResolver()
                .query(SeriesGuideContract.Shows.buildShowUri(showTvdbId),
                        new String[] { SeriesGuideContract.Shows.TRAKT_ID }, null, null, null);
        if (traktIdQuery == null) {
            return null;
        }

        Integer traktId = null;
        if (traktIdQuery.moveToFirst()) {
            traktId = traktIdQuery.getInt(0);
            if (traktId <= 0) {
                traktId = null;
            }
        }

        traktIdQuery.close();

        return traktId;
    }

    /**
     * Returns a set of the TVDb ids of all shows in the local database.
     *
     * @return null if there was an error, empty list if there are no shows.
     */
    @Nullable
    public static HashSet<Integer> getShowTvdbIdsAsSet(Context context) {
        HashSet<Integer> existingShows = new HashSet<>();

        Cursor shows = context.getContentResolver().query(SeriesGuideContract.Shows.CONTENT_URI,
                new String[] { SeriesGuideContract.Shows._ID }, null, null, null);
        if (shows == null) {
            return null;
        }

        while (shows.moveToNext()) {
            existingShows.add(shows.getInt(0));
        }

        shows.close();

        return existingShows;
    }

    /**
     * Returns a set of the TVDb ids of all shows in the local database mapped to their poster path
     * (null if there is no poster).
     *
     * @return null if there was an error, empty list if there are no shows.
     */
    @Nullable
    public static SparseArrayCompat<String> getShowTvdbIdsAndPosters(Context context) {
        SparseArrayCompat<String> existingShows = new SparseArrayCompat<>();

        Cursor shows = context.getContentResolver().query(SeriesGuideContract.Shows.CONTENT_URI,
                new String[] { SeriesGuideContract.Shows._ID, SeriesGuideContract.Shows.POSTER },
                null, null, null);
        if (shows == null) {
            return null;
        }

        while (shows.moveToNext()) {
            existingShows.put(shows.getInt(0), shows.getString(1));
        }

        shows.close();

        return existingShows;
    }

    /**
     * Decodes the show status and returns the localized text representation. May be {@code null} if
     * status is unknown.
     *
     * @param encodedStatus Detection based on {@link com.battlelancer.seriesguide.util.ShowTools.Status}.
     */
    @Nullable
    public static String getStatus(@NonNull Context context, int encodedStatus) {
        if (encodedStatus == Status.CONTINUING) {
            return context.getString(R.string.show_isalive);
        } else if (encodedStatus == Status.ENDED) {
            return context.getString(R.string.show_isnotalive);
        } else {
            // status unknown, display nothing
            return null;
        }
    }

    /**
     * Gets the show status from {@link #getStatus} and sets a status dependant text color on the
     * given view.
     *
     * @param encodedStatus Detection based on {@link com.battlelancer.seriesguide.util.ShowTools.Status}.
     */
    public static void setStatusAndColor(@NonNull TextView view, int encodedStatus) {
        view.setText(getStatus(view.getContext(), encodedStatus));
        if (encodedStatus == Status.CONTINUING) {
            view.setTextColor(
                    ContextCompat.getColor(view.getContext(), Utils.resolveAttributeToResourceId(
                            view.getContext().getTheme(), R.attr.sgTextColorGreen)));
        } else {
            view.setTextColor(
                    ContextCompat.getColor(view.getContext(), Utils.resolveAttributeToResourceId(
                            view.getContext().getTheme(), android.R.attr.textColorSecondary)));
        }
    }
}
