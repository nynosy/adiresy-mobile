package org.github.nynosy.adiresy_mobile.ui.fiche;

import android.app.Application;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import org.github.nynosy.adiresy_mobile.R;
import org.github.nynosy.adiresy_mobile.data.AdiresyRepository;
import org.github.nynosy.adiresy_mobile.data.Result;
import org.github.nynosy.adiresy_mobile.data.api.dto.ListingDto;
import org.github.nynosy.adiresy_mobile.data.cache.AddressEntity;

/** Backs FicheDetailActivity with two independent fetches: the listing (by
 *  slug) for the fiche content, and the address (by code) purely to fill in
 *  fokontany/region — the two fields ListingDto can't supply on its own (see
 *  that class's doc). The address fetch is best-effort: its failure doesn't
 *  affect the screen's error state, which is driven by the listing alone. */
public class FicheDetailViewModel extends AndroidViewModel {

    private final AdiresyRepository repository;
    private final MutableLiveData<String> slugInput = new MutableLiveData<>();
    private final MutableLiveData<String> codeInput = new MutableLiveData<>();

    public FicheDetailViewModel(@NonNull Application application) {
        super(application);
        repository = AdiresyRepository.getInstance(application);
    }

    public LiveData<Result<ListingDto>> getListing() {
        return Transformations.switchMap(slugInput, slug ->
                (slug != null && !slug.isEmpty())
                        ? repository.getListing(slug) : new MutableLiveData<>());
    }

    public LiveData<Result<AddressEntity>> getAddress() {
        return Transformations.switchMap(codeInput, code ->
                (code != null && !code.isEmpty())
                        ? repository.resolveCode(code) : new MutableLiveData<>());
    }

    /** Kicks off both fetches once, in parallel. {@code code} may be null if
     *  the caller doesn't have one yet — the address fetch simply stays idle. */
    public void load(String slug, @Nullable String code) {
        slugInput.setValue(slug);
        codeInput.setValue(code);
    }

    public void retry(String slug, @Nullable String code) {
        slugInput.setValue(null);
        codeInput.setValue(null);
        slugInput.setValue(slug);
        codeInput.setValue(code);
    }

    public void copyCode(Context context, String code) {
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("adiresy_code", code));
    }

    public String buildShareText(Context context, String code) {
        return context.getString(R.string.share_text, code);
    }
}
