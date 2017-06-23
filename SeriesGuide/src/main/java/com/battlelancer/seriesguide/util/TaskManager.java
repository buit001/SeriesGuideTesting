package com.battlelancer.seriesguide.util;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.os.AsyncTaskCompat;
import android.widget.Toast;
import com.battlelancer.seriesguide.R;
import com.battlelancer.seriesguide.SgApp;
import com.battlelancer.seriesguide.dataliberation.JsonExportTask;
import com.battlelancer.seriesguide.items.SearchResult;
import java.util.ArrayList;
import java.util.List;

/**
 * Inspired by florianmski's traktoid TraktManager. This class is used to hold running tasks, so it
 * can execute independently from a running activity (so the application can still be used while
 * the
 * update continues). A plain AsyncTask could do this, too, but here we can also restrict it to one
 * task running at a time.
 */
public class TaskManager {

    private static TaskManager _instance;

    private Handler mHandler = new Handler(Looper.getMainLooper());

    private AddShowTask mAddTask;

    private JsonExportTask mBackupTask;

    private LatestEpisodeUpdateTask mNextEpisodeUpdateTask;

    private Context mContext;

    private TaskManager(Context context) {
        mContext = context.getApplicationContext();
    }

    public static synchronized TaskManager getInstance(Context context) {
        if (_instance == null) {
            _instance = new TaskManager(context);
        }
        return _instance;
    }

    public synchronized void performAddTask(SgApp app, SearchResult show) {
        List<SearchResult> wrapper = new ArrayList<>();
        wrapper.add(show);
        performAddTask(app, wrapper, false, false);
    }

    /**
     * Schedule shows to be added to the database.
     *
     * @param isSilentMode   Whether to display status toasts if a show could not be added.
     * @param isMergingShows Whether to set the Hexagon show merged flag to true if all shows were
     */
    public synchronized void performAddTask(final SgApp app, final List<SearchResult> shows,
            final boolean isSilentMode, final boolean isMergingShows) {
        if (!isSilentMode) {
            // notify user here already
            if (shows.size() == 1) {
                // say title of show
                SearchResult show = shows.get(0);
                Toast.makeText(mContext,
                        mContext.getString(R.string.add_started, show.title),
                        Toast.LENGTH_SHORT).show();
            } else {
                // generic adding multiple message
                Toast.makeText(
                        mContext,
                        R.string.add_multiple,
                        Toast.LENGTH_SHORT).show();
            }
        }

        // add the show(s) to a running add task or create a new one
        if (!isAddTaskRunning() || !mAddTask.addShows(shows, isSilentMode, isMergingShows)) {
            // ensure this is called on our main thread (AsyncTask needs access to it)
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mAddTask = (AddShowTask) Utils.executeInOrder(
                            new AddShowTask(app, shows, isSilentMode, isMergingShows));
                }
            });
        }
    }

    public boolean isAddTaskRunning() {
        return !(mAddTask == null || mAddTask.getStatus() == AsyncTask.Status.FINISHED);
    }

    /**
     * If no {@link AddShowTask} or {@link JsonExportTask} created by this {@link
     * com.battlelancer.seriesguide.util.TaskManager} is running a
     * {@link JsonExportTask} is scheduled in silent mode.
     */
    public synchronized void tryBackupTask() {
        if (!isAddTaskRunning()
                && (mBackupTask == null || mBackupTask.getStatus() == AsyncTask.Status.FINISHED)) {
            mBackupTask = new JsonExportTask(mContext, null, false, true);
            AsyncTaskCompat.executeParallel(mBackupTask);
        }
    }

    /**
     * Schedules a {@link com.battlelancer.seriesguide.util.LatestEpisodeUpdateTask} for all shows
     * if no other one of this type is currently running.
     */
    public synchronized void tryNextEpisodeUpdateTask() {
        if (mNextEpisodeUpdateTask == null
                || mNextEpisodeUpdateTask.getStatus() == AsyncTask.Status.FINISHED) {
            mNextEpisodeUpdateTask = new LatestEpisodeUpdateTask(mContext);
            AsyncTaskCompat.executeParallel(mNextEpisodeUpdateTask);
        }
    }
}
