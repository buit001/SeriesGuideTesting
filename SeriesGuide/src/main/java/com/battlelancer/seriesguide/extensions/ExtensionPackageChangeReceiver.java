package com.battlelancer.seriesguide.extensions;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.text.TextUtils;
import java.util.List;
import timber.log.Timber;

/**
 * Broadcast receiver watching for changes to installed packages on the device. Removes uninstalled
 * extensions or clears caches triggering a data refresh if an extension was updated.
 *
 * <p> Adapted from <a href="https://github.com/romannurik/muzei">muzei's</a>
 * SourcePackageChangeReceiver.
 */
public class ExtensionPackageChangeReceiver extends WakefulBroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getData() == null) {
            return;
        }
        String action = intent.getAction();
        if (!Intent.ACTION_PACKAGE_CHANGED.equals(action)
                || !Intent.ACTION_PACKAGE_REPLACED.equals(action)
                || !Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
            // action does not match, ignore the intent
            return;
        }

        String changedPackage = intent.getData().getSchemeSpecificPart();
        ExtensionManager extensionManager = ExtensionManager.getInstance(context);
        List<ComponentName> enabledExtensions = extensionManager.getEnabledExtensions();
        int affectedExtensionIndex = -1;
        for (int i = 0; i < enabledExtensions.size(); i++) {
            ComponentName componentName = enabledExtensions.get(i);
            if (TextUtils.equals(changedPackage, componentName.getPackageName())) {
                affectedExtensionIndex = i;
                break;
            }
        }
        if (affectedExtensionIndex == -1) {
            return;
        }

        // temporarily unsubscribe from extension
        ComponentName changedExtension = enabledExtensions.remove(affectedExtensionIndex);
        extensionManager.setEnabledExtensions(enabledExtensions);

        try {
            context.getPackageManager().getServiceInfo(changedExtension, 0);
        } catch (PackageManager.NameNotFoundException e) {
            Timber.i(e, "Extension no longer available: removed");
            return;
        }

        // changed or updated
        Timber.i("Extension package changed or replaced: re-subscribing");
        enabledExtensions.add(affectedExtensionIndex, changedExtension);
        extensionManager.setEnabledExtensions(enabledExtensions);
    }
}
