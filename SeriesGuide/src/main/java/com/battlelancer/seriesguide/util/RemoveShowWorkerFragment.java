package com.battlelancer.seriesguide.util;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.widget.Toast;
import com.battlelancer.seriesguide.R;
import com.battlelancer.seriesguide.SgApp;
import com.battlelancer.seriesguide.enums.NetworkResult;
import org.greenrobot.eventbus.EventBus;

/**
 * Worker {@link android.support.v4.app.Fragment} hosting an {@link android.os.AsyncTask} that
 * removes a show from the database.
 */
public class RemoveShowWorkerFragment extends Fragment {

    public static final String TAG = "RemoveShowWorkerFragment";

    private static final String KEY_SHOW_TVDBID = "show_tvdb_id";

    public static RemoveShowWorkerFragment newInstance(int showTvdbId) {
        RemoveShowWorkerFragment f = new RemoveShowWorkerFragment();

        Bundle args = new Bundle();
        args.putInt(KEY_SHOW_TVDBID, showTvdbId);
        f.setArguments(args);

        return f;
    }

    /**
     * Posted if a show is about to get removed.
     */
    public static class OnRemovingShowEvent {
        public final int showTvdbId;

        public OnRemovingShowEvent(int showTvdbId) {
            this.showTvdbId = showTvdbId;
        }
    }

    /**
     * Posted if show was just removed (or failure).
     */
    public static class OnShowRemovedEvent {
        public final int showTvdbId;
        /** One of {@link com.battlelancer.seriesguide.enums.NetworkResult}. */
        public final int resultCode;

        public OnShowRemovedEvent(int showTvdbId, int resultCode) {
            this.showTvdbId = showTvdbId;
            this.resultCode = resultCode;
        }
    }

    private RemoveShowTask mTask;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // do not overwrite existing task
        if (mTask == null) {
            mTask = new RemoveShowTask(SgApp.from(getActivity()),
                    getArguments().getInt(KEY_SHOW_TVDBID));
            Utils.executeInOrder(mTask);
        }
    }

    public boolean isTaskFinished() {
        return mTask == null || mTask.getStatus() == AsyncTask.Status.FINISHED;
    }

    private static class RemoveShowTask extends AsyncTask<Integer, Void, Integer> {

        private final SgApp app;
        private final int showTvdbId;

        public RemoveShowTask(SgApp app, int showTvdbId) {
            this.app = app;
            this.showTvdbId = showTvdbId;
        }

        @Override
        protected void onPreExecute() {
            EventBus.getDefault().post(new OnRemovingShowEvent(showTvdbId));
        }

        @Override
        protected Integer doInBackground(Integer... params) {
            return app.getShowTools().removeShow(showTvdbId);
        }

        @Override
        protected void onPostExecute(Integer result) {
            if (result == NetworkResult.OFFLINE) {
                Toast.makeText(app, R.string.offline, Toast.LENGTH_LONG).show();
            } else if (result == NetworkResult.ERROR) {
                Toast.makeText(app, R.string.delete_error, Toast.LENGTH_LONG).show();
            }

            EventBus.getDefault().post(new OnShowRemovedEvent(showTvdbId, result));
        }
    }
}
