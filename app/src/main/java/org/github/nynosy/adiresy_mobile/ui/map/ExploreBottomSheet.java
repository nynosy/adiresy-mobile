package org.github.nynosy.adiresy_mobile.ui.map;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import org.github.nynosy.adiresy_mobile.R;
import org.github.nynosy.adiresy_mobile.data.cache.AdminUnitEntity;
import org.github.nynosy.adiresy_mobile.databinding.BottomSheetExploreBinding;

public class ExploreBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "ExploreBottomSheet";

    private static final String ARG_UUID   = "start_uuid";
    private static final String ARG_LEVEL  = "start_level";
    private static final String ARG_NAME   = "start_name";
    private static final String ARG_PARENT = "start_parent";

    /** Open at region list (normal explore flow). */
    public static ExploreBottomSheet newInstance() {
        return new ExploreBottomSheet();
    }

    /**
     * Open pre-navigated to a specific admin unit from a search result.
     * @param uuid   UUID of the admin unit
     * @param level  "region" | "district" | "commune" | "fokontany"
     * @param name   Display name (e.g. "Ambositra")
     * @param parent Parent name for the subtitle (e.g. "Amoron'i Mania") — may be null
     */
    public static ExploreBottomSheet forUnit(String uuid, String level, String name, String parent) {
        Bundle args = new Bundle();
        args.putString(ARG_UUID,   uuid);
        args.putString(ARG_LEVEL,  level);
        args.putString(ARG_NAME,   name);
        if (parent != null) args.putString(ARG_PARENT, parent);
        ExploreBottomSheet sheet = new ExploreBottomSheet();
        sheet.setArguments(args);
        return sheet;
    }

    private BottomSheetExploreBinding binding;
    private MapViewModel viewModel;
    private AdminUnitAdapter adapter;

    private final Deque<AdminUnitEntity> breadcrumb = new ArrayDeque<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetExploreBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(requireParentFragment()).get(MapViewModel.class);

        adapter = new AdminUnitAdapter();
        adapter.setOnItemClickListener(this::onUnitSelected);
        binding.recyclerAdmin.setAdapter(adapter);

        binding.btnBack.setOnClickListener(v -> navigateBack());

        viewModel.getAdminUnits().observe(getViewLifecycleOwner(), result -> {
            if (result == null) return;
            List<AdminUnitEntity> items = result.data != null ? result.data : Collections.emptyList();
            adapter.submitList(items);
            boolean empty = items.isEmpty();
            binding.recyclerAdmin.setVisibility(empty ? View.GONE : View.VISIBLE);
            binding.labelAdminEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            if (empty) {
                binding.labelAdminEmpty.setText(
                        result.isError() ? R.string.error_no_network : R.string.explore_empty);
            }
        });

        Bundle args = getArguments();
        if (args != null && args.containsKey(ARG_UUID)) {
            showUnitHeader(args);
            loadChildrenOf(args.getString(ARG_LEVEL), args.getString(ARG_UUID));
        } else {
            loadRegions();
        }
    }

    private void showUnitHeader(Bundle args) {
        String level  = args.getString(ARG_LEVEL, "");
        String name   = args.getString(ARG_NAME, "");
        String parent = args.getString(ARG_PARENT);

        binding.layoutUnitHeader.setVisibility(View.VISIBLE);
        binding.labelUnitType.setText(levelLabel(level).toUpperCase());
        binding.labelUnitName.setText(name);

        if (parent != null && !parent.isEmpty()) {
            binding.labelUnitParent.setVisibility(View.VISIBLE);
            binding.labelUnitParent.setText(parent);
        }

        // Show children level title, back button hidden (top of this search result)
        binding.labelLevelTitle.setText(childLevelLabel(level));
        binding.btnBack.setVisibility(View.GONE);
    }

    private void loadChildrenOf(String level, String uuid) {
        if (level == null) { loadRegions(); return; }
        switch (level) {
            case "region":    viewModel.loadDistricts(uuid);  break;
            case "district":  viewModel.loadCommunes(uuid);   break;
            case "commune":   viewModel.loadFokontany(uuid);  break;
            default:          loadRegions();                  break;
        }
    }

    private void loadRegions() {
        breadcrumb.clear();
        binding.layoutUnitHeader.setVisibility(View.GONE);
        binding.btnBack.setVisibility(View.GONE);
        binding.labelLevelTitle.setText(R.string.label_region);
        viewModel.loadRegions();
    }

    private void onUnitSelected(AdminUnitEntity unit) {
        breadcrumb.push(unit);
        binding.layoutUnitHeader.setVisibility(View.GONE);
        binding.btnBack.setVisibility(View.VISIBLE);
        binding.labelLevelTitle.setText(childLevelLabel(unit.type));

        switch (unit.type) {
            case "region":   viewModel.loadDistricts(unit.uuid);  break;
            case "district": viewModel.loadCommunes(unit.uuid);   break;
            case "commune":  viewModel.loadFokontany(unit.uuid);  break;
            default:
                viewModel.showBoundary(unit);
                dismiss();
                break;
        }
    }

    private void navigateBack() {
        if (breadcrumb.isEmpty()) {
            // If we arrived from search args, dismiss; otherwise go to region list
            if (getArguments() != null && getArguments().containsKey(ARG_UUID)) {
                dismiss();
            } else {
                loadRegions();
            }
            return;
        }
        breadcrumb.pop();
        if (breadcrumb.isEmpty()) {
            // Back to the search-result root (if args present) or region list
            if (getArguments() != null && getArguments().containsKey(ARG_UUID)) {
                Bundle args = getArguments();
                showUnitHeader(args);
                loadChildrenOf(args.getString(ARG_LEVEL), args.getString(ARG_UUID));
            } else {
                loadRegions();
            }
        } else {
            AdminUnitEntity parent = breadcrumb.peek();
            if (parent == null) { loadRegions(); return; }
            binding.labelLevelTitle.setText(childLevelLabel(parent.type));
            switch (parent.type) {
                case "region":   viewModel.loadDistricts(parent.uuid);  break;
                case "district": viewModel.loadCommunes(parent.uuid);   break;
                case "commune":  viewModel.loadFokontany(parent.uuid);  break;
            }
        }
    }

    private String levelLabel(String type) {
        if (type == null) return "";
        switch (type) {
            case "region" -> {
                return getString(R.string.label_region);
            }
            case "district" -> {
                return getString(R.string.label_district);
            }
            case "commune" -> {
                return getString(R.string.label_commune);
            }
            case "fokontany" -> {
                return getString(R.string.label_fokontany);
            }
            default -> {
                return type;
            }
        }
    }

    private String childLevelLabel(String parentType) {
        if (parentType == null) return getString(R.string.label_region);
        switch (parentType) {
            case "region" -> {
                return getString(R.string.label_district);
            }
            case "district" -> {
                return getString(R.string.label_commune);
            }
            case "commune" -> {
                return getString(R.string.label_fokontany);
            }
            default -> {
                return getString(R.string.label_fokontany);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
