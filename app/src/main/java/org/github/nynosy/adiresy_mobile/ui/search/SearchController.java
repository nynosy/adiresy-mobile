package org.github.nynosy.adiresy_mobile.ui.search;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
import java.util.List;

import org.github.nynosy.adiresy_mobile.R;
import org.github.nynosy.adiresy_mobile.data.api.dto.AutocompleteDto;

public class SearchController {

    public interface Listener {
        void onResultSelected(AutocompleteDto.Item item);
        void onHistorySelected(String query);
    }

    private final Context        context;
    private final RecyclerView   recycler;
    private final View           historyHeader;
    private final TextView       emptyLabel;
    private final EditText       editText;
    private final SearchViewModel viewModel;

    private final SearchAdapter  searchAdapter  = new SearchAdapter();
    private final HistoryAdapter historyAdapter = new HistoryAdapter();

    private boolean showingHistory = true;

    public SearchController(Context context,
                            RecyclerView recycler,
                            View historyHeader,
                            TextView emptyLabel,
                            EditText editText,
                            View clearButton,
                            SearchViewModel viewModel,
                            LifecycleOwner owner,
                            Listener listener) {
        this.context       = context;
        this.recycler      = recycler;
        this.historyHeader = historyHeader;
        this.emptyLabel    = emptyLabel;
        this.editText      = editText;
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
                String q = editText.getText() != null ? editText.getText().toString() : "";
                emptyLabel.setText(context.getString(R.string.search_empty, q));
            }
        });

        viewModel.getSearchHistory().observe(owner, history -> {
            if (!showingHistory) return;
            boolean hasHistory = history != null && !history.isEmpty();
            historyHeader.setVisibility(hasHistory ? View.VISIBLE : View.GONE);
            if (hasHistory) {
                historyAdapter.submitList(history);
                recycler.setAdapter(historyAdapter);
                recycler.setVisibility(View.VISIBLE);
            } else {
                recycler.setVisibility(View.GONE);
            }
        });

        switchToHistory();
    }

    public void switchToHistory() {
        showingHistory = true;
        recycler.setAdapter(historyAdapter);
        emptyLabel.setVisibility(View.GONE);
        viewModel.clearQuery();
    }

    public void switchToResults() {
        if (showingHistory) {
            showingHistory = false;
            recycler.setAdapter(searchAdapter);
            historyHeader.setVisibility(View.GONE);
        }
    }
}
