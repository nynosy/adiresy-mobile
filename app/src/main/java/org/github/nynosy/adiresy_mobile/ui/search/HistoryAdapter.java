package org.github.nynosy.adiresy_mobile.ui.search;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.github.nynosy.adiresy_mobile.data.cache.SearchHistoryEntity;
import org.github.nynosy.adiresy_mobile.databinding.ItemSearchResultBinding;

public class HistoryAdapter extends ListAdapter<SearchHistoryEntity, HistoryAdapter.ViewHolder> {

    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(String query);
    }

    public HistoryAdapter() {
        super(DIFF);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemSearchResultBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemSearchResultBinding binding;

        ViewHolder(ItemSearchResultBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(SearchHistoryEntity item, OnItemClickListener listener) {
            binding.labelLabel.setText(item.query);
            binding.labelType.setText("recent");
            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) listener.onItemClick(item.query);
            });
        }
    }

    private static final DiffUtil.ItemCallback<SearchHistoryEntity> DIFF =
            new DiffUtil.ItemCallback<SearchHistoryEntity>() {
                @Override
                public boolean areItemsTheSame(@NonNull SearchHistoryEntity a, @NonNull SearchHistoryEntity b) {
                    return a.query.equals(b.query);
                }
                @Override
                public boolean areContentsTheSame(@NonNull SearchHistoryEntity a, @NonNull SearchHistoryEntity b) {
                    return a.query.equals(b.query) && a.timestamp == b.timestamp;
                }
            };
}
