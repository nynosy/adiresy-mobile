package org.github.nynosy.adiresy_mobile.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.github.nynosy.adiresy_mobile.databinding.BottomSheetLocationRationaleBinding;

public class LocationRationaleBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "LocationRationale";

    private BottomSheetLocationRationaleBinding binding;
    private Runnable onGrantCallback;

    public void setOnGrantCallback(Runnable callback) {
        this.onGrantCallback = callback;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetLocationRationaleBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        binding.btnGrant.setOnClickListener(v -> {
            dismiss();
            if (onGrantCallback != null) onGrantCallback.run();
        });
        binding.btnDeny.setOnClickListener(v -> dismiss());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
