package com.battlelancer.seriesguide.extensions;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import com.battlelancer.seriesguide.api.Action;
import com.battlelancer.seriesguide.api.Episode;
import com.battlelancer.seriesguide.api.Movie;
import com.battlelancer.seriesguide.api.SeriesGuideExtension;
import com.battlelancer.seriesguide.api.constants.IncomingConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.greenrobot.eventbus.EventBus;
import org.json.JSONArray;
import org.json.JSONException;
import timber.log.Timber;

import static com.battlelancer.seriesguide.api.constants.OutgoingConstants.ACTION_TYPE_EPISODE;
import static com.battlelancer.seriesguide.api.constants.OutgoingConstants.ACTION_TYPE_MOVIE;

public class ExtensionManager {

    private static final String PREF_FILE_SUBSCRIPTIONS = "seriesguide_extensions";
    private static final String PREF_SUBSCRIPTIONS = "subscriptions";

    private static final int HARD_CACHE_CAPACITY = 5;

    // Cashes received actions for the last few displayed episodes.
    private final static android.support.v4.util.LruCache<Integer, Map<ComponentName, Action>>
            sEpisodeActionsCache = new android.support.v4.util.LruCache<>(HARD_CACHE_CAPACITY);

    // Cashes received actions for the last few displayed movies.
    private final static android.support.v4.util.LruCache<Integer, Map<ComponentName, Action>>
            sMovieActionsCache = new android.support.v4.util.LruCache<>(HARD_CACHE_CAPACITY);

    private static ExtensionManager _instance;

    public static synchronized ExtensionManager getInstance(Context context) {
        if (_instance == null) {
            _instance = new ExtensionManager(context);
        }
        return _instance;
    }

    /**
     * {@link com.battlelancer.seriesguide.extensions.ExtensionManager} has received new {@link
     * com.battlelancer.seriesguide.api.Action} objects from enabled extensions. Receivers might
     * want to requery available actions.
     */
    public static class EpisodeActionReceivedEvent {
        public int episodeTvdbId;

        public EpisodeActionReceivedEvent(int episodeTvdbId) {
            this.episodeTvdbId = episodeTvdbId;
        }
    }

    /**
     * {@link com.battlelancer.seriesguide.extensions.ExtensionManager} has received new {@link
     * com.battlelancer.seriesguide.api.Action} objects from enabled extensions. Receivers might
     * want to requery available actions.
     */
    public static class MovieActionReceivedEvent {
        public int movieTmdbId;

        public MovieActionReceivedEvent(int movieTmdbId) {
            this.movieTmdbId = movieTmdbId;
        }
    }

    private Context context;
    private SharedPreferences prefs;
    private ComponentName subscriberComponentName;

    private Map<ComponentName, String> subscriptions; // extension + token = sub
    private Map<String, ComponentName> tokens; // mirrored map for faster token searching

    private List<ComponentName> enabledExtensions; // order-preserving list of enabled extensions

    private ExtensionManager(Context context) {
        Timber.d("Initializing extension manager");
        this.context = context.getApplicationContext();
        prefs = this.context.getSharedPreferences(PREF_FILE_SUBSCRIPTIONS, 0);
        subscriberComponentName = new ComponentName(this.context, ExtensionSubscriberService.class);
        loadSubscriptions();
    }

    /**
     * Queries the {@link android.content.pm.PackageManager} for any installed {@link
     * com.battlelancer.seriesguide.api.SeriesGuideExtension} extensions. Their info is extracted
     * into {@link com.battlelancer.seriesguide.extensions.ExtensionManager.Extension} objects.
     */
    @NonNull
    public List<Extension> queryAllAvailableExtensions() {
        Intent queryIntent = new Intent(SeriesGuideExtension.ACTION_SERIESGUIDE_EXTENSION);
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> resolveInfos = pm.queryIntentServices(queryIntent,
                PackageManager.GET_META_DATA);

        List<Extension> extensions = new ArrayList<>();
        for (ResolveInfo info : resolveInfos) {
            Extension extension = new Extension();
            // get label, icon and component name
            extension.label = info.loadLabel(pm).toString();
            extension.icon = info.loadIcon(pm);
            extension.componentName = new ComponentName(info.serviceInfo.packageName,
                    info.serviceInfo.name);
            // get description
            Context packageContext;
            try {
                packageContext = context.createPackageContext(
                        extension.componentName.getPackageName(), 0);
                Resources packageRes = packageContext.getResources();
                extension.description = packageRes.getString(info.serviceInfo.descriptionRes);
            } catch (SecurityException | PackageManager.NameNotFoundException | Resources.NotFoundException e) {
                Timber.e(e, "Reading description for extension %s failed", extension.componentName);
                extension.description = "";
            }
            // get (optional) settings activity
            Bundle metaData = info.serviceInfo.metaData;
            if (metaData != null) {
                String settingsActivity = metaData.getString("settingsActivity");
                if (!TextUtils.isEmpty(settingsActivity)) {
                    extension.settingsActivity = ComponentName.unflattenFromString(
                            info.serviceInfo.packageName + "/" + settingsActivity);
                }
            }

            Timber.d("queryAllAvailableExtensions: found extension %s %s", extension.label,
                    extension.componentName);
            extensions.add(extension);
        }

        return extensions;
    }

    /**
     * Enables the default list of extensions that come with this app.
     */
    public void setDefaultEnabledExtensions() {
        List<ComponentName> defaultExtensions = new ArrayList<>();
        defaultExtensions.add(new ComponentName(context, WebSearchExtension.class));
        defaultExtensions.add(new ComponentName(context, YouTubeExtension.class));
        setEnabledExtensions(defaultExtensions);
    }

    /**
     * Compares the list of currently enabled extensions with the given list and enables added
     * extensions and disables removed extensions.
     */
    public synchronized void setEnabledExtensions(List<ComponentName> extensions) {
        Set<ComponentName> extensionsToEnable = new HashSet<>(extensions);
        boolean isChanged = false;

        // disable removed extensions
        for (ComponentName extension : enabledExtensions) {
            if (!extensionsToEnable.contains(extension)) {
                // disable extension
                disableExtension(extension);
                isChanged = true;
            }
            // no need to enable, is already enabled
            extensionsToEnable.remove(extension);
        }

        // enable added extensions
        for (ComponentName extension : extensionsToEnable) {
            enableExtension(extension);
            isChanged = true;
        }

        // always save because just the order might have changed
        enabledExtensions = new ArrayList<>(extensions);
        saveSubscriptions();

        if (isChanged) {
            // clear actions cache so loaders will request new actions
            sEpisodeActionsCache.evictAll();
            sMovieActionsCache.evictAll();
        }
    }

    /**
     * Returns a copy of the list of currently enabled extensions in the order the user previously
     * determined.
     */
    public synchronized List<ComponentName> getEnabledExtensions() {
        return new ArrayList<>(enabledExtensions);
    }

    private void enableExtension(ComponentName extension) {
        if (extension == null) {
            Timber.e("enableExtension: empty extension");
        }

        if (subscriptions.containsKey(extension)) {
            // already subscribed
            Timber.d("enableExtension: already subscribed to %s", extension);
            return;
        }

        // subscribe
        String token = UUID.randomUUID().toString();
        while (tokens.containsKey(token)) {
            // create another UUID on collision
            /**
             * As the number of enabled extensions is rather low compared to the UUID number
             * space we shouldn't have to worry about this ever looping.
             */
            token = UUID.randomUUID().toString();
        }
        Timber.d("enableExtension: subscribing to %s", extension);
        subscriptions.put(extension, token);
        tokens.put(token, extension);
        context.startService(new Intent(IncomingConstants.ACTION_SUBSCRIBE)
                .setComponent(extension)
                .putExtra(IncomingConstants.EXTRA_SUBSCRIBER_COMPONENT,
                        subscriberComponentName)
                .putExtra(IncomingConstants.EXTRA_TOKEN, token));
    }

    private void disableExtension(ComponentName extension) {
        if (extension == null) {
            Timber.e("disableExtension: extension empty");
        }

        if (!subscriptions.containsKey(extension)) {
            Timber.d("disableExtension: extension not enabled %s", extension);
            return;
        }

        // unsubscribe
        Timber.d("disableExtension: unsubscribing from %s", extension);
        context.startService(new Intent(IncomingConstants.ACTION_SUBSCRIBE)
                .setComponent(extension)
                .putExtra(IncomingConstants.EXTRA_SUBSCRIBER_COMPONENT,
                        subscriberComponentName)
                .putExtra(IncomingConstants.EXTRA_TOKEN, (String) null));
        tokens.remove(subscriptions.remove(extension));
    }

    /**
     * Returns the currently available {@link com.battlelancer.seriesguide.api.Action} list for the
     * given episode, identified through its TVDb id. Sorted in the order determined by the user.
     */
    public synchronized List<Action> getLatestEpisodeActions(int episodeTvdbId) {
        Map<ComponentName, Action> actionMap = sEpisodeActionsCache.get(episodeTvdbId);
        return actionListFrom(actionMap);
    }

    /**
     * Returns the currently available {@link com.battlelancer.seriesguide.api.Action} list for the
     * given movie, identified through its TMDB id. Sorted in the order determined by the user.
     */
    public synchronized List<Action> getLatestMovieActions(int movieTmdbId) {
        Map<ComponentName, Action> actionMap = sMovieActionsCache.get(movieTmdbId);
        return actionListFrom(actionMap);
    }

    private List<Action> actionListFrom(Map<ComponentName, Action> actionMap) {
        if (actionMap == null) {
            return null;
        }
        List<Action> sortedActions = new ArrayList<>();
        for (ComponentName extension : enabledExtensions) {
            Action action = actionMap.get(extension);
            if (action != null) {
                sortedActions.add(action);
            }
        }
        return sortedActions;
    }

    /**
     * Asks all enabled extensions to publish an action for the given episode.
     */
    public synchronized void requestEpisodeActions(Episode episode) {
        for (ComponentName extension : subscriptions.keySet()) {
            requestEpisodeAction(extension, episode);
        }
    }

    /**
     * Ask a single extension to publish an action for the given episode.
     */
    private synchronized void requestEpisodeAction(ComponentName extension, Episode episode) {
        Timber.d("requestAction: requesting from %s for %s", extension, episode.getTvdbId());
        // prepare to receive actions for the given episode
        if (sEpisodeActionsCache.get(episode.getTvdbId()) == null) {
            sEpisodeActionsCache.put(episode.getTvdbId(), new HashMap<ComponentName, Action>());
        }
        // actually request actions
        context.startService(new Intent(IncomingConstants.ACTION_UPDATE)
                .setComponent(extension)
                .putExtra(IncomingConstants.EXTRA_ENTITY_IDENTIFIER, episode.getTvdbId())
                .putExtra(IncomingConstants.EXTRA_EPISODE, episode.toBundle()));
    }

    /**
     * Asks all enabled extensions to publish an action for the given movie.
     */
    public synchronized void requestMovieActions(Movie movie) {
        for (ComponentName extension : subscriptions.keySet()) {
            requestMovieAction(extension, movie);
        }
    }

    /**
     * Ask a single extension to publish an action for the given movie.
     */
    private synchronized void requestMovieAction(ComponentName extension, Movie movie) {
        Timber.d("requestAction: requesting from %s for %s", extension, movie.getTmdbId());
        // prepare to receive actions for the given episode
        if (sMovieActionsCache.get(movie.getTmdbId()) == null) {
            sMovieActionsCache.put(movie.getTmdbId(), new HashMap<ComponentName, Action>());
        }
        // actually request actions
        context.startService(new Intent(IncomingConstants.ACTION_UPDATE)
                .setComponent(extension)
                .putExtra(IncomingConstants.EXTRA_ENTITY_IDENTIFIER, movie.getTmdbId())
                .putExtra(IncomingConstants.EXTRA_MOVIE, movie.toBundle()));
    }

    public void handlePublishedAction(String token, Action action, int type) {
        if (TextUtils.isEmpty(token) || action == null) {
            // whoops, no token or action received
            Timber.d("handlePublishedAction: token or action empty");
            return;
        }
        if (type != ACTION_TYPE_EPISODE && type != ACTION_TYPE_MOVIE) {
            Timber.d("handlePublishedAction: unknown type of entity");
            return;
        }

        synchronized (this) {
            if (!tokens.containsKey(token)) {
                // we are not subscribed, ignore
                Timber.d("handlePublishedAction: token invalid, ignoring incoming action");
                return;
            }

            // check if action entity identifier is for an entity we requested actions for
            Map<ComponentName, Action> actionMap;
            if (type == ACTION_TYPE_EPISODE) {
                // episode
                actionMap = sEpisodeActionsCache.get(action.getEntityIdentifier());
            } else {
                // movie
                actionMap = sMovieActionsCache.get(action.getEntityIdentifier());
            }
            if (actionMap == null) {
                // did not request actions for this episode, or is already out of cache (too late!)
                Timber.d(
                        "handlePublishedAction: ignoring actions for %s, not requested",
                        action.getEntityIdentifier());
                return;
            }
            // store action for this entity
            ComponentName extension = tokens.get(token);
            actionMap.put(extension, action);
        }

        // notify that actions were updated
        if (type == ACTION_TYPE_EPISODE) {
            EventBus.getDefault()
                    .post(new EpisodeActionReceivedEvent(action.getEntityIdentifier()));
        } else {
            EventBus.getDefault().post(new MovieActionReceivedEvent(action.getEntityIdentifier()));
        }
    }

    private synchronized void loadSubscriptions() {
        enabledExtensions = new ArrayList<>();
        subscriptions = new HashMap<>();
        tokens = new HashMap<>();

        String serializedSubscriptions = prefs.getString(PREF_SUBSCRIPTIONS, null);
        if (serializedSubscriptions == null) {
            setDefaultEnabledExtensions();
            return;
        }

        JSONArray jsonArray;
        try {
            jsonArray = new JSONArray(serializedSubscriptions);
        } catch (JSONException e) {
            Timber.e(e, "Deserializing subscriptions failed");
            return;
        }

        for (int i = 0; i < jsonArray.length(); i++) {
            String subscription = jsonArray.optString(i, null);
            if (subscription == null) {
                continue;
            }
            String[] arr = subscription.split("\\|", 2);
            ComponentName extension = ComponentName.unflattenFromString(arr[0]);
            String token = arr[1];
            enabledExtensions.add(extension);
            subscriptions.put(extension, token);
            tokens.put(token, extension);
            Timber.d("Restored subscription: %s token: %s", extension, token);
        }
    }

    private synchronized void saveSubscriptions() {
        List<String> serializedSubscriptions = new ArrayList<>();
        for (ComponentName extension : enabledExtensions) {
            serializedSubscriptions.add(extension.flattenToShortString() + "|"
                    + subscriptions.get(extension));
        }
        Timber.d("Saving %s subscriptions", serializedSubscriptions.size());
        JSONArray json = new JSONArray(serializedSubscriptions);
        prefs.edit().putString(PREF_SUBSCRIPTIONS, json.toString()).apply();
    }

    /**
     * Removes all currently cached {@link com.battlelancer.seriesguide.api.Action} objects for all
     * enabled {@linkplain com.battlelancer.seriesguide.extensions.ExtensionManager.Extension}s.
     * Call this e.g. after going into an extensions settings activity.
     */
    public synchronized void clearActionsCache() {
        sEpisodeActionsCache.evictAll();
        sMovieActionsCache.evictAll();
    }

    public class Extension {
        public Drawable icon;
        public String label;
        public ComponentName componentName;
        public String description;
        public ComponentName settingsActivity;
    }
}
