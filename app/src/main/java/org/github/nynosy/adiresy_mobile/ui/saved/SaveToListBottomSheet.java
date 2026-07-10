package org.github.nynosy.adiresy_mobile.ui.saved;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import org.github.nynosy.adiresy_mobile.R;
import org.github.nynosy.adiresy_mobile.data.BookmarkRepository;
import org.github.nynosy.adiresy_mobile.data.cache.BookmarkListWithCount;
import org.github.nynosy.adiresy_mobile.databinding.BottomSheetSaveToListBinding;

public class SaveToListBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "SaveToList";

    private static final String ARG_CODE      = "code";
    private static final String ARG_LAT       = "lat";
    private static final String ARG_LNG       = "lng";
    private static final String ARG_FOKONTANY = "fokontany";
    private static final String ARG_COMMUNE   = "commune";
    private static final String ARG_DISTRICT  = "district";
    private static final String ARG_REGION    = "region";

    private BottomSheetSaveToListBinding binding;
    private SaveToListAdapter adapter;
    private SavedViewModel viewModel;
    private BookmarkRepository repository;

    private BookmarkListWithCount selectedList;

    public static SaveToListBottomSheet forAddress(String code, double lat, double lng,
                                                   String fokontany, String commune,
                                                   String district, String region) {
        Bundle args = new Bundle();
        args.putString(ARG_CODE, code);
        args.putDouble(ARG_LAT, lat);
        args.putDouble(ARG_LNG, lng);
        args.putString(ARG_FOKONTANY, fokontany);
        args.putString(ARG_COMMUNE, commune);
        args.putString(ARG_DISTRICT, district);
        args.putString(ARG_REGION, region);
        SaveToListBottomSheet sheet = new SaveToListBottomSheet();
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetSaveToListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        repository = BookmarkRepository.getInstance(requireContext());
        viewModel  = new ViewModelProvider(requireActivity()).get(SavedViewModel.class);

        adapter = new SaveToListAdapter();
        adapter.setListener(new SaveToListAdapter.OnListSelectedListener() {
            @Override
            public void onListSelected(BookmarkListWithCount item) {
                selectedList = item;
                showDescriptionInput();
            }

            @Override
            public void onNewListClicked() {
                // Do NOT dismiss first — the sheet stays open so the user can
                // pick the newly created list from the auto-updated RecyclerView.
                showCreateListDialog();
            }
        });

        binding.recyclerLists.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerLists.setAdapter(adapter);

        viewModel.getAllLists().observe(getViewLifecycleOwner(), lists ->
                adapter.setItems(lists));

        binding.btnConfirmSave.setOnClickListener(v -> performSave());
    }

    private void showDescriptionInput() {
        binding.layoutName.setVisibility(View.VISIBLE);
        binding.layoutDescription.setVisibility(View.VISIBLE);
        binding.btnConfirmSave.setVisibility(View.VISIBLE);
    }

    private void performSave() {
        if (selectedList == null) return;

        Bundle args    = requireArguments();
        String code    = args.getString(ARG_CODE, "");
        double lat     = args.getDouble(ARG_LAT, 0);
        double lng     = args.getDouble(ARG_LNG, 0);
        String foko    = args.getString(ARG_FOKONTANY, "");
        String commune = args.getString(ARG_COMMUNE, "");
        String dist    = args.getString(ARG_DISTRICT, "");
        String region  = args.getString(ARG_REGION, "");
        String name    = binding.inputName.getText() != null
                ? binding.inputName.getText().toString().trim() : null;
        if (name != null && name.isEmpty()) name = null;
        String desc    = binding.inputDescription.getText() != null
                ? binding.inputDescription.getText().toString().trim() : null;
        if (desc != null && desc.isEmpty()) desc = null;

        repository.saveBookmark(code, lat, lng, foko, commune, dist, region,
                name, desc, selectedList.list.id, result -> {
                    dismiss();
                    handleSaveResult(result);
                });
    }

    private void handleSaveResult(BookmarkRepository.SaveResult result) {
        View root = requireActivity().findViewById(android.R.id.content);
        if (root == null) return;

        switch (result.status) {
            case OK:
                Snackbar.make(root,
                        getString(R.string.bookmark_saved_to, result.listName),
                        Snackbar.LENGTH_SHORT).show();
                break;
            case WARN:
                Snackbar.make(root,
                        getString(R.string.bookmark_saved_to, result.listName),
                        Snackbar.LENGTH_LONG)
                        .setText(getString(R.string.bookmark_limit_warning, result.total))
                        .show();
                break;
            case LIMIT_REACHED:
                Snackbar.make(root, R.string.bookmark_limit_reached,
                        Snackbar.LENGTH_LONG).show();
                break;
            case MOVED:
                Snackbar.make(root,
                        getString(R.string.bookmark_moved_to, result.listName),
                        Snackbar.LENGTH_SHORT).show();
                break;
        }
    }

    private void showCreateListDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_create_edit_list, null);

        com.google.android.material.textfield.TextInputEditText inputName =
                dialogView.findViewById(R.id.input_name);
        com.google.android.material.textfield.TextInputEditText inputDesc =
                dialogView.findViewById(R.id.input_desc);
        android.widget.TextView btnEmoji = dialogView.findViewById(R.id.btn_emoji);

        final String[] selectedEmoji = {"📍"};
        btnEmoji.setText(selectedEmoji[0]);
        btnEmoji.setOnClickListener(v ->
                EmojiPickerDialog.show(requireContext(), selectedEmoji[0], emoji -> {
                    selectedEmoji[0] = emoji;
                    btnEmoji.setText(emoji);
                }));

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.create_list_title)
                .setView(dialogView)
                .setPositiveButton(R.string.btn_save, (dialog, which) -> {
                    String name = inputName.getText() != null
                            ? inputName.getText().toString().trim() : "";
                    if (name.isEmpty()) return;
                    String desc = inputDesc.getText() != null
                            ? inputDesc.getText().toString().trim() : null;
                    if (desc != null && desc.isEmpty()) desc = null;
                    // Sheet stays open; LiveData updates the RecyclerView automatically
                    // so user can immediately tap the new list.
                    viewModel.createList(name, desc, selectedEmoji[0], null);
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
