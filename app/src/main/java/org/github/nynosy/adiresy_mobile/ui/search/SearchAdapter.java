package org.github.nynosy.adiresy_mobile.ui.search;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.github.nynosy.adiresy_mobile.data.api.dto.AutocompleteDto;
import org.github.nynosy.adiresy_mobile.databinding.ItemSearchResultBinding;

public class SearchAdapter extends ListAdapter<AutocompleteDto.Item, SearchAdapter.ViewHolder> {

    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(AutocompleteDto.Item item);
    }

    public SearchAdapter() {
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

        void bind(AutocompleteDto.Item item, OnItemClickListener listener) {
            binding.labelLabel.setText(item.label);
            binding.labelType.setText(item.type != null ? item.type.toUpperCase() : "");
            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) listener.onItemClick(item);
            });
        }
    }

    private static final DiffUtil.ItemCallback<AutocompleteDto.Item> DIFF =
            new DiffUtil.ItemCallback<AutocompleteDto.Item>() {
                @Override
                public boolean areItemsTheSame(@NonNull AutocompleteDto.Item a, @NonNull AutocompleteDto.Item b) {
                    return a.label != null && a.label.equals(b.label);
                }
                @Override
                public boolean areContentsTheSame(@NonNull AutocompleteDto.Item a, @NonNull AutocompleteDto.Item b) {
                    return areItemsTheSame(a, b);
                }
            };
}
