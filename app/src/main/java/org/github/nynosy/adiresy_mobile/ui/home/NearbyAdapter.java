package org.github.nynosy.adiresy_mobile.ui.home;

import android.location.Location;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Locale;

import org.github.nynosy.adiresy_mobile.data.cache.AddressEntity;
import org.github.nynosy.adiresy_mobile.databinding.ItemNearbyBuildingBinding;

public class NearbyAdapter extends ListAdapter<AddressEntity, NearbyAdapter.ViewHolder> {

    private Location userLocation;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(AddressEntity address);
    }

    public NearbyAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setUserLocation(@Nullable Location location) {
        this.userLocation = location;
        notifyItemRangeChanged(0, getItemCount());
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemNearbyBuildingBinding binding = ItemNearbyBuildingBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position), userLocation, listener);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemNearbyBuildingBinding binding;

        ViewHolder(ItemNearbyBuildingBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(AddressEntity item, @Nullable Location userLocation,
                  @Nullable OnItemClickListener listener) {
            binding.labelCode.setText(item.canonicalCode);
            binding.labelLocation.setText(item.fokontanyName);

            if (userLocation != null) {
                float[] results = new float[1];
                Location.distanceBetween(userLocation.getLatitude(), userLocation.getLongitude(),
                        item.latitude, item.longitude, results);
                float distM = results[0];
                String distText = distM < 1000
                        ? String.format(Locale.getDefault(), "%.0f m", distM)
                        : String.format(Locale.getDefault(), "%.1f km", distM / 1000f);
                binding.labelDistance.setText(distText);
            } else {
                binding.labelDistance.setText("");
            }

            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) listener.onItemClick(item);
            });
        }
    }

    private static final DiffUtil.ItemCallback<AddressEntity> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<AddressEntity>() {
                @Override
                public boolean areItemsTheSame(@NonNull AddressEntity a, @NonNull AddressEntity b) {
                    return a.canonicalCode.equals(b.canonicalCode);
                }
                @Override
                public boolean areContentsTheSame(@NonNull AddressEntity a, @NonNull AddressEntity b) {
                    return a.canonicalCode.equals(b.canonicalCode)
                            && a.fokontanyName != null
                            && a.fokontanyName.equals(b.fokontanyName);
                }
            };
}
