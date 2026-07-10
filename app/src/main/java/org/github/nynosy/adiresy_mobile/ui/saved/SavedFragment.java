package org.github.nynosy.adiresy_mobile.ui.saved;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.github.nynosy.adiresy_mobile.R;
import org.github.nynosy.adiresy_mobile.databinding.FragmentSavedBinding;

public class SavedFragment extends Fragment {

    private FragmentSavedBinding binding;
    private SavedViewModel viewModel;
    private BookmarkListAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSavedBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(requireActivity()).get(SavedViewModel.class);

        adapter = new BookmarkListAdapter();
        adapter.setListener(item -> {
            BookmarkListDetailFragment detail = BookmarkListDetailFragment.forList(
                    item.list.id, item.list.name, item.list.emoji);
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, detail)
                    .addToBackStack(null)
                    .commit();
        });

        binding.recyclerLists.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerLists.setAdapter(adapter);

        viewModel.getAllLists().observe(getViewLifecycleOwner(), lists -> {
            boolean empty = lists == null || lists.isEmpty();
            binding.emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
            binding.recyclerLists.setVisibility(empty ? View.GONE : View.VISIBLE);
            if (!empty) adapter.submitList(lists);
        });

        binding.fabNewList.setOnClickListener(v -> showCreateListDialog());
    }

    private void showCreateListDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_create_edit_list, null);

        android.widget.TextView btnEmoji =
                dialogView.findViewById(R.id.btn_emoji);
        com.google.android.material.textfield.TextInputEditText inputName =
                dialogView.findViewById(R.id.input_name);
        com.google.android.material.textfield.TextInputEditText inputDesc =
                dialogView.findViewById(R.id.input_desc);

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
