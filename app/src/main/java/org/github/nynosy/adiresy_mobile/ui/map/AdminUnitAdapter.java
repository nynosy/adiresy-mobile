package org.github.nynosy.adiresy_mobile.ui.map;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.github.nynosy.adiresy_mobile.data.cache.AdminUnitEntity;
import org.github.nynosy.adiresy_mobile.databinding.ItemAdminUnitBinding;

public class AdminUnitAdapter extends ListAdapter<AdminUnitEntity, AdminUnitAdapter.ViewHolder> {

    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(AdminUnitEntity unit);
    }

    public AdminUnitAdapter() {
        super(DIFF);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemAdminUnitBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemAdminUnitBinding binding;

        ViewHolder(ItemAdminUnitBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(AdminUnitEntity unit, OnItemClickListener listener) {
            binding.labelName.setText(unit.name);
            // Show the type badge only at the search-result root level (type is visible context)
            if (unit.type != null && !unit.type.isEmpty()) {
                binding.labelTypeBadge.setVisibility(android.view.View.VISIBLE);
                binding.labelTypeBadge.setText(unit.type.substring(0, 1).toUpperCase()
                        + unit.type.substring(1));
            } else {
                binding.labelTypeBadge.setVisibility(android.view.View.GONE);
            }
            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) listener.onItemClick(unit);
            });
        }
    }

    private static final DiffUtil.ItemCallback<AdminUnitEntity> DIFF =
            new DiffUtil.ItemCallback<AdminUnitEntity>() {
                @Override
                public boolean areItemsTheSame(@NonNull AdminUnitEntity a, @NonNull AdminUnitEntity b) {
                    return a.pcode.equals(b.pcode);
                }
                @Override
                public boolean areContentsTheSame(@NonNull AdminUnitEntity a, @NonNull AdminUnitEntity b) {
                    return a.pcode.equals(b.pcode) && a.name != null && a.name.equals(b.name);
                }
            };
}
