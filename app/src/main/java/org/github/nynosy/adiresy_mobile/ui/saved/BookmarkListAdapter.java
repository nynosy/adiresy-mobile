package org.github.nynosy.adiresy_mobile.ui.saved;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.github.nynosy.adiresy_mobile.R;
import org.github.nynosy.adiresy_mobile.data.cache.BookmarkListWithCount;

public class BookmarkListAdapter
        extends ListAdapter<BookmarkListWithCount, BookmarkListAdapter.ViewHolder> {

    public interface OnListClickListener {
        void onListClicked(BookmarkListWithCount item);
    }

    private OnListClickListener listener;

    public BookmarkListAdapter() {
        super(new DiffUtil.ItemCallback<BookmarkListWithCount>() {
            @Override
            public boolean areItemsTheSame(@NonNull BookmarkListWithCount a,
                                           @NonNull BookmarkListWithCount b) {
                return a.list.id == b.list.id;
            }

            @Override
            public boolean areContentsTheSame(@NonNull BookmarkListWithCount a,
                                              @NonNull BookmarkListWithCount b) {
                return a.list.name.equals(b.list.name)
                        && a.list.emoji.equals(b.list.emoji)
                        && a.bookmarkCount == b.bookmarkCount;
            }
        });
    }

    public void setListener(OnListClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_bookmark_list, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        BookmarkListWithCount item = getItem(position);
        h.emoji.setText(item.list.emoji);
        h.name.setText(item.list.name);

        if (item.list.description != null && !item.list.description.isEmpty()) {
            h.description.setVisibility(View.VISIBLE);
            h.description.setText(item.list.description);
        } else {
            h.description.setVisibility(View.GONE);
        }

        String countLabel = item.bookmarkCount == 1
                ? h.itemView.getContext().getString(R.string.places_count_one)
                : h.itemView.getContext().getString(R.string.places_count_other, item.bookmarkCount);
        h.count.setText(countLabel);

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onListClicked(item);
        });
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView emoji, name, description, count;
        View cardRoot;

        ViewHolder(View v) {
            super(v);
            cardRoot    = v.findViewById(R.id.card_root);
            emoji       = v.findViewById(R.id.label_emoji);
            name        = v.findViewById(R.id.label_name);
            description = v.findViewById(R.id.label_description);
            count       = v.findViewById(R.id.label_count);
        }
    }
}
