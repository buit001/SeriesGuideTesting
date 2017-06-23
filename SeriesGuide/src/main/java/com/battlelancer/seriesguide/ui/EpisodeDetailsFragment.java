package com.battlelancer.seriesguide.ui;

import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.os.AsyncTaskCompat;
import android.support.v4.widget.TextViewCompat;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.style.TextAppearanceSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import com.battlelancer.seriesguide.R;
import com.battlelancer.seriesguide.SgApp;
import com.battlelancer.seriesguide.api.Action;
import com.battlelancer.seriesguide.backend.settings.HexagonSettings;
import com.battlelancer.seriesguide.enums.EpisodeFlags;
import com.battlelancer.seriesguide.extensions.ActionsHelper;
import com.battlelancer.seriesguide.extensions.EpisodeActionsContract;
import com.battlelancer.seriesguide.extensions.ExtensionManager;
import com.battlelancer.seriesguide.loaders.EpisodeActionsLoader;
import com.battlelancer.seriesguide.provider.SeriesGuideContract.Episodes;
import com.battlelancer.seriesguide.provider.SeriesGuideContract.ListItemTypes;
import com.battlelancer.seriesguide.provider.SeriesGuideContract.Seasons;
import com.battlelancer.seriesguide.provider.SeriesGuideContract.Shows;
import com.battlelancer.seriesguide.provider.SeriesGuideDatabase.Tables;
import com.battlelancer.seriesguide.settings.DisplaySettings;
import com.battlelancer.seriesguide.settings.TraktCredentials;
import com.battlelancer.seriesguide.thetvdbapi.TvdbEpisodeDetailsTask;
import com.battlelancer.seriesguide.thetvdbapi.TvdbImageTools;
import com.battlelancer.seriesguide.ui.dialogs.CheckInDialogFragment;
import com.battlelancer.seriesguide.ui.dialogs.ManageListsDialogFragment;
import com.battlelancer.seriesguide.ui.dialogs.RateDialogFragment;
import com.battlelancer.seriesguide.util.EpisodeTools;
import com.battlelancer.seriesguide.util.LanguageTools;
import com.battlelancer.seriesguide.util.ServiceUtils;
import com.battlelancer.seriesguide.util.ShareUtils;
import com.battlelancer.seriesguide.util.TextTools;
import com.battlelancer.seriesguide.util.TimeTools;
import com.battlelancer.seriesguide.util.TraktRatingsTask;
import com.battlelancer.seriesguide.util.TraktTools;
import com.battlelancer.seriesguide.util.Utils;
import com.battlelancer.seriesguide.util.ViewTools;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;
import com.uwetrottmann.androidutils.CheatSheet;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import timber.log.Timber;

/**
 * Displays details about a single episode like summary, ratings and episode image if available.
 */
public class EpisodeDetailsFragment extends Fragment implements EpisodeActionsContract {

    private static final String TAG = "Episode Details";

    private static final String KEY_EPISODE_TVDB_ID = "episodeTvdbId";

    private Handler mHandler = new Handler();
    private TvdbEpisodeDetailsTask detailsTask;
    private TraktRatingsTask ratingsTask;

    protected int mEpisodeFlag;
    protected boolean mCollected;
    protected int mShowTvdbId;
    protected int mSeasonNumber;
    protected int mEpisodeNumber;
    private String mEpisodeTitle;
    private String mShowTitle;
    private int mShowRunTime;
    private long mEpisodeReleaseTime;

    @BindView(R.id.containerEpisode) View mEpisodeContainer;
    @BindView(R.id.containerRatings) View mRatingsContainer;
    @BindView(R.id.containerEpisodeActions) LinearLayout mActionsContainer;

    @BindView(R.id.containerEpisodeImage) View mImageContainer;
    @BindView(R.id.imageViewEpisode) ImageView mEpisodeImage;

    @BindView(R.id.textViewEpisodeTitle) TextView mTitle;
    @BindView(R.id.textViewEpisodeDescription) TextView mDescription;
    @BindView(R.id.textViewEpisodeReleaseTime) TextView mReleaseTime;
    @BindView(R.id.textViewEpisodeReleaseDate) TextView mReleaseDate;
    @BindView(R.id.textViewEpisodeLastEdit) TextView mLastEdit;
    @BindView(R.id.labelEpisodeGuestStars) View mLabelGuestStars;
    @BindView(R.id.textViewEpisodeGuestStars) TextView mGuestStars;
    @BindView(R.id.textViewEpisodeDirectors) TextView mDirectors;
    @BindView(R.id.textViewEpisodeWriters) TextView mWriters;
    @BindView(R.id.labelEpisodeDvd) View mLabelDvd;
    @BindView(R.id.textViewEpisodeDvd) TextView mDvd;
    @BindView(R.id.textViewRatingsValue) TextView mTextRating;
    @BindView(R.id.textViewRatingsVotes) TextView mTextRatingVotes;
    @BindView(R.id.textViewRatingsUser) TextView mTextUserRating;

    @BindView(R.id.dividerEpisodeButtons) View dividerEpisodeButtons;
    @BindView(R.id.buttonEpisodeCheckin) Button buttonCheckin;
    @BindView(R.id.buttonEpisodeWatched) Button buttonWatch;
    @BindView(R.id.buttonEpisodeCollected) Button buttonCollect;
    @BindView(R.id.buttonEpisodeSkip) Button buttonSkip;

    @BindView(R.id.buttonEpisodeImdb) Button imdbButton;
    @BindView(R.id.buttonEpisodeTvdb) Button tvdbButton;
    @BindView(R.id.buttonEpisodeTrakt) Button traktButton;
    @BindView(R.id.buttonEpisodeComments) Button commentsButton;

    private Unbinder unbinder;

    /**
     * Data which has to be passed when creating this fragment.
     */
    public interface InitBundle {

        /**
         * Integer extra.
         */
        String EPISODE_TVDBID = "episode_tvdbid";

        /**
         * Boolean extra.
         */
        String IS_IN_MULTIPANE_LAYOUT = "multipane";
    }

    public static EpisodeDetailsFragment newInstance(int episodeId, boolean isInMultiPaneLayout) {
        EpisodeDetailsFragment f = new EpisodeDetailsFragment();

        // Supply index input as an argument.
        Bundle args = new Bundle();
        args.putInt(InitBundle.EPISODE_TVDBID, episodeId);
        args.putBoolean(InitBundle.IS_IN_MULTIPANE_LAYOUT, isInMultiPaneLayout);
        f.setArguments(args);

        return f;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_episode, container, false);
        unbinder = ButterKnife.bind(this, v);

        mEpisodeContainer.setVisibility(View.GONE);

        // comments button
        Resources.Theme theme = getActivity().getTheme();
        ViewTools.setVectorDrawableLeft(theme, commentsButton, R.drawable.ic_forum_black_24dp);

        // other bottom buttons
        ViewTools.setVectorDrawableLeft(theme, imdbButton, R.drawable.ic_link_black_24dp);
        ViewTools.setVectorDrawableLeft(theme, tvdbButton, R.drawable.ic_link_black_24dp);
        ViewTools.setVectorDrawableLeft(theme, traktButton, R.drawable.ic_link_black_24dp);

        return v;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        getLoaderManager().initLoader(EpisodesActivity.EPISODE_LOADER_ID, null,
                mEpisodeDataLoaderCallbacks);

        setHasOptionsMenu(true);
    }

    @Override
    public void onResume() {
        super.onResume();

        BaseNavDrawerActivity.ServiceActiveEvent event = EventBus.getDefault()
                .getStickyEvent(BaseNavDrawerActivity.ServiceActiveEvent.class);
        setEpisodeButtonsEnabled(event == null);

        EventBus.getDefault().register(this);
        loadEpisodeActionsDelayed();
    }

    @Override
    public void onPause() {
        super.onPause();

        if (mHandler != null) {
            mHandler.removeCallbacks(mEpisodeActionsRunnable);
        }
        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        // Always cancel the request here, this is safe to call even if the image has been loaded.
        // This ensures that the anonymous callback we have does not prevent the fragment from
        // being garbage collected. It also prevents our callback from getting invoked even after the
        // fragment is destroyed.
        Picasso.with(getContext()).cancelRequest(mEpisodeImage);
        unbinder.unbind();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        boolean isLightTheme = SeriesGuidePreferences.THEME == R.style.Theme_SeriesGuide_Light;
        // multi-pane layout has non-transparent action bar, adjust icon color
        boolean isInMultipane = getArguments().getBoolean(InitBundle.IS_IN_MULTIPANE_LAYOUT);
        inflater.inflate(isLightTheme && !isInMultipane
                ? R.menu.episodedetails_menu_light : R.menu.episodedetails_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_share) {
            shareEpisode();
            return true;
        } else if (itemId == R.id.menu_manage_lists) {
            ManageListsDialogFragment.showListsDialog(getEpisodeTvdbId(), ListItemTypes.EPISODE,
                    getFragmentManager());
            Utils.trackAction(getActivity(), TAG, "Manage lists");
            return true;
        } else if (itemId == R.id.menu_action_episode_calendar) {
            ShareUtils.suggestCalendarEvent(getActivity(), mShowTitle,
                    TextTools.getNextEpisodeString(getActivity(), mSeasonNumber, mEpisodeNumber,
                            mEpisodeTitle), mEpisodeReleaseTime, mShowRunTime);
            Utils.trackAction(getActivity(), TAG, "Add to calendar");
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private int getEpisodeTvdbId() {
        return getArguments().getInt(InitBundle.EPISODE_TVDBID);
    }

    /**
     * If episode was watched, flags as unwatched. Otherwise, flags as watched.
     */
    private void onToggleWatched() {
        changeEpisodeFlag(EpisodeTools.isWatched(mEpisodeFlag)
                ? EpisodeFlags.UNWATCHED : EpisodeFlags.WATCHED);
    }

    /**
     * If episode was skipped, flags as unwatched. Otherwise, flags as skipped.
     */
    private void onToggleSkipped() {
        changeEpisodeFlag(EpisodeTools.isSkipped(mEpisodeFlag)
                ? EpisodeFlags.UNWATCHED : EpisodeFlags.SKIPPED);
    }

    private void changeEpisodeFlag(int episodeFlag) {
        mEpisodeFlag = episodeFlag;
        EpisodeTools.episodeWatched(SgApp.from(getActivity()), mShowTvdbId, getEpisodeTvdbId(),
                mSeasonNumber, mEpisodeNumber, episodeFlag);
    }

    private void onToggleCollected() {
        mCollected = !mCollected;
        EpisodeTools.episodeCollected(SgApp.from(getActivity()), mShowTvdbId, getEpisodeTvdbId(),
                mSeasonNumber, mEpisodeNumber, mCollected);
    }

    @Override
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(ExtensionManager.EpisodeActionReceivedEvent event) {
        if (getEpisodeTvdbId() == event.episodeTvdbId) {
            loadEpisodeActionsDelayed();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventEpisodeTask(BaseNavDrawerActivity.ServiceActiveEvent event) {
        setEpisodeButtonsEnabled(false);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventEpisodeTask(BaseNavDrawerActivity.ServiceCompletedEvent event) {
        setEpisodeButtonsEnabled(true);
    }

    private void setEpisodeButtonsEnabled(boolean enabled) {
        buttonWatch.setEnabled(enabled);
        buttonCollect.setEnabled(enabled);
        buttonSkip.setEnabled(enabled);
        buttonCheckin.setEnabled(enabled);
    }

    private LoaderManager.LoaderCallbacks<Cursor> mEpisodeDataLoaderCallbacks
            = new LoaderManager.LoaderCallbacks<Cursor>() {
        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            return new CursorLoader(getActivity(), Episodes.buildEpisodeWithShowUri(String
                    .valueOf(getEpisodeTvdbId())), DetailsQuery.PROJECTION, null, null, null);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            if (!isAdded()) {
                return;
            }
            populateEpisodeData(data);
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
            // do nothing (we are never holding onto the cursor
        }
    };

    private void populateEpisodeData(Cursor cursor) {
        if (cursor == null || !cursor.moveToFirst()) {
            // no data to display
            if (mEpisodeContainer != null) {
                mEpisodeContainer.setVisibility(View.GONE);
            }
            return;
        }

        mShowTvdbId = cursor.getInt(DetailsQuery.SHOW_ID);
        mSeasonNumber = cursor.getInt(DetailsQuery.SEASON);
        mEpisodeNumber = cursor.getInt(DetailsQuery.NUMBER);
        mShowRunTime = cursor.getInt(DetailsQuery.SHOW_RUNTIME);
        mEpisodeReleaseTime = cursor.getLong(DetailsQuery.FIRST_RELEASE_MS);

        // title and description
        mEpisodeFlag = cursor.getInt(DetailsQuery.WATCHED);
        mEpisodeTitle = TextTools.getEpisodeTitle(getContext(),
                cursor.getString(DetailsQuery.TITLE), mEpisodeNumber);
        boolean hideDetails = EpisodeTools.isUnwatched(mEpisodeFlag)
                && DisplaySettings.preventSpoilers(getContext());
        if (hideDetails) {
            // just show the episode number "1x02"
            mTitle.setText(TextTools.getEpisodeNumber(getContext(), mSeasonNumber, mEpisodeNumber));
        } else {
            mTitle.setText(mEpisodeTitle);
        }
        String overview = cursor.getString(DetailsQuery.OVERVIEW);
        if (TextUtils.isEmpty(overview)) {
            // no description available, show no translation available message
            mDescription.setText(getString(R.string.no_translation,
                    LanguageTools.getShowLanguageStringFor(getContext(),
                            cursor.getString(DetailsQuery.SHOW_LANGUAGE)),
                    getString(R.string.tvdb)));
        } else {
            if (hideDetails) {
                mDescription.setText(R.string.no_spoilers);
            } else {
                mDescription.setText(overview);
            }
        }

        // show title
        mShowTitle = cursor.getString(DetailsQuery.SHOW_TITLE);

        // release date, also build release time and day
        boolean isReleased;
        SpannableStringBuilder timeAndNumbersText = new SpannableStringBuilder();
        if (mEpisodeReleaseTime != -1) {
            Date actualRelease = TimeTools.applyUserOffset(getContext(), mEpisodeReleaseTime);
            isReleased = TimeTools.isReleased(actualRelease);
            mReleaseDate.setText(TimeTools.formatToLocalDateAndDay(getContext(), actualRelease));

            String dateTime;
            if (DisplaySettings.isDisplayExactDate(getContext())) {
                // "31. October 2010"
                dateTime = TimeTools.formatToLocalDate(getContext(), actualRelease);
            } else {
                // "in 15 mins"
                dateTime = TimeTools.formatToLocalRelativeTime(getContext(), actualRelease);
            }
            // append day: "in 15 mins (Fri)"
            timeAndNumbersText.append(getString(R.string.release_date_and_day, dateTime,
                    TimeTools.formatToLocalDay(actualRelease)).toUpperCase(Locale.getDefault()));
        } else {
            mReleaseDate.setText(R.string.unknown);
            timeAndNumbersText.append(getString(R.string.episode_firstaired_unknown));
            isReleased = false;
        }
        // absolute number (e.g. relevant for Anime): "ABSOLUTE 142"
        int absoluteNumber = cursor.getInt(DetailsQuery.NUMBER_ABSOLUTE);
        if (absoluteNumber > 0) {
            timeAndNumbersText.append("  ");
            int numberStartIndex = timeAndNumbersText.length();
            timeAndNumbersText
                    .append(getString(R.string.episode_number_absolute))
                    .append(" ")
                    .append(String.valueOf(absoluteNumber));
            // de-emphasize number
            timeAndNumbersText.setSpan(new TextAppearanceSpan(getActivity(),
                            R.style.TextAppearance_Caption_Dim), numberStartIndex,
                    timeAndNumbersText.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        mReleaseTime.setText(timeAndNumbersText);

        // dim text color for title if not released
        TextViewCompat.setTextAppearance(mTitle, isReleased
                ? R.style.TextAppearance_Title : R.style.TextAppearance_Title_Dim);
        if (!isReleased) { // overrides text appearance span from above
            TextViewCompat.setTextAppearance(mReleaseTime, R.style.TextAppearance_Caption_Dim);
        }

        // guest stars
        ViewTools.setLabelValueOrHide(mLabelGuestStars, mGuestStars,
                TextTools.splitAndKitTVDBStrings(cursor.getString(DetailsQuery.GUESTSTARS))
        );
        // DVD episode number
        ViewTools.setLabelValueOrHide(mLabelDvd, mDvd, cursor.getDouble(DetailsQuery.NUMBER_DVD));
        // directors
        ViewTools.setValueOrPlaceholder(mDirectors, TextTools.splitAndKitTVDBStrings(cursor
                .getString(DetailsQuery.DIRECTORS)));
        // writers
        ViewTools.setValueOrPlaceholder(mWriters, TextTools.splitAndKitTVDBStrings(cursor
                .getString(DetailsQuery.WRITERS)));

        // last TVDb edit date
        long lastEditSeconds = cursor.getLong(DetailsQuery.LAST_EDITED);
        if (lastEditSeconds > 0) {
            mLastEdit.setText(DateUtils.formatDateTime(getActivity(), lastEditSeconds * 1000,
                    DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME));
        } else {
            mLastEdit.setText(R.string.unknown);
        }

        // ratings
        mRatingsContainer.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                rateEpisode();
            }
        });
        CheatSheet.setup(mRatingsContainer, R.string.action_rate);

        // trakt rating
        mTextRating.setText(
                TraktTools.buildRatingString(cursor.getDouble(DetailsQuery.RATING_GLOBAL)));
        mTextRatingVotes.setText(TraktTools.buildRatingVotesString(getActivity(),
                cursor.getInt(DetailsQuery.RATING_VOTES)));

        // user rating
        mTextUserRating.setText(TraktTools.buildUserRatingString(getActivity(),
                cursor.getInt(DetailsQuery.RATING_USER)));

        // episode image
        final String imagePath = cursor.getString(DetailsQuery.IMAGE);
        mImageContainer.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(), FullscreenImageActivity.class);
                intent.putExtra(FullscreenImageActivity.EXTRA_IMAGE,
                        TvdbImageTools.fullSizeUrl(imagePath));
                Utils.startActivityWithAnimation(getActivity(), intent, v);
            }
        });
        loadImage(imagePath, hideDetails);

        // check in button
        final int episodeTvdbId = cursor.getInt(DetailsQuery._ID);
        buttonCheckin.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // display a check-in dialog
                CheckInDialogFragment f = CheckInDialogFragment.newInstance(getActivity(),
                        episodeTvdbId);
                if (f != null && isResumed()) {
                    f.show(getFragmentManager(), "checkin-dialog");
                    Utils.trackAction(getActivity(), TAG, "Check-In");
                }
            }
        });
        CheatSheet.setup(buttonCheckin);

        // hide check-in if not connected to trakt or hexagon is enabled
        boolean isConnectedToTrakt = TraktCredentials.get(getActivity()).hasCredentials();
        boolean displayCheckIn = isConnectedToTrakt && !HexagonSettings.isEnabled(getActivity());
        buttonCheckin.setVisibility(displayCheckIn ? View.VISIBLE : View.GONE);
        dividerEpisodeButtons.setVisibility(displayCheckIn ? View.VISIBLE : View.GONE);

        // watched button
        boolean isWatched = EpisodeTools.isWatched(mEpisodeFlag);
        ViewTools.setCompoundDrawablesRelativeWithIntrinsicBounds(buttonWatch, 0,
                isWatched ? Utils.resolveAttributeToResourceId(getActivity().getTheme(),
                        R.attr.drawableWatched)
                        : Utils.resolveAttributeToResourceId(getActivity().getTheme(),
                                R.attr.drawableWatch), 0, 0);
        buttonWatch.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onToggleWatched();
                Utils.trackAction(getActivity(), TAG, "Toggle watched");
            }
        });
        buttonWatch.setText(isWatched ? R.string.action_unwatched : R.string.action_watched);
        CheatSheet.setup(buttonWatch, isWatched ? R.string.action_unwatched
                : R.string.action_watched);

        // collected button
        mCollected = cursor.getInt(DetailsQuery.COLLECTED) == 1;
        ViewTools.setCompoundDrawablesRelativeWithIntrinsicBounds(buttonCollect, 0,
                mCollected ? R.drawable.ic_collected
                        : Utils.resolveAttributeToResourceId(getActivity().getTheme(),
                                R.attr.drawableCollect), 0, 0);
        buttonCollect.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onToggleCollected();
                Utils.trackAction(getActivity(), TAG, "Toggle collected");
            }
        });
        buttonCollect.setText(mCollected
                ? R.string.action_collection_remove : R.string.action_collection_add);
        CheatSheet.setup(buttonCollect, mCollected
                ? R.string.action_collection_remove : R.string.action_collection_add);

        // skip button
        boolean isSkipped = EpisodeTools.isSkipped(mEpisodeFlag);
        if (isWatched) {
            // if watched do not allow skipping
            buttonSkip.setVisibility(View.INVISIBLE);
        } else {
            buttonSkip.setVisibility(View.VISIBLE);
            ViewTools.setCompoundDrawablesRelativeWithIntrinsicBounds(buttonSkip, 0,
                    isSkipped
                            ? R.drawable.ic_skipped
                            : Utils.resolveAttributeToResourceId(getActivity().getTheme(),
                                    R.attr.drawableSkip), 0, 0);
            buttonSkip.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    onToggleSkipped();
                    Utils.trackAction(getActivity(), TAG, "Toggle skipped");
                }
            });
            buttonSkip.setText(isSkipped ? R.string.action_dont_skip : R.string.action_skip);
            CheatSheet.setup(buttonSkip,
                    isSkipped ? R.string.action_dont_skip : R.string.action_skip);
        }

        // service buttons
        ServiceUtils.setUpTraktEpisodeButton(traktButton, getEpisodeTvdbId(), TAG);
        // IMDb
        String imdbId = cursor.getString(DetailsQuery.IMDBID);
        if (TextUtils.isEmpty(imdbId)) {
            // fall back to show IMDb id
            imdbId = cursor.getString(DetailsQuery.SHOW_IMDBID);
        }
        ServiceUtils.setUpImdbButton(imdbId, imdbButton, TAG);
        // TVDb
        final int seasonTvdbId = cursor.getInt(DetailsQuery.SEASON_ID);
        ServiceUtils.setUpTvdbButton(mShowTvdbId, seasonTvdbId, getEpisodeTvdbId(), tvdbButton,
                TAG);
        // trakt comments
        commentsButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(), TraktCommentsActivity.class);
                intent.putExtras(TraktCommentsActivity.createInitBundleEpisode(mEpisodeTitle,
                        getEpisodeTvdbId()
                ));
                Utils.startActivityWithAnimation(getActivity(), intent, v);
                Utils.trackAction(v.getContext(), TAG, "Comments");
            }
        });

        mEpisodeContainer.setVisibility(View.VISIBLE);

        loadDetails(cursor);
    }

    private void loadDetails(Cursor cursor) {
        // get full info if episode was edited on TVDb
        if (detailsTask == null || detailsTask.getStatus() == AsyncTask.Status.FINISHED) {
            long lastEdited = cursor.getLong(DetailsQuery.LAST_EDITED);
            long lastUpdated = cursor.getLong(DetailsQuery.LAST_UPDATED);
            detailsTask = TvdbEpisodeDetailsTask.runIfOutdated(SgApp.from(getActivity()),
                    mShowTvdbId, getEpisodeTvdbId(), lastEdited, lastUpdated);
        }

        // update trakt ratings
        if (ratingsTask == null || ratingsTask.getStatus() == AsyncTask.Status.FINISHED) {
            ratingsTask = new TraktRatingsTask(SgApp.from(getActivity()), mShowTvdbId,
                    getEpisodeTvdbId(), mSeasonNumber, mEpisodeNumber);
            AsyncTaskCompat.executeParallel(ratingsTask);
        }
    }

    private void rateEpisode() {
        RateDialogFragment.displayRateDialog(getActivity(), getFragmentManager(),
                getEpisodeTvdbId());
        Utils.trackAction(getActivity(), TAG, "Rate (trakt)");
    }

    private void shareEpisode() {
        if (mEpisodeTitle == null || mShowTitle == null) {
            return;
        }
        ShareUtils.shareEpisode(getActivity(), getEpisodeTvdbId(), mSeasonNumber, mEpisodeNumber,
                mShowTitle, mEpisodeTitle);
        Utils.trackAction(getActivity(), TAG, "Share");
    }

    private void loadImage(String imagePath, boolean hideDetails) {
        // immediately hide container if there is no image
        if (TextUtils.isEmpty(imagePath)) {
            mImageContainer.setVisibility(View.GONE);
            return;
        }

        if (hideDetails) {
            // show image placeholder
            mEpisodeImage.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            mEpisodeImage.setImageResource(R.drawable.ic_image_missing);
        } else {
            // try loading image
            mImageContainer.setVisibility(View.VISIBLE);
            ServiceUtils.loadWithPicasso(getActivity(), TvdbImageTools.fullSizeUrl(imagePath))
                    .error(R.drawable.ic_image_missing)
                    .into(mEpisodeImage,
                            new Callback() {
                                @Override
                                public void onSuccess() {
                                    mEpisodeImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
                                }

                                @Override
                                public void onError() {
                                    mEpisodeImage.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                                }
                            }
                    );
        }
    }

    private LoaderManager.LoaderCallbacks<List<Action>> mEpisodeActionsLoaderCallbacks =
            new LoaderManager.LoaderCallbacks<List<Action>>() {
                @Override
                public Loader<List<Action>> onCreateLoader(int id, Bundle args) {
                    int episodeTvdbId = args.getInt(KEY_EPISODE_TVDB_ID);
                    return new EpisodeActionsLoader(getActivity(), episodeTvdbId);
                }

                @Override
                public void onLoadFinished(Loader<List<Action>> loader, List<Action> data) {
                    if (!isAdded()) {
                        return;
                    }
                    if (data == null) {
                        Timber.e("onLoadFinished: did not receive valid actions for %s",
                                getEpisodeTvdbId());
                    } else {
                        Timber.d("onLoadFinished: received %s actions for %s", data.size(),
                                getEpisodeTvdbId());
                    }
                    ActionsHelper.populateActions(getActivity().getLayoutInflater(),
                            getActivity().getTheme(), mActionsContainer, data, TAG);
                }

                @Override
                public void onLoaderReset(Loader<List<Action>> loader) {
                    // do nothing, we are not holding onto the actions list
                }
            };

    public void loadEpisodeActions() {
        Bundle args = new Bundle();
        args.putInt(KEY_EPISODE_TVDB_ID, getEpisodeTvdbId());
        getLoaderManager().restartLoader(EpisodesActivity.ACTIONS_LOADER_ID, args,
                mEpisodeActionsLoaderCallbacks);
    }

    Runnable mEpisodeActionsRunnable = new Runnable() {
        @Override
        public void run() {
            loadEpisodeActions();
        }
    };

    public void loadEpisodeActionsDelayed() {
        mHandler.removeCallbacks(mEpisodeActionsRunnable);
        mHandler.postDelayed(mEpisodeActionsRunnable,
                EpisodeActionsContract.ACTION_LOADER_DELAY_MILLIS);
    }

    interface DetailsQuery {

        String[] PROJECTION = new String[] {
                Tables.EPISODES + "." + Episodes._ID,
                Episodes.NUMBER,
                Episodes.ABSOLUTE_NUMBER,
                Episodes.DVDNUMBER,
                Seasons.REF_SEASON_ID,
                Episodes.SEASON,
                Episodes.IMDBID,
                Episodes.TITLE,
                Episodes.OVERVIEW,
                Episodes.FIRSTAIREDMS,
                Episodes.DIRECTORS,
                Episodes.GUESTSTARS,
                Episodes.WRITERS,
                Episodes.IMAGE,
                Tables.EPISODES + "." + Episodes.RATING_GLOBAL,
                Episodes.RATING_VOTES,
                Episodes.RATING_USER,
                Episodes.WATCHED,
                Episodes.COLLECTED,
                Episodes.LAST_EDITED,
                Episodes.LAST_UPDATED,
                Shows.REF_SHOW_ID,
                Shows.IMDBID,
                Shows.TITLE,
                Shows.RUNTIME,
                Shows.LANGUAGE
        };

        int _ID = 0;
        int NUMBER = 1;
        int NUMBER_ABSOLUTE = 2;
        int NUMBER_DVD = 3;
        int SEASON_ID = 4;
        int SEASON = 5;
        int IMDBID = 6;
        int TITLE = 7;
        int OVERVIEW = 8;
        int FIRST_RELEASE_MS = 9;
        int DIRECTORS = 10;
        int GUESTSTARS = 11;
        int WRITERS = 12;
        int IMAGE = 13;
        int RATING_GLOBAL = 14;
        int RATING_VOTES = 15;
        int RATING_USER = 16;
        int WATCHED = 17;
        int COLLECTED = 18;
        int LAST_EDITED = 19;
        int LAST_UPDATED = 20;
        int SHOW_ID = 21;
        int SHOW_IMDBID = 22;
        int SHOW_TITLE = 23;
        int SHOW_RUNTIME = 24;
        int SHOW_LANGUAGE = 25;
    }
}
