package org.github.nynosy.adiresy_mobile.ui.about;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.github.nynosy.adiresy_mobile.BuildConfig;
import org.github.nynosy.adiresy_mobile.R;
import org.github.nynosy.adiresy_mobile.databinding.FragmentAboutBinding;

public class AboutFragment extends Fragment {

    private FragmentAboutBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAboutBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        binding.labelVersion.setText(getString(R.string.label_app_version) + ": " + BuildConfig.VERSION_NAME);

        binding.linkWebsite.setOnClickListener(v -> openUrl("https://adiresy.mg"));
        binding.linkRepoApp.setOnClickListener(v ->
                openUrl("https://github.com/nynosy/adiresy-mobile"));
        binding.linkRepoTiles.setOnClickListener(v ->
                openUrl("https://github.com/nynosy/adiresy-tiles"));
    }

    private void openUrl(String url) {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
