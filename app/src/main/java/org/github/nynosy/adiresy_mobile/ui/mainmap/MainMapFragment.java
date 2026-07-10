package org.github.nynosy.adiresy_mobile.ui.mainmap;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PointF;
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
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.search.SearchView;
import com.google.android.material.snackbar.Snackbar;

import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.geometry.LatLngBounds;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.OnMapReadyCallback;
import org.maplibre.android.maps.Style;

import org.maplibre.geojson.Feature;

import java.util.Collections;
import java.util.List;

import org.github.nynosy.adiresy_mobile.R;
import org.github.nynosy.adiresy_mobile.data.BookmarkRepository;
import org.github.nynosy.adiresy_mobile.data.Result;
import org.github.nynosy.adiresy_mobile.data.api.dto.AutocompleteDto;
import org.github.nynosy.adiresy_mobile.data.cache.AddressEntity;
import org.github.nynosy.adiresy_mobile.data.cache.BookmarkEntity;
import org.github.nynosy.adiresy_mobile.databinding.FragmentMainMapBinding;
import org.github.nynosy.adiresy_mobile.map.AttributionBottomSheet;
import org.github.nynosy.adiresy_mobile.map.BookmarkPinController;
import org.github.nynosy.adiresy_mobile.map.MapController;
import org.github.nynosy.adiresy_mobile.map.PoiIconFactory;
import org.github.nynosy.adiresy_mobile.map.StyleLoader;
import org.github.nynosy.adiresy_mobile.ui.code.CodeDetailActivity;
import org.github.nynosy.adiresy_mobile.ui.home.CodeCardBottomSheet;
import org.github.nynosy.adiresy_mobile.ui.home.HomeViewModel;
import org.github.nynosy.adiresy_mobile.ui.home.LocationRationaleBottomSheet;
import org.github.nynosy.adiresy_mobile.ui.home.NearbyAdapter;
import org.github.nynosy.adiresy_mobile.ui.map.ExploreBottomSheet;
import org.github.nynosy.adiresy_mobile.ui.map.MapViewModel;
import org.github.nynosy.adiresy_mobile.ui.saved.SharedMapViewModel;
import org.github.nynosy.adiresy_mobile.ui.search.SearchController;
import org.github.nynosy.adiresy_mobile.ui.search.SearchViewModel;

public class MainMapFragment extends Fragment implements OnMapReadyCallback {

    private static final LatLng MADAGASCAR_CENTRE = new LatLng(-18.9100, 46.8691);
    private static final float  ZOOM_OVERVIEW     = 5f;
    private static final float  ZOOM_STREET       = 15f;
    private static final float  ZOOM_DETAIL       = 17f;
    private static final int    PEEK_DP           = 180;

    private FragmentMainMapBinding binding;
    private MapController          mapController;
    private StyleLoader            styleLoader;

    private HomeViewModel      homeViewModel;
    private SearchViewModel    searchViewModel;
    private SharedMapViewModel sharedMapViewModel;
    private MapViewModel       mapViewModel;

    private NearbyAdapter             nearbyAdapter;
    private BottomSheetBehavior<View> sheetBehavior;

    private BookmarkPinController bookmarkPinController;
    private SearchController      searchController;

    private MapLibreMap    mapRef;
    private BookmarkEntity pendingFocusBookmark;

    private final ActivityResultLauncher<String> locationPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (granted) homeViewModel.startLocating();
                        else showPermissionDenied();
                    });

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentMainMapBinding.inflate(inflater, container, false);
        binding.mapView.onCreate(savedInstanceState);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        homeViewModel      = new ViewModelProvider(this).get(HomeViewModel.class);
        searchViewModel    = new ViewModelProvider(this).get(SearchViewModel.class);
        sharedMapViewModel = new ViewModelProvider(requireActivity()).get(SharedMapViewModel.class);
        mapViewModel       = new ViewModelProvider(this).get(MapViewModel.class);
        styleLoader        = new StyleLoader(requireContext());

        setupOfflineBanner();
        setupMap();
        setupBottomSheet();
        setupFab();

        searchController = new SearchController(
                requireContext(),
                binding.recyclerSearch,
                binding.historyHeader,
                binding.labelSearchEmpty,
                binding.searchView.getEditText(),
                binding.btnClearHistory,
                searchViewModel,
                getViewLifecycleOwner(),
                new SearchController.Listener() {
                    @Override
                    public void onResultSelected(AutocompleteDto.Item item) {
                        onSearchResultSelected(item);
                    }
                    @Override
                    public void onHistorySelected(String query) {
                        binding.searchView.getEditText().setText(query);
                        binding.searchView.getEditText().setSelection(query.length());
                    }
                });

        bookmarkPinController = new BookmarkPinController(
                BookmarkRepository.getInstance(requireContext()), requireContext());

        observeLocation();
        observeNearby();
        observeTapResult();
        observeBookmarkFocus();

        binding.searchView.addTransitionListener((sv, prev, next) -> {
            if (next == SearchView.TransitionState.SHOWING) {
                searchController.switchToHistory();
            }
        });
        binding.searchView.setupWithSearchBar(binding.searchBar);
    }

    // ── Map setup ─────────────────────────────────────────────────────────────

    private void setupMap() {
        binding.mapView.getMapAsync(this);
    }

    @Override
    public void onMapReady(@NonNull MapLibreMap map) {
        mapRef = map;
        mapController = new MapController(map, requireContext());
        bookmarkPinController.setMap(map);
        configureCompass(map);

        String styleUri = styleLoader.getStyleUri(requireContext());
        Style.Builder builder = styleUri.startsWith("{")
                ? new Style.Builder().fromJson(styleUri)
                : new Style.Builder().fromUri(styleUri);

        map.setStyle(builder, style -> {
            PoiIconFactory.addAllToStyle(style, requireContext());
            mapController.jumpTo(MADAGASCAR_CENTRE, ZOOM_OVERVIEW);
            bookmarkPinController.onStyleReady(style);
            if (pendingFocusBookmark != null) {
                mapController.centreOn(
                        new LatLng(pendingFocusBookmark.latitude, pendingFocusBookmark.longitude),
                        ZOOM_DETAIL);
                pendingFocusBookmark = null;
                sharedMapViewModel.clearFocus();
            }
        });

        map.addOnCameraIdleListener(() -> bookmarkPinController.onCameraIdle());

        map.addOnMapClickListener(point -> {
            PointF screen = map.getProjection().toScreenLocation(point);

            List<Feature> bookmarkHits =
                    map.queryRenderedFeatures(screen, BookmarkPinController.BOOKMARK_LAYER);
            if (!bookmarkHits.isEmpty()) {
                Feature f = bookmarkHits.get(0);
                Number latProp = f.getNumberProperty("lat");
                Number lngProp = f.getNumberProperty("lng");
                double lat = latProp != null ? latProp.doubleValue() : point.getLatitude();
                double lng = lngProp != null ? lngProp.doubleValue() : point.getLongitude();
                mapController.centreOn(new LatLng(lat, lng), ZOOM_DETAIL);
                binding.loadingOverlay.setVisibility(View.VISIBLE);
                homeViewModel.identifyAt(lat, lng);
                return true;
            }

            List<Feature> hits = map.queryRenderedFeatures(screen, "buildings-fill");
            if (!hits.isEmpty()) {
                binding.loadingOverlay.setVisibility(View.VISIBLE);
                homeViewModel.identifyAt(point.getLatitude(), point.getLongitude());
                return true;
            }
            return false;
        });

        map.addOnMapLongClickListener(point -> {
            new AttributionBottomSheet()
                    .show(getChildFragmentManager(), AttributionBottomSheet.TAG);
            return true;
        });
    }

    // ── Offline banner ────────────────────────────────────────────────────────

    private void setupOfflineBanner() {
        if (!styleLoader.hasOfflineTiles()) {
            binding.offlineBanner.setVisibility(View.VISIBLE);
        }
    }

    // ── Bottom sheet (nearby buildings) ───────────────────────────────────────

    private void setupBottomSheet() {
        sheetBehavior = BottomSheetBehavior.from(binding.bottomSheet);
        sheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        sheetBehavior.setPeekHeight(dpToPx(PEEK_DP), false);

        sheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onSlide(@NonNull View sheet, float offset) {
                float peekPx = dpToPx(PEEK_DP);
                float fabOffset = peekPx * Math.max(0f, offset + 1f);
                binding.fabLocate.setTranslationY(-fabOffset);
            }
            @Override
            public void onStateChanged(@NonNull View sheet, int state) {}
        });

        nearbyAdapter = new NearbyAdapter();
        nearbyAdapter.setOnItemClickListener(address ->
                CodeCardBottomSheet.forAddress(address)
                        .show(getChildFragmentManager(), CodeCardBottomSheet.TAG));
        binding.recyclerNearby.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerNearby.setAdapter(nearbyAdapter);
    }

    // ── FABs ──────────────────────────────────────────────────────────────────

    private void setupFab() {
        binding.fabLocate.setOnClickListener(v -> onLocateMeClicked());
        binding.fabLegend.setOnClickListener(v -> showLegendDialog());
    }

    private void showLegendDialog() {
        Context ctx = requireContext();
        float dp = ctx.getResources().getDisplayMetrics().density;
        int iconSz  = Math.round(20 * dp);
        int padH    = Math.round(24 * dp);
        int padV    = Math.round(8  * dp);
        int gap     = Math.round(14 * dp);
        int rowPadV = Math.round(6  * dp);

        android.widget.LinearLayout container = new android.widget.LinearLayout(ctx);
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        container.setPadding(padH, padV, padH, padV);

        for (PoiIconFactory.Entry entry : PoiIconFactory.allEntries(ctx)) {
            android.widget.LinearLayout row = new android.widget.LinearLayout(ctx);
            row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(0, rowPadV, 0, rowPadV);

            android.widget.ImageView img = new android.widget.ImageView(ctx);
            img.setImageBitmap(entry.icon);
            android.widget.LinearLayout.LayoutParams imgLp =
                    new android.widget.LinearLayout.LayoutParams(iconSz, iconSz);
            imgLp.setMarginEnd(gap);
            img.setLayoutParams(imgLp);

            android.widget.TextView label = new android.widget.TextView(ctx);
            label.setText(entry.labelRes);
            label.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
            label.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));

            row.addView(img);
            row.addView(label);
            container.addView(row);
        }

        android.widget.ScrollView scroll = new android.widget.ScrollView(ctx);
        scroll.addView(container);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.title_legend)
                .setView(scroll)
                .setPositiveButton(R.string.btn_close, null)
                .show();
    }

    private void onLocateMeClicked() {
        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            homeViewModel.startLocating();
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            LocationRationaleBottomSheet sheet = new LocationRationaleBottomSheet();
            sheet.setOnGrantCallback(() ->
                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION));
            sheet.show(getChildFragmentManager(), LocationRationaleBottomSheet.TAG);
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    private void showPermissionDenied() {
        Snackbar.make(binding.getRoot(), R.string.location_permission_denied, Snackbar.LENGTH_LONG)
                .setAction(R.string.btn_open_settings, v -> {
                    Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    i.setData(Uri.fromParts("package", requireContext().getPackageName(), null));
                    startActivity(i);
                }).show();
    }

    // ── Location / nearby observations ────────────────────────────────────────

    private void observeLocation() {
        homeViewModel.getLocationState().observe(getViewLifecycleOwner(), state -> {
            switch (state) {
                case REQUESTING:
                    binding.fabLocate.setEnabled(false);
                    break;
                case ACQUIRED:
                    binding.fabLocate.setEnabled(true);
                    break;
                case FAILED:
                    binding.fabLocate.setEnabled(true);
                    Snackbar.make(binding.getRoot(),
                            R.string.location_failed_outdoors, Snackbar.LENGTH_LONG).show();
                    break;
                default:
                    binding.fabLocate.setEnabled(true);
                    break;
            }
        });

        homeViewModel.getLocation().observe(getViewLifecycleOwner(), loc -> {
            if (loc != null && mapController != null) {
                mapController.centreOn(
                        new LatLng(loc.getLatitude(), loc.getLongitude()), ZOOM_STREET);
            }
            nearbyAdapter.setUserLocation(loc);
        });
    }

    private void observeNearby() {
        homeViewModel.getNearbyBuildings().observe(getViewLifecycleOwner(), result -> {
            if (result == null) return;
            List<AddressEntity> buildings = result.data != null
                    ? result.data : Collections.emptyList();

            nearbyAdapter.submitList(buildings);
            boolean hasBuildings = !buildings.isEmpty();

            binding.labelSheetHeader.setVisibility(View.VISIBLE);
            binding.recyclerNearby.setVisibility(hasBuildings ? View.VISIBLE : View.GONE);
            binding.labelSheetEmpty.setVisibility(hasBuildings ? View.GONE : View.VISIBLE);

            if (!hasBuildings) {
                binding.labelSheetEmpty.setText(result.isError()
                        ? R.string.nearby_empty_offline
                        : R.string.nearby_empty_online);
            }

            sheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        });
    }

    // ── Building tap → code card ──────────────────────────────────────────────

    private void observeTapResult() {
        homeViewModel.getTapResult().observe(getViewLifecycleOwner(), result -> {
            if (result == null) return;
            binding.loadingOverlay.setVisibility(View.GONE);
            if (result.data != null) {
                CodeCardBottomSheet.forAddress(result.data)
                        .show(getChildFragmentManager(), CodeCardBottomSheet.TAG);
            } else if (result.status == Result.Status.ERROR_NETWORK) {
                Snackbar.make(binding.getRoot(),
                        R.string.tap_no_network, Snackbar.LENGTH_LONG).show();
            } else {
                Snackbar.make(binding.getRoot(),
                        R.string.tap_no_address, Snackbar.LENGTH_SHORT).show();
            }
        });
    }

    // ── Search result handling ────────────────────────────────────────────────

    private void onSearchResultSelected(AutocompleteDto.Item item) {
        if (item.label != null) searchViewModel.recordSearch(item.label);
        binding.searchView.hide();

        if ("code".equals(item.type) && item.code != null) {
            Intent intent = new Intent(requireContext(), CodeDetailActivity.class);
            intent.putExtra(CodeDetailActivity.EXTRA_CODE, item.code);
            startActivity(intent);
        } else if (item.lat != null && item.lng != null && mapRef != null) {
            if (item.bbox != null && item.bbox.length == 4) {
                LatLngBounds bounds = new LatLngBounds.Builder()
                        .include(new LatLng(item.bbox[1], item.bbox[0]))
                        .include(new LatLng(item.bbox[3], item.bbox[2]))
                        .build();
                mapRef.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80));
            } else {
                mapController.centreOn(new LatLng(item.lat, item.lng), ZOOM_STREET);
            }

            if (isAdminType(item.type) && item.uuid != null && item.level != null) {
                String name   = item.label;
                String parent = null;
                if (item.label != null) {
                    int sep = item.label.indexOf(" · ");
                    if (sep > 0) {
                        name   = item.label.substring(0, sep).trim();
                        parent = item.label.substring(sep + 3).trim();
                    }
                }
                ExploreBottomSheet.forUnit(item.uuid, item.level, name, parent)
                        .show(getChildFragmentManager(), ExploreBottomSheet.TAG);
            }
        }
    }

    // ── Bookmark focus (cross-tab navigation) ─────────────────────────────────

    private void observeBookmarkFocus() {
        sharedMapViewModel.focusBookmark.observe(getViewLifecycleOwner(), bookmark -> {
            if (bookmark == null) return;
            if (mapRef == null || mapRef.getStyle() == null) {
                pendingFocusBookmark = bookmark;
                return;
            }
            mapController.centreOn(
                    new LatLng(bookmark.latitude, bookmark.longitude), ZOOM_DETAIL);
            sharedMapViewModel.clearFocus();
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void configureCompass(MapLibreMap map) {
        float dp = requireContext().getResources().getDisplayMetrics().density;
        map.getUiSettings().setCompassGravity(android.view.Gravity.TOP | android.view.Gravity.END);
        map.getUiSettings().setCompassMargins(0, (int) (88 * dp), (int) (10 * dp), 0);
        map.getUiSettings().setCompassImage(
                androidx.appcompat.content.res.AppCompatResources.getDrawable(
                        requireContext(), R.drawable.ic_compass));
    }

    private static boolean isAdminType(String type) {
        return "region".equals(type) || "district".equals(type)
                || "commune".equals(type) || "fokontany".equals(type);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * requireContext().getResources().getDisplayMetrics().density);
    }

    // ── MapView lifecycle ─────────────────────────────────────────────────────

    @Override public void onStart()     { super.onStart();     if (binding != null) binding.mapView.onStart(); }
    @Override public void onResume()    { super.onResume();    if (binding != null) binding.mapView.onResume(); }
    @Override public void onPause()     { super.onPause();     if (binding != null) binding.mapView.onPause(); }
    @Override public void onStop()      { super.onStop();      if (binding != null) binding.mapView.onStop(); }
    @Override public void onLowMemory() { super.onLowMemory(); if (binding != null) binding.mapView.onLowMemory(); }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        if (binding != null) binding.mapView.onSaveInstanceState(out);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        homeViewModel.cancelLocating();
        bookmarkPinController.shutdown();
        binding.mapView.onDestroy();
        binding = null;
    }
}
