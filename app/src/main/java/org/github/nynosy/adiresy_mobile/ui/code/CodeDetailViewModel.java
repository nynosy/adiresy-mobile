package org.github.nynosy.adiresy_mobile.ui.code;

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
import org.github.nynosy.adiresy_mobile.data.cache.AddressEntity;

public class CodeDetailViewModel extends AndroidViewModel {

    private final AdiresyRepository repository;
    private final MutableLiveData<String> codeInput = new MutableLiveData<>();
    private double[] coordinates;

    public CodeDetailViewModel(@NonNull Application application) {
        super(application);
        repository = AdiresyRepository.getInstance(application);
    }

    public LiveData<Result<AddressEntity>> getAddress() {
        return Transformations.switchMap(codeInput, code ->
                (code != null && !code.isEmpty())
                        ? repository.resolveCode(code)
                        : new MutableLiveData<>());
    }

    public void loadCode(String code) {
        codeInput.setValue(code);
    }

    public void retry() {
        String current = codeInput.getValue();
        codeInput.setValue(null);
        codeInput.setValue(current);
    }

    public void setCoordinates(double lat, double lng) {
        coordinates = new double[]{lat, lng};
    }

    @Nullable
    public double[] getCoordinates() {
        return coordinates;
    }

    public void copyCode(Context context) {
        String code = codeInput.getValue();
        if (code == null) return;
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("adiresy_code", code));
    }

    public String buildShareText(Context context) {
        String code = codeInput.getValue() != null ? codeInput.getValue() : "";
        return context.getString(R.string.share_text, code, code);
    }
}
