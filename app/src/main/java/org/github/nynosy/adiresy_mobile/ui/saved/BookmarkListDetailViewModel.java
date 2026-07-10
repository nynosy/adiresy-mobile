package org.github.nynosy.adiresy_mobile.ui.saved;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import java.util.List;

import org.github.nynosy.adiresy_mobile.data.BookmarkRepository;
import org.github.nynosy.adiresy_mobile.data.cache.BookmarkEntity;
import org.github.nynosy.adiresy_mobile.data.cache.BookmarkListEntity;

public class BookmarkListDetailViewModel extends AndroidViewModel {

    private final BookmarkRepository repository;

    public BookmarkListDetailViewModel(@NonNull Application application) {
        super(application);
        repository = BookmarkRepository.getInstance(application);
    }

    public LiveData<List<BookmarkEntity>> getBookmarks(long listId) {
        return repository.getBookmarksForList(listId);
    }

    public void updateList(BookmarkListEntity list, Runnable onDone) {
        repository.updateList(list, onDone);
    }

    public void deleteList(BookmarkListEntity list, Runnable onDone) {
        repository.deleteList(list, onDone);
    }

    public void deleteBookmark(BookmarkEntity bookmark, Runnable onDone) {
        repository.deleteBookmark(bookmark, onDone);
    }

    public void insertBookmark(BookmarkEntity bookmark, Runnable onDone) {
        repository.insertBookmark(bookmark, onDone);
    }

    public void updateDescription(long id, String description, Runnable onDone) {
        repository.updateDescription(id, description, onDone);
    }

    public void moveBookmark(long bookmarkId, long newListId, Runnable onDone) {
        repository.moveBookmark(bookmarkId, newListId, onDone);
    }
}
