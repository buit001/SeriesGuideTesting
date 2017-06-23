package com.battlelancer.seriesguide.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import com.battlelancer.seriesguide.R;
import com.battlelancer.seriesguide.loaders.OrderedListsLoader;
import java.util.List;

/**
 * Used with {@link com.battlelancer.seriesguide.ui.dialogs.ListsReorderDialogFragment}.
 */
public class ListsAdapter extends ArrayAdapter<OrderedListsLoader.OrderedList> {

    static class ListsViewHolder {
        public TextView name;

        public ListsViewHolder(View v) {
            name = (TextView) v.findViewById(R.id.textViewItemListName);
        }
    }

    private List<OrderedListsLoader.OrderedList> dataset;

    public ListsAdapter(Context context) {
        super(context, 0);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ListsViewHolder viewHolder;

        if (convertView == null) {
            convertView = LayoutInflater.from(getContext())
                    .inflate(R.layout.item_list, parent, false);

            viewHolder = new ListsViewHolder(convertView);
            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ListsViewHolder) convertView.getTag();
        }

        OrderedListsLoader.OrderedList item = getItem(position);
        viewHolder.name.setText(item.name);

        return convertView;
    }

    public synchronized void setData(List<OrderedListsLoader.OrderedList> dataset) {
        this.dataset = dataset;

        clear();
        if (dataset != null) {
            addAll(dataset);
        }
    }

    public synchronized void reorderList(int from, int to) {
        if (dataset == null || from >= dataset.size()) {
            return;
        }
        OrderedListsLoader.OrderedList list = dataset.remove(from);
        dataset.add(to, list);

        clear();
        addAll(dataset);
    }
}
