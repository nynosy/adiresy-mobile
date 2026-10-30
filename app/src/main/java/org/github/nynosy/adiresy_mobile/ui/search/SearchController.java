package org.github.nynosy.adiresy_mobile.ui.search;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.StringRes;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
import java.util.List;

import org.github.nynosy.adiresy_mobile.R;
import org.github.nynosy.adiresy_mobile.data.Result;
import org.github.nynosy.adiresy_mobile.data.api.dto.AutocompleteDto;
import org.github.nynosy.adiresy_mobile.data.cache.SearchHistoryEntity;

public class SearchController {

    public interface Listener {
        void onResultSelected(AutocompleteDto.Item item);
        void onHistorySelected(String query);
    }

    private final RecyclerView   recycler;
    private final View           historyHeader;
    private final TextView       emptyLabel;
    private final SearchViewModel viewModel;

    private final SearchAdapter  searchAdapter  = new SearchAdapter();
    private final HistoryAdapter historyAdapter = new HistoryAdapter();

    private boolean showingHistory = true;
    private List<SearchHistoryEntity> lastHistory = Collections.emptyList();

    public SearchController(Context context,
                            RecyclerView recycler,
                            View historyHeader,
                            TextView emptyLabel,
                            EditText editText,
                            View clearButton,
                            SearchViewModel viewModel,
                            LifecycleOwner owner,
                            Listener listener) {
        this.recycler      = recycler;
        this.historyHeader = historyHeader;
        this.emptyLabel    = emptyLabel;
        this.viewModel     = viewModel;

        recycler.setLayoutManager(new LinearLayoutManager(context));
        searchAdapter.setOnItemClickListener(listener::onResultSelected);
        historyAdapter.setOnItemClickListener(listener::onHistorySelected);

        editText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int c, int a) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String text = s.toString().trim();
                if (text.isEmpty()) switchToHistory();
                else { switchToResults(); viewModel.onQueryChanged(text); }
            }
        });

        clearButton.setOnClickListener(v -> viewModel.clearHistory());

        viewModel.getSearchResults().observe(owner, result -> {
            if (result == null || showingHistory) return;
            List<AutocompleteDto.Item> items =
                    result.data != null ? result.data : Collections.emptyList();
            searchAdapter.submitList(items);
            boolean empty = items.isEmpty();
            recycler.setVisibility(empty ? View.GONE : View.VISIBLE);
            emptyLabel.setVisibility(empty ? View.VISIBLE : View.GONE);
            if (empty) {
                if (result.isError()) {
                    emptyLabel.setText(errorMessageRes(result));
                } else {
                    String q = editText.getText() != null ? editText.getText().toString() : "";
                    emptyLabel.setText(context.getString(R.string.search_empty, q));
                }
            }
        });

        viewModel.getSearchHistory().observe(owner, history -> {
            lastHistory = history != null ? history : Collections.emptyList();
            historyAdapter.submitList(lastHistory);
            if (showingHistory) applyHistoryVisibility();
        });

        switchToHistory();
    }

    public void switchToHistory() {
        showingHistory = true;
        recycler.setAdapter(historyAdapter);
        emptyLabel.setVisibility(View.GONE);
        applyHistoryVisibility();
        viewModel.clearQuery();
    }

    /** Shows/hides the header and list based on the last-seen history list.
     *  Used both by the history LiveData observer and by switchToHistory() —
     *  the observer only re-runs on a fresh emission, which switching modes
     *  alone doesn't trigger, so mode switches need their own call to stay in
     *  sync. Reads {@code lastHistory} rather than historyAdapter.getItemCount():
     *  ListAdapter diffs submitList() asynchronously, so the adapter's count
     *  can still reflect the previous list for a moment after submitList(). */
    private void applyHistoryVisibility() {
        boolean hasHistory = !lastHistory.isEmpty();
        historyHeader.setVisibility(hasHistory ? View.VISIBLE : View.GONE);
        recycler.setVisibility(hasHistory ? View.VISIBLE : View.GONE);
    }

    public void switchToResults() {
        if (showingHistory) {
            showingHistory = false;
            recycler.setAdapter(searchAdapter);
            historyHeader.setVisibility(View.GONE);
        }
    }

    @StringRes
    private static int errorMessageRes(Result<?> result) {
        if (result.isNetworkError()) return R.string.error_no_network;
        if (result.isRateLimited()) return R.string.error_rate_limited;
        return R.string.error_generic;
    }
}
