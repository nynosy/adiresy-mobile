package org.github.nynosy.adiresy_mobile.ui.saved;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.github.nynosy.adiresy_mobile.data.cache.BookmarkEntity;

/**
 * Activity-scoped ViewModel for cross-tab navigation.
 * When the user taps a bookmark in the Saved tab, this posts the target
 * so MainMapFragment can centre the map and open the code card.
 */
public class SharedMapViewModel extends ViewModel {

    public final MutableLiveData<BookmarkEntity> focusBookmark = new MutableLiveData<>();

    public void focusOn(BookmarkEntity bookmark) {
        focusBookmark.setValue(bookmark);
    }

    public void clearFocus() {
        focusBookmark.setValue(null);
    }
}
