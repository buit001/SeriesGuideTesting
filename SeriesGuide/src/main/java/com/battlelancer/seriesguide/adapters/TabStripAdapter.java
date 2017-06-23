package com.battlelancer.seriesguide.adapters;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.StringRes;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import com.battlelancer.seriesguide.R;
import com.battlelancer.seriesguide.widgets.SlidingTabLayout;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Helper class for easy setup of a {@link com.battlelancer.seriesguide.widgets.SlidingTabLayout}.
 */
public class TabStripAdapter extends FragmentPagerAdapter {

    private final ArrayList<TabInfo> mTabs = new ArrayList<>();

    private final Context mContext;

    private final FragmentManager mFragmentManager;

    private final ViewPager mViewPager;

    private final SlidingTabLayout mTabLayout;

    static final class TabInfo {

        private final Class<?> mClass;

        private final Bundle mArgs;

        private final int mTitleRes;

        TabInfo(Class<?> fragmentClass, Bundle args, int titleRes) {
            mClass = fragmentClass;
            mArgs = args;
            mTitleRes = titleRes;
        }
    }

    public TabStripAdapter(FragmentManager fm, Context context, ViewPager pager,
            SlidingTabLayout tabs) {
        super(fm);
        mFragmentManager = fm;
        mContext = context;

        // setup view pager
        mViewPager = pager;
        mViewPager.setAdapter(this);

        // setup tabs
        mTabLayout = tabs;
        mTabLayout.setCustomTabView(R.layout.tabstrip_item_allcaps, R.id.textViewTabStripItem);
        mTabLayout.setSelectedIndicatorColors(ContextCompat.getColor(context, R.color.white));
        mTabLayout.setViewPager(mViewPager);
    }

    /**
     * Adds a new tab. Make sure to call {@link #notifyTabsChanged} after you have added them all.
     */
    public void addTab(@StringRes int titleRes, Class<?> fragmentClass, Bundle args) {
        mTabs.add(new TabInfo(fragmentClass, args, titleRes));
    }

    /**
     * Update an existing tab. Make sure to call {@link #notifyTabsChanged} afterwards.
     */
    public void updateTab(int titleRes, Class<?> fragmentClass, Bundle args, int position) {
        if (position >= 0 && position < mTabs.size()) {
            // update tab info
            mTabs.set(position, new TabInfo(fragmentClass, args, titleRes));

            // find current fragment of tab
            Fragment oldFragment = mFragmentManager
                    .findFragmentByTag(makeFragmentName(mViewPager.getId(), getItemId(position)));
            // remove it
            FragmentTransaction transaction = mFragmentManager.beginTransaction();
            transaction.remove(oldFragment);
            transaction.commitAllowingStateLoss();
            mFragmentManager.executePendingTransactions();
        }
    }

    /**
     * Notifies the adapter and tab strip that the tabs have changed.
     */
    public void notifyTabsChanged() {
        notifyDataSetChanged();
        mTabLayout.setViewPager(mViewPager);
    }

    @Override
    public Fragment getItem(int position) {
        TabInfo tab = mTabs.get(position);
        return Fragment.instantiate(mContext, tab.mClass.getName(), tab.mArgs);
    }

    @Override
    public int getCount() {
        return mTabs.size();
    }

    @Override
    public CharSequence getPageTitle(int position) {
        TabInfo tabInfo = mTabs.get(position);
        if (tabInfo != null) {
            return mContext.getString(tabInfo.mTitleRes).toUpperCase(Locale.getDefault());
        }
        return "";
    }

    /**
     * Copied from FragmentPagerAdapter.
     */
    private static String makeFragmentName(int viewId, long id) {
        return "android:switcher:" + viewId + ":" + id;
    }
}
