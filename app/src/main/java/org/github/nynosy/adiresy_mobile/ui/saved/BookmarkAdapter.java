package org.github.nynosy.adiresy_mobile.ui.saved;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.util.concurrent.TimeUnit;

import org.github.nynosy.adiresy_mobile.R;
import org.github.nynosy.adiresy_mobile.data.cache.BookmarkEntity;

public class BookmarkAdapter
        extends ListAdapter<BookmarkEntity, BookmarkAdapter.ViewHolder> {

    public interface OnBookmarkActionListener {
        void onBookmarkClicked(BookmarkEntity bookmark);
        void onEditDescription(BookmarkEntity bookmark);
        void onMoveToList(BookmarkEntity bookmark);
        void onShare(BookmarkEntity bookmark);
        void onDelete(BookmarkEntity bookmark);
    }

    private OnBookmarkActionListener listener;

    public BookmarkAdapter() {
        super(new DiffUtil.ItemCallback<BookmarkEntity>() {
            @Override
            public boolean areItemsTheSame(@NonNull BookmarkEntity a, @NonNull BookmarkEntity b) {
                return a.id == b.id;
            }

            @Override
            public boolean areContentsTheSame(@NonNull BookmarkEntity a, @NonNull BookmarkEntity b) {
                return a.canonicalCode.equals(b.canonicalCode)
                        && java.util.Objects.equals(a.name, b.name)
                        && java.util.Objects.equals(a.userDescription, b.userDescription)
                        && a.savedAt == b.savedAt;
            }
        });
    }

    public void setListener(OnBookmarkActionListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_bookmark, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        BookmarkEntity item = getItem(position);
        Context ctx = h.itemView.getContext();

        // Primary: user-set name, fallback to canonical code
        String primary = (item.name != null && !item.name.isEmpty())
                ? item.name : item.canonicalCode;
        h.description.setText(primary);

        // Secondary: fokontany · commune, plus code when a name is set
        StringBuilder location = new StringBuilder();
        if (item.fokontanyName != null && !item.fokontanyName.isEmpty())
            location.append(item.fokontanyName);
        if (item.communeName != null && !item.communeName.isEmpty()) {
            if (location.length() > 0) location.append(" · ");
            location.append(item.communeName);
        }
        if (item.name != null && !item.name.isEmpty()) {
            if (location.length() > 0) location.append("  ");
            location.append(item.canonicalCode);
        }
        h.location.setText(location.toString());

        // Notes (userDescription) shown if present
        if (item.userDescription != null && !item.userDescription.isEmpty()) {
            h.notes.setVisibility(View.VISIBLE);
            h.notes.setText(item.userDescription);
        } else {
            h.notes.setVisibility(View.GONE);
        }

        h.date.setText(relativeDate(ctx, item.savedAt));

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onBookmarkClicked(item);
        });

        h.overflow.setOnClickListener(v -> showOverflow(h.overflow, item));
    }

    private void showOverflow(View anchor, BookmarkEntity item) {
        PopupMenu popup = new PopupMenu(anchor.getContext(), anchor);
        popup.getMenu().add(0, 0, 0, R.string.edit_description_title);
        popup.getMenu().add(0, 1, 1, R.string.move_to_list_title);
        popup.getMenu().add(0, 2, 2, R.string.btn_share);
        popup.getMenu().add(0, 3, 3, R.string.btn_delete);
        popup.setOnMenuItemClickListener(menuItem -> {
            if (listener == null) return false;
            switch (menuItem.getItemId()) {
                case 0: listener.onEditDescription(item); return true;
                case 1: listener.onMoveToList(item);      return true;
                case 2: listener.onShare(item);           return true;
                case 3: listener.onDelete(item);          return true;
            }
            return false;
        });
        popup.show();
    }

    private String relativeDate(Context ctx, long savedAt) {
        long diffMs = System.currentTimeMillis() - savedAt;
        long days   = TimeUnit.MILLISECONDS.toDays(diffMs);
        if (days == 0) return ctx.getString(R.string.saved_today);
        if (days == 1) return ctx.getString(R.string.saved_yesterday);
        return ctx.getString(R.string.saved_days_ago, (int) days);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView    description, location, notes, date;
        ImageButton overflow;

        ViewHolder(View v) {
            super(v);
            description = v.findViewById(R.id.label_description);
            location    = v.findViewById(R.id.label_location);
            notes       = v.findViewById(R.id.label_notes);
            date        = v.findViewById(R.id.label_date);
            overflow    = v.findViewById(R.id.btn_overflow);
        }
    }
}
