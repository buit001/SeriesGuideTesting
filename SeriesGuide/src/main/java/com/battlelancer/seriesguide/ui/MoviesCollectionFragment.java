package com.battlelancer.seriesguide.ui;

import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import com.battlelancer.seriesguide.R;
import com.battlelancer.seriesguide.SgApp;
import com.battlelancer.seriesguide.adapters.MoviesCursorAdapter;
import com.battlelancer.seriesguide.settings.MoviesDistillationSettings;
import com.battlelancer.seriesguide.util.MovieTools;

import static com.battlelancer.seriesguide.provider.SeriesGuideContract.Movies;

/**
 * Displays a users collection of movies in a grid.
 */
public class MoviesCollectionFragment extends MoviesBaseFragment {

    private static final int CONTEXT_COLLECTION_REMOVE_ID = 0;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View v = super.onCreateView(inflater, container, savedInstanceState);

        emptyView.setText(R.string.movies_collection_empty);

        return v;
    }

    @Override
    public void onPopupMenuClick(View v, final int movieTmdbId) {
        PopupMenu popupMenu = new PopupMenu(v.getContext(), v);
        popupMenu.getMenu()
                .add(0, CONTEXT_COLLECTION_REMOVE_ID, 0, R.string.action_collection_remove);
        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case CONTEXT_COLLECTION_REMOVE_ID: {
                        MovieTools.removeFromCollection(SgApp.from(getActivity()), movieTmdbId);
                        return true;
                    }
                }
                return false;
            }
        });
        popupMenu.show();
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new CursorLoader(getContext(), Movies.CONTENT_URI,
                MoviesCursorAdapter.MoviesQuery.PROJECTION, Movies.SELECTION_COLLECTION, null,
                MoviesDistillationSettings.getSortQuery(getContext()));
    }

    @Override
    protected int getLoaderId() {
        return MoviesActivity.COLLECTION_LOADER_ID;
    }

    @Override
    protected int getTabPosition(boolean showingNowTab) {
        return showingNowTab
                ? MoviesActivity.TAB_POSITION_COLLECTION_WITH_NOW
                : MoviesActivity.TAB_POSITION_COLLECTION_DEFAULT;
    }
}
