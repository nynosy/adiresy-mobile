package org.github.nynosy.adiresy_mobile.ui.home;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;

import java.util.Collections;
import java.util.List;

import org.github.nynosy.adiresy_mobile.R;
import org.github.nynosy.adiresy_mobile.data.Result;
import org.github.nynosy.adiresy_mobile.data.cache.AddressEntity;
import org.github.nynosy.adiresy_mobile.databinding.FragmentHomeBinding;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private HomeViewModel viewModel;
    private NearbyAdapter adapter;

    private final ActivityResultLauncher<String> locationPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (granted) startLocating();
                        else showPermissionDeniedFeedback();
                    });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        adapter = new NearbyAdapter();
        adapter.setOnItemClickListener(address ->
                CodeCardBottomSheet.forAddress(address)
                        .show(getChildFragmentManager(), CodeCardBottomSheet.TAG));
        binding.recyclerNearby.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerNearby.setAdapter(adapter);

        binding.btnLocateMe.setOnClickListener(v -> onLocateMeClicked());

        viewModel.getLocationState().observe(getViewLifecycleOwner(), state -> {
            switch (state) {
                case REQUESTING:
                    binding.btnLocateMe.setEnabled(false);
                    binding.progressLocating.setVisibility(View.VISIBLE);
                    break;
                case ACQUIRED:
                case IDLE:
                    binding.btnLocateMe.setEnabled(true);
                    binding.progressLocating.setVisibility(View.GONE);
                    break;
                case FAILED:
                    binding.btnLocateMe.setEnabled(true);
                    binding.progressLocating.setVisibility(View.GONE);
                    Snackbar.make(binding.getRoot(),
                            R.string.location_failed_outdoors, Snackbar.LENGTH_LONG).show();
                    break;
            }
        });

        viewModel.getLocation().observe(getViewLifecycleOwner(), location -> {
            adapter.setUserLocation(location);
            binding.labelNearby.setVisibility(View.VISIBLE);
        });

        viewModel.getNearbyBuildings().observe(getViewLifecycleOwner(), result -> {
            if (result == null) return;
            List<AddressEntity> buildings = result.data != null ? result.data : Collections.emptyList();
            adapter.submitList(buildings);
            boolean hasBuildings = !buildings.isEmpty();
            binding.recyclerNearby.setVisibility(hasBuildings ? View.VISIBLE : View.GONE);
            binding.emptyState.setVisibility(hasBuildings ? View.GONE : View.VISIBLE);
            if (!hasBuildings) {
                binding.labelEmpty.setText(result.isError()
                        ? R.string.nearby_empty_offline
                        : R.string.nearby_empty_online);
            }
        });
    }

    private void onLocateMeClicked() {
        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocating();
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            LocationRationaleBottomSheet sheet = new LocationRationaleBottomSheet();
            sheet.setOnGrantCallback(() ->
                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION));
            sheet.show(getChildFragmentManager(), LocationRationaleBottomSheet.TAG);
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    private void startLocating() {
        viewModel.startLocating();
    }

    private void showPermissionDeniedFeedback() {
        Snackbar.make(binding.getRoot(), R.string.location_permission_denied, Snackbar.LENGTH_LONG)
                .setAction(R.string.btn_open_settings, v -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.fromParts("package", requireContext().getPackageName(), null));
                    startActivity(intent);
                })
                .show();
    }

    @Override
    public void onResume() {
        super.onResume();
        AppCompatActivity activity = (AppCompatActivity) requireActivity();
        if (activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setTitle(R.string.app_name);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        viewModel.cancelLocating();
        binding = null;
    }
}
