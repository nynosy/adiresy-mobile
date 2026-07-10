package org.github.nynosy.adiresy_mobile.ui.saved;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import java.util.List;

import org.github.nynosy.adiresy_mobile.data.BookmarkRepository;
import org.github.nynosy.adiresy_mobile.data.cache.BookmarkListWithCount;

public class SavedViewModel extends AndroidViewModel {

    private final BookmarkRepository repository;

    public SavedViewModel(@NonNull Application application) {
        super(application);
        repository = BookmarkRepository.getInstance(application);
    }

    public LiveData<List<BookmarkListWithCount>> getAllLists() {
        return repository.getAllListsWithCount();
    }

    public void createList(String name, String description, String emoji,
                           BookmarkRepository.Callback<Long> cb) {
        repository.createList(name, description, emoji, cb);
    }
}
