package org.github.nynosy.adiresy_mobile.ui.saved;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import org.github.nynosy.adiresy_mobile.R;
import org.github.nynosy.adiresy_mobile.data.cache.BookmarkListWithCount;

public class SaveToListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_LIST    = 0;
    private static final int TYPE_NEW     = 1;

    public interface OnListSelectedListener {
        void onListSelected(BookmarkListWithCount item);
        void onNewListClicked();
    }

    private List<BookmarkListWithCount> items = new ArrayList<>();
    private OnListSelectedListener listener;

    public void setItems(List<BookmarkListWithCount> items) {
        this.items = items != null ? items : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setListener(OnListSelectedListener listener) {
        this.listener = listener;
    }

    @Override
    public int getItemCount() {
        return items.size() + 1; // +1 for "New list" row
    }

    @Override
    public int getItemViewType(int position) {
        return position < items.size() ? TYPE_LIST : TYPE_NEW;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_LIST) {
            View v = inflater.inflate(R.layout.item_save_to_list_row, parent, false);
            return new ListViewHolder(v);
        } else {
            View v = inflater.inflate(R.layout.item_save_to_list_row, parent, false);
            return new NewListViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ListViewHolder) {
            BookmarkListWithCount item = items.get(position);
            ListViewHolder h = (ListViewHolder) holder;
            h.emoji.setText(item.list.emoji);
            h.name.setText(item.list.name);
            String countLabel = item.bookmarkCount == 1
                    ? h.itemView.getContext().getString(R.string.places_count_one)
                    : h.itemView.getContext().getString(R.string.places_count_other, item.bookmarkCount);
            h.count.setText(countLabel);
            h.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onListSelected(item);
            });
        } else if (holder instanceof NewListViewHolder) {
            NewListViewHolder h = (NewListViewHolder) holder;
            h.emoji.setText("➕");
            h.name.setText(R.string.new_list_label);
            h.count.setVisibility(View.GONE);
            h.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onNewListClicked();
            });
        }
    }

    static class ListViewHolder extends RecyclerView.ViewHolder {
        TextView emoji, name, count;
        ListViewHolder(View v) {
            super(v);
            emoji = v.findViewById(R.id.label_emoji);
            name  = v.findViewById(R.id.label_list_name);
            count = v.findViewById(R.id.label_list_count);
        }
    }

    static class NewListViewHolder extends RecyclerView.ViewHolder {
        TextView emoji, name, count;
        NewListViewHolder(View v) {
            super(v);
            emoji = v.findViewById(R.id.label_emoji);
            name  = v.findViewById(R.id.label_list_name);
            count = v.findViewById(R.id.label_list_count);
        }
    }
}
