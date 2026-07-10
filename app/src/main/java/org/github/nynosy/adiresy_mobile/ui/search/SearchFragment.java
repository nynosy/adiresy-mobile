package org.github.nynosy.adiresy_mobile.ui.search;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import org.github.nynosy.adiresy_mobile.R;
import org.github.nynosy.adiresy_mobile.data.api.dto.AutocompleteDto;
import org.github.nynosy.adiresy_mobile.databinding.FragmentSearchBinding;
import org.github.nynosy.adiresy_mobile.ui.code.CodeDetailActivity;

public class SearchFragment extends Fragment {

    private FragmentSearchBinding binding;
    private SearchViewModel       viewModel;
    private SearchController      searchController;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSearchBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this).get(SearchViewModel.class);

        searchController = new SearchController(
                requireContext(),
                binding.recyclerResults,
                binding.historyHeader,
                binding.labelEmpty,
                binding.inputSearch,
                binding.btnClearHistory,
                viewModel,
                getViewLifecycleOwner(),
                new SearchController.Listener() {
                    @Override
                    public void onResultSelected(AutocompleteDto.Item item) {
                        onSearchResultSelected(item);
                    }
                    @Override
                    public void onHistorySelected(String query) {
                        if (binding.inputSearch != null) {
                            binding.inputSearch.setText(query);
                            binding.inputSearch.setSelection(query.length());
                        }
                    }
                });
    }

    private void onSearchResultSelected(AutocompleteDto.Item item) {
        if ("code".equals(item.type) && item.code != null) {
            viewModel.recordSearch(item.label);
            Intent intent = new Intent(requireContext(), CodeDetailActivity.class);
            intent.putExtra(CodeDetailActivity.EXTRA_CODE, item.code);
            startActivity(intent);
        }
        // For place/admin types: map navigation is wired in M9
    }

    @Override
    public void onResume() {
        super.onResume();
        AppCompatActivity activity = (AppCompatActivity) requireActivity();
        if (activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setTitle(R.string.tab_search);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
