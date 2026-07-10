package org.github.nynosy.adiresy_mobile.ui.search;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import java.util.List;

import org.github.nynosy.adiresy_mobile.data.AdiresyRepository;
import org.github.nynosy.adiresy_mobile.data.Result;
import org.github.nynosy.adiresy_mobile.data.api.dto.AutocompleteDto;
import org.github.nynosy.adiresy_mobile.data.cache.SearchHistoryEntity;

public class SearchViewModel extends AndroidViewModel {

    private static final long DEBOUNCE_MS = 300;

    private final AdiresyRepository repository;
    private final MutableLiveData<String> queryInput = new MutableLiveData<>("");
    private final Handler debounceHandler = new Handler(Looper.getMainLooper());

    public SearchViewModel(@NonNull Application application) {
        super(application);
        repository = AdiresyRepository.getInstance(application);
    }

    /** Debounced autocomplete results. */
    public LiveData<Result<List<AutocompleteDto.Item>>> getSearchResults() {
        return Transformations.switchMap(queryInput, q ->
                (q != null && !q.trim().isEmpty())
                        ? repository.autocomplete(q.trim())
                        : new MutableLiveData<>());
    }

    public LiveData<List<SearchHistoryEntity>> getSearchHistory() {
        return repository.getSearchHistory();
    }

    public void onQueryChanged(String text) {
        debounceHandler.removeCallbacksAndMessages(null);
        debounceHandler.postDelayed(() -> queryInput.setValue(text), DEBOUNCE_MS);
    }

    public void clearQuery() {
        debounceHandler.removeCallbacksAndMessages(null);
        queryInput.setValue("");
    }

    public void recordSearch(String query) {
        repository.recordSearch(query);
    }

    public void clearHistory() {
        repository.clearSearchHistory();
    }

    @Override
    protected void onCleared() {
        debounceHandler.removeCallbacksAndMessages(null);
    }
}
