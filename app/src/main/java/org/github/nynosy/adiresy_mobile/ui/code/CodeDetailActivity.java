package org.github.nynosy.adiresy_mobile.ui.code;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import org.github.nynosy.adiresy_mobile.R;
import org.github.nynosy.adiresy_mobile.databinding.ActivityCodeDetailBinding;

public class CodeDetailActivity extends AppCompatActivity {

    public static final String EXTRA_CODE = "extra_code";

    private ActivityCodeDetailBinding binding;
    private CodeDetailViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCodeDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.app_name);
        }

        viewModel = new ViewModelProvider(this).get(CodeDetailViewModel.class);

        // Code comes either from EXTRA or from a deep link URI
        String code = resolveCode(getIntent());
        if (code != null && !code.isEmpty()) {
            viewModel.loadCode(code);
        }

        observeViewModel();
        wireButtons();
    }

    private String resolveCode(Intent intent) {
        if (intent.hasExtra(EXTRA_CODE)) {
            return intent.getStringExtra(EXTRA_CODE);
        }
        Uri data = intent.getData();
        if (data != null) {
            String path = data.getPath();
            if (path != null && path.length() > 1) {
                return path.substring(1); // strip leading "/"
            }
        }
        return null;
    }

    private void observeViewModel() {
        viewModel.getAddress().observe(this, result -> {
            if (result == null) return;
            if (result.isError()) {
                binding.contentGroup.setVisibility(View.GONE);
                binding.errorGroup.setVisibility(View.VISIBLE);
                binding.labelError.setText(result.isApiKeyError()
                        ? R.string.error_api_key
                        : result.httpCode == 0
                                ? R.string.error_no_network
                                : R.string.error_code_not_found);
                return;
            }
            binding.contentGroup.setVisibility(View.VISIBLE);
            binding.errorGroup.setVisibility(View.GONE);

            if (result.data != null) {
                binding.labelCode.setText(result.data.canonicalCode);
                binding.labelFokontany.setText(result.data.fokontanyName);
                binding.labelHierarchy.setText(
                        result.data.communeName + " › "
                        + result.data.districtName + " › "
                        + result.data.regionName);
                viewModel.setCoordinates(result.data.latitude, result.data.longitude);
            }
        });
    }

    private void wireButtons() {
        binding.btnRetry.setOnClickListener(v -> viewModel.retry());

        binding.btnCopy.setOnClickListener(v -> viewModel.copyCode(this));

        binding.btnShare.setOnClickListener(v -> {
            String text = viewModel.buildShareText(this);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, text);
            startActivity(Intent.createChooser(intent, getString(R.string.share_chooser_title)));
        });

        binding.btnNavigate.setOnClickListener(v -> {
            double[] coords = viewModel.getCoordinates();
            if (coords != null) {
                Uri uri = Uri.parse("geo:" + coords[0] + "," + coords[1]
                        + "?q=" + coords[0] + "," + coords[1]);
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
