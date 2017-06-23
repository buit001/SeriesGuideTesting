package com.battlelancer.seriesguide.util.tasks;

import android.content.Context;
import android.support.annotation.NonNull;
import com.battlelancer.seriesguide.R;
import com.battlelancer.seriesguide.SgApp;
import com.battlelancer.seriesguide.util.MovieTools;
import com.uwetrottmann.seriesguide.backend.movies.model.Movie;
import com.uwetrottmann.trakt5.entities.SyncItems;
import com.uwetrottmann.trakt5.entities.SyncResponse;
import com.uwetrottmann.trakt5.services.Sync;
import retrofit2.Call;

public class SetMovieUnwatchedTask extends BaseMovieActionTask {

    public SetMovieUnwatchedTask(SgApp app, int movieTmdbId) {
        super(app, movieTmdbId);
    }

    @Override
    protected int getSuccessTextResId() {
        return R.string.action_unwatched;
    }

    @Override
    protected boolean isSendingToHexagon() {
        return false;
    }

    @Override
    protected boolean doDatabaseUpdate(Context context, int movieTmdbId) {
        return MovieTools.setWatchedFlag(context, movieTmdbId, false);
    }

    @Override
    protected void setHexagonMovieProperties(Movie movie) {
        // do nothing
    }

    @NonNull
    @Override
    protected String getTraktAction() {
        return "set movie not watched";
    }

    @NonNull
    @Override
    protected Call<SyncResponse> doTraktAction(Sync traktSync, SyncItems items) {
        return traktSync.deleteItemsFromWatchedHistory(items);
    }
}
