package org.github.nynosy.adiresy_mobile.ui.map;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import androidx.lifecycle.ViewModelProvider;

import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.OnMapReadyCallback;
import org.maplibre.android.maps.Style;

import org.github.nynosy.adiresy_mobile.R;
import org.github.nynosy.adiresy_mobile.databinding.FragmentMapBinding;
import org.github.nynosy.adiresy_mobile.map.AttributionBottomSheet;
import org.github.nynosy.adiresy_mobile.map.MapController;
import org.github.nynosy.adiresy_mobile.map.StyleLoader;

public class MapFragment extends Fragment implements OnMapReadyCallback {

    private FragmentMapBinding binding;
    private MapController mapController;
    private StyleLoader styleLoader;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentMapBinding.inflate(inflater, container, false);
        // MapView requires onCreate before getMapAsync
        binding.mapView.onCreate(savedInstanceState);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        styleLoader = new StyleLoader(requireContext());

        if (!styleLoader.hasOfflineTiles()) {
            binding.offlineBanner.setVisibility(View.VISIBLE);
        }

        binding.mapView.getMapAsync(this);

        MapViewModel mapViewModel = new ViewModelProvider(this).get(MapViewModel.class);

        binding.btnExplore.setOnClickListener(v ->
                new ExploreBottomSheet()
                        .show(getChildFragmentManager(), ExploreBottomSheet.TAG));

        mapViewModel.getSelectedBoundary().observe(getViewLifecycleOwner(), unit -> {
            // Phase 1: centre map on the bounding box when a boundary is selected.
            // Polygon drawing is wired when the geometry endpoint is called (M9+).
            if (unit != null && mapController != null && unit.bboxJson != null) {
                // Simple centring — full boundary overlay in polish pass
            }
        });
    }

    // Madagascar bounding box centre — fallback if style doesn't carry center/zoom
    private static final LatLng MADAGASCAR_CENTRE = new LatLng(-18.9100, 46.8691);
    private static final float MADAGASCAR_ZOOM = 5f;

    @Override
    public void onMapReady(@NonNull MapLibreMap mapLibreMap) {
        mapController = new MapController(mapLibreMap);

        String styleUri = styleLoader.getStyleUri(requireContext());
        Style.Builder builder = styleUri.startsWith("{")
                ? new Style.Builder().fromJson(styleUri)
                : new Style.Builder().fromUri(styleUri);
        mapLibreMap.setStyle(builder, style ->
                mapController.jumpTo(MADAGASCAR_CENTRE, MADAGASCAR_ZOOM));

        // Long-press shows attribution
        mapLibreMap.addOnMapLongClickListener(point -> {
            new AttributionBottomSheet()
                    .show(getChildFragmentManager(), AttributionBottomSheet.TAG);
            return true;
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        binding.mapView.onResume();
        AppCompatActivity activity = (AppCompatActivity) requireActivity();
        if (activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setTitle(R.string.tab_map);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        binding.mapView.onStart();
    }

    @Override
    public void onPause() {
        super.onPause();
        binding.mapView.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
        binding.mapView.onStop();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        binding.mapView.onLowMemory();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (binding != null) {
            binding.mapView.onSaveInstanceState(outState);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding.mapView.onDestroy();
        binding = null;
    }
}
