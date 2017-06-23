package com.battlelancer.seriesguide.ui;

import android.app.SearchManager;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.PopupMenu;
import com.battlelancer.seriesguide.R;
import com.battlelancer.seriesguide.SgApp;
import com.battlelancer.seriesguide.adapters.BaseShowsAdapter;
import com.battlelancer.seriesguide.adapters.ShowResultsAdapter;
import com.battlelancer.seriesguide.provider.SeriesGuideContract;
import com.battlelancer.seriesguide.util.ShowMenuItemClickListener;
import com.battlelancer.seriesguide.util.TabClickEvent;
import com.battlelancer.seriesguide.util.TimeTools;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

/**
 * Displays show search results.
 */
public class ShowSearchFragment extends ListFragment {

    private ShowResultsAdapter adapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_search, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        adapter = new ShowResultsAdapter(getActivity(), onContextMenuClickListener);
        setListAdapter(adapter);

        // initially display shows with recently released episodes
        getLoaderManager().initLoader(SearchActivity.SHOWS_LOADER_ID, new Bundle(),
                searchLoaderCallbacks);
    }

    @Override
    public void onStart() {
        super.onStart();

        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        super.onStop();

        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        Intent i = new Intent(getActivity(), OverviewActivity.class);
        i.putExtra(OverviewActivity.EXTRA_INT_SHOW_TVDBID, (int) id);

        ActivityCompat.startActivity(getActivity(), i,
                ActivityOptionsCompat.makeScaleUpAnimation(v, 0, 0, v.getWidth(), v.getHeight())
                        .toBundle());
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onEventMainThread(SearchActivity.SearchQueryEvent event) {
        search(event.args);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventTabClick(TabClickEvent event) {
        if (event.position == SearchActivity.TAB_POSITION_SHOWS) {
            getListView().smoothScrollToPosition(0);
        }
    }

    public void search(Bundle args) {
        getLoaderManager().restartLoader(SearchActivity.SHOWS_LOADER_ID, args,
                searchLoaderCallbacks);
    }

    private LoaderManager.LoaderCallbacks<Cursor>
            searchLoaderCallbacks = new LoaderManager.LoaderCallbacks<Cursor>() {
        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            String query = args.getString(SearchManager.QUERY);
            if (TextUtils.isEmpty(query)) {
                // empty query selects shows with next episodes before this point in time
                String customTimeInOneHour = String.valueOf(TimeTools.getCurrentTime(getActivity())
                        + DateUtils.HOUR_IN_MILLIS);
                return new CursorLoader(getActivity(), SeriesGuideContract.Shows.CONTENT_URI,
                        ShowResultsAdapter.Query.PROJECTION,
                        SeriesGuideContract.Shows.NEXTEPISODE + "!='' AND "
                                + SeriesGuideContract.Shows.HIDDEN + "=0 AND "
                                + SeriesGuideContract.Shows.NEXTAIRDATEMS + "<?",
                        new String[] { customTimeInOneHour },
                        SeriesGuideContract.Shows.SORT_LATEST_EPISODE);
            } else {
                Uri uri = SeriesGuideContract.Shows.CONTENT_URI_FILTER.buildUpon()
                        .appendPath(query)
                        .build();
                return new CursorLoader(getActivity(), uri,
                        ShowResultsAdapter.Query.PROJECTION, null, null, null);
            }
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            adapter.swapCursor(data);
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
            adapter.swapCursor(null);
        }
    };

    private ShowResultsAdapter.OnContextMenuClickListener onContextMenuClickListener
            = new ShowResultsAdapter.OnContextMenuClickListener() {
        @Override
        public void onClick(View view, BaseShowsAdapter.ShowViewHolder viewHolder) {
            PopupMenu popupMenu = new PopupMenu(view.getContext(), view);
            popupMenu.inflate(R.menu.shows_popup_menu);

            // show/hide some menu items depending on show properties
            Menu menu = popupMenu.getMenu();
            menu.findItem(R.id.menu_action_shows_favorites_add)
                    .setVisible(!viewHolder.isFavorited);
            menu.findItem(R.id.menu_action_shows_favorites_remove)
                    .setVisible(viewHolder.isFavorited);
            menu.findItem(R.id.menu_action_shows_hide).setVisible(!viewHolder.isHidden);
            menu.findItem(R.id.menu_action_shows_unhide).setVisible(viewHolder.isHidden);

            // hide unused actions
            menu.findItem(R.id.menu_action_shows_watched_next).setVisible(false);

            popupMenu.setOnMenuItemClickListener(
                    new ShowMenuItemClickListener(SgApp.from(getActivity()),
                            getFragmentManager(), viewHolder.showTvdbId, viewHolder.episodeTvdbId,
                            ListsActivity.TAG));
            popupMenu.show();
        }
    };
}
