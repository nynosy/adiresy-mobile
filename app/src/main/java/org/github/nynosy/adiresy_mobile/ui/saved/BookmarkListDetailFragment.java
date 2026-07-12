package org.github.nynosy.adiresy_mobile.ui.saved;

import android.content.Intent;
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
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import org.github.nynosy.adiresy_mobile.R;
import org.github.nynosy.adiresy_mobile.data.cache.BookmarkEntity;
import org.github.nynosy.adiresy_mobile.data.cache.BookmarkListEntity;
import org.github.nynosy.adiresy_mobile.databinding.FragmentBookmarkListDetailBinding;

public class BookmarkListDetailFragment extends Fragment {

    private static final String ARG_LIST_ID   = "list_id";
    private static final String ARG_LIST_NAME = "list_name";
    private static final String ARG_LIST_EMOJI= "list_emoji";

    private FragmentBookmarkListDetailBinding binding;
    private BookmarkListDetailViewModel viewModel;
    private SharedMapViewModel sharedMapViewModel;
    private BookmarkAdapter adapter;

    private long listId;
    private String listName;
    private String listEmoji;

    public static BookmarkListDetailFragment forList(long id, String name, String emoji) {
        Bundle args = new Bundle();
        args.putLong(ARG_LIST_ID,    id);
        args.putString(ARG_LIST_NAME, name);
        args.putString(ARG_LIST_EMOJI, emoji);
        BookmarkListDetailFragment f = new BookmarkListDetailFragment();
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentBookmarkListDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Bundle args = requireArguments();
        listId    = args.getLong(ARG_LIST_ID);
        listName  = args.getString(ARG_LIST_NAME, "");
        listEmoji = args.getString(ARG_LIST_EMOJI, "📍");

        viewModel         = new ViewModelProvider(this).get(BookmarkListDetailViewModel.class);
        sharedMapViewModel= new ViewModelProvider(requireActivity()).get(SharedMapViewModel.class);

        setupToolbar();
        setupRecyclerView();

        viewModel.getBookmarks(listId).observe(getViewLifecycleOwner(), bookmarks -> {
            boolean empty = bookmarks == null || bookmarks.isEmpty();
            binding.emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
            binding.recyclerBookmarks.setVisibility(empty ? View.GONE : View.VISIBLE);
            if (!empty) adapter.submitList(bookmarks);
        });
    }

    private void setupToolbar() {
        binding.toolbar.setTitle(listEmoji + " " + listName);
        binding.toolbar.setNavigationIcon(
                androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        binding.toolbar.setNavigationOnClickListener(v ->
                requireActivity().getSupportFragmentManager().popBackStack());

        binding.toolbar.inflateMenu(R.menu.menu_bookmark_list_detail);
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_edit_list) {
                showEditListDialog();
                return true;
            }
            if (item.getItemId() == R.id.action_delete_list) {
                showDeleteListDialog();
                return true;
            }
            return false;
        });
    }

    private void setupRecyclerView() {
        adapter = new BookmarkAdapter();
        adapter.setListener(new BookmarkAdapter.OnBookmarkActionListener() {
            @Override
            public void onBookmarkClicked(BookmarkEntity bookmark) {
                // Post focus; MainActivity.observeBookmarkFocus switches tab + clears back stack
                sharedMapViewModel.focusOn(bookmark);
            }

            @Override
            public void onEditDescription(BookmarkEntity bookmark) {
                showEditDescriptionDialog(bookmark);
            }

            @Override
            public void onMoveToList(BookmarkEntity bookmark) {
                showMoveDialog(bookmark);
            }

            @Override
            public void onShare(BookmarkEntity bookmark) {
                shareBookmark(bookmark);
            }

            @Override
            public void onDelete(BookmarkEntity bookmark) {
                viewModel.deleteBookmark(bookmark, () -> {
                    if (getView() == null) return;
                    Snackbar.make(requireView(),
                            getString(R.string.bookmark_removed_from, listName),
                            Snackbar.LENGTH_LONG)
                            .setAction(R.string.bookmark_undo, v ->
                                    viewModel.insertBookmark(bookmark, null))
                            .show();
                });
            }
        });

        binding.recyclerBookmarks.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerBookmarks.setAdapter(adapter);
    }

    private void showEditListDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_create_edit_list, null);

        android.widget.TextView btnEmoji = dialogView.findViewById(R.id.btn_emoji);
        TextInputEditText inputName = dialogView.findViewById(R.id.input_name);
        TextInputEditText inputDesc = dialogView.findViewById(R.id.input_desc);

        final String[] selectedEmoji = {listEmoji};
        btnEmoji.setText(listEmoji);
        inputName.setText(listName);
        btnEmoji.setOnClickListener(v ->
                EmojiPickerDialog.show(requireContext(), selectedEmoji[0], emoji -> {
                    selectedEmoji[0] = emoji;
                    btnEmoji.setText(emoji);
                }));

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.edit_list_title)
                .setView(dialogView)
                .setPositiveButton(R.string.btn_save, (dialog, which) -> {
                    String name = inputName.getText() != null
                            ? inputName.getText().toString().trim() : "";
                    if (name.isEmpty()) return;
                    String desc = inputDesc.getText() != null
                            ? inputDesc.getText().toString().trim() : null;
                    if (desc != null && desc.isEmpty()) desc = null;

                    BookmarkListEntity updated = new BookmarkListEntity();
                    updated.id          = listId;
                    updated.name        = name;
                    updated.description = desc;
                    updated.emoji       = selectedEmoji[0];
                    updated.createdAt   = 0; // Room @Update doesn't care about this if set

                    viewModel.updateList(updated, () -> {
                        listName  = name;
                        listEmoji = selectedEmoji[0];
                        binding.toolbar.setTitle(listEmoji + " " + listName);
                    });
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void showDeleteListDialog() {
        String body = getString(R.string.delete_list_body_empty, listName);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.delete_list_title)
                .setMessage(body)
                .setPositiveButton(R.string.btn_delete, (dialog, which) -> {
                    BookmarkListEntity list = new BookmarkListEntity();
                    list.id = listId;
                    viewModel.deleteList(list, () ->
                            requireActivity().getSupportFragmentManager().popBackStack());
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void showEditDescriptionDialog(BookmarkEntity bookmark) {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_create_edit_list, null); // reuse layout
        // Hide the emoji and name fields — just need the description field
        dialogView.findViewById(R.id.btn_emoji).setVisibility(View.GONE);
        dialogView.findViewById(R.id.layout_name).setVisibility(View.GONE);
        TextInputEditText inputDesc = dialogView.findViewById(R.id.input_desc);
        if (bookmark.userDescription != null) inputDesc.setText(bookmark.userDescription);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.edit_description_title)
                .setView(dialogView)
                .setPositiveButton(R.string.btn_save, (dialog, which) -> {
                    String desc = inputDesc.getText() != null
                            ? inputDesc.getText().toString().trim() : null;
                    if (desc != null && desc.isEmpty()) desc = null;
                    viewModel.updateDescription(bookmark.id, desc, null);
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void showMoveDialog(BookmarkEntity bookmark) {
        // Open the SaveToList sheet in move mode — it handles the move logic
        // For simplicity we reuse SaveToListBottomSheet; after selection it calls
        // saveBookmark which detects existing = different list and calls moveToList.
        SaveToListBottomSheet.forAddress(
                bookmark.canonicalCode,
                bookmark.latitude, bookmark.longitude,
                bookmark.fokontanyName, bookmark.communeName,
                bookmark.districtName, bookmark.regionName)
        .show(getChildFragmentManager(), SaveToListBottomSheet.TAG);
    }

    private void shareBookmark(BookmarkEntity bookmark) {
        StringBuilder sb = new StringBuilder();
        if (bookmark.name != null && !bookmark.name.isEmpty()) {
            sb.append(bookmark.name).append("  ");
        }
        sb.append(bookmark.canonicalCode);
        if (bookmark.fokontanyName != null && !bookmark.fokontanyName.isEmpty()) {
            sb.append("\n").append(bookmark.fokontanyName);
            if (bookmark.communeName != null && !bookmark.communeName.isEmpty())
                sb.append(", ").append(bookmark.communeName);
            if (bookmark.districtName != null && !bookmark.districtName.isEmpty())
                sb.append(", ").append(bookmark.districtName);
            if (bookmark.regionName != null && !bookmark.regionName.isEmpty())
                sb.append(", ").append(bookmark.regionName);
        }
        if (bookmark.userDescription != null && !bookmark.userDescription.isEmpty()) {
            sb.append("\n").append(bookmark.userDescription);
        }
        sb.append("\n").append(getString(R.string.share_text, bookmark.canonicalCode, bookmark.canonicalCode));

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(intent,
                getString(R.string.share_chooser_title)));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
