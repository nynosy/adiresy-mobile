package org.github.nynosy.adiresy_mobile.ui.code;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;

import org.github.nynosy.adiresy_mobile.R;
import org.github.nynosy.adiresy_mobile.data.BookmarkRepository;
import org.github.nynosy.adiresy_mobile.data.cache.AddressEntity;
import org.github.nynosy.adiresy_mobile.data.cache.BookmarkEntity;
import org.github.nynosy.adiresy_mobile.databinding.ActivityCodeDetailBinding;
import org.github.nynosy.adiresy_mobile.map.QrCodeGenerator;
import org.github.nynosy.adiresy_mobile.ui.saved.SaveToListBottomSheet;

import java.util.Locale;

public class CodeDetailActivity extends AppCompatActivity {

    public static final String EXTRA_CODE = "extra_code";

    private ActivityCodeDetailBinding binding;
    private CodeDetailViewModel viewModel;
    private BookmarkRepository bookmarkRepository;

    private String currentCode;
    private AddressEntity currentAddress;
    private BookmarkEntity currentBookmark;
    private MenuItem bookmarkItem;

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
        bookmarkRepository = BookmarkRepository.getInstance(this);

        // Code comes either from EXTRA or from a deep link URI
        currentCode = resolveCode(getIntent());
        if (currentCode != null && !currentCode.isEmpty()) {
            viewModel.loadCode(currentCode);
        }

        observeViewModel();
        wireButtons();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The Save-to-list sheet can add a bookmark while this screen is behind it;
        // re-check on return so the toolbar icon reflects the current state.
        refreshBookmarkState();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_code_detail, menu);
        bookmarkItem = menu.findItem(R.id.action_bookmark);
        updateBookmarkIcon(currentBookmark != null);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@androidx.annotation.NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_bookmark) {
            onBookmarkToggle();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** "Ankatsakantsa Sud (ANKA)" — omits the parens if no code is available. */
    private static String withCode(String name, String code) {
        return (code == null || code.isEmpty()) ? name : name + " (" + code + ")";
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
                currentAddress = result.data;
                refreshBookmarkState();

                binding.labelCode.setText(result.data.canonicalCode);
                binding.imageQrCode.setImageBitmap(QrCodeGenerator.generate(
                        getString(R.string.share_text, result.data.canonicalCode)));

                binding.valueFokontany.setText(result.data.fokontanyName);
                binding.valueCommune.setText(withCode(result.data.communeName, result.data.communeShort));
                binding.valueDistrict.setText(withCode(result.data.districtName, result.data.districtCode));
                binding.valueRegion.setText(result.data.regionName);

                binding.valueLatitude.setText(getString(R.string.value_latitude,
                        String.format(Locale.US, "%.6f", result.data.latitude)));
                binding.valueLongitude.setText(getString(R.string.value_longitude,
                        String.format(Locale.US, "%.6f", result.data.longitude)));

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

    // ── Bookmarks ──────────────────────────────────────────────────────────────

    private void refreshBookmarkState() {
        if (currentCode == null || currentCode.isEmpty()) return;
        new Thread(() -> {
            BookmarkEntity existing = bookmarkRepository.findByCodeSync(currentCode);
            new Handler(Looper.getMainLooper()).post(() -> {
                currentBookmark = existing;
                updateBookmarkIcon(existing != null);
            });
        }).start();
    }

    private void onBookmarkToggle() {
        if (currentAddress == null) return;

        if (currentBookmark != null) {
            BookmarkEntity snapshot = currentBookmark;
            currentBookmark = null;
            updateBookmarkIcon(false);
            bookmarkRepository.deleteBookmarkByCode(currentCode, snapshot.listId, listName ->
                    Snackbar.make(binding.getRoot(),
                            getString(R.string.bookmark_removed_from, listName),
                            Snackbar.LENGTH_LONG)
                            .setAction(R.string.bookmark_undo, v ->
                                    bookmarkRepository.insertBookmark(snapshot, () -> {
                                        currentBookmark = snapshot;
                                        updateBookmarkIcon(true);
                                    }))
                            .show());
        } else {
            SaveToListBottomSheet.forAddress(
                    currentAddress.canonicalCode,
                    currentAddress.latitude, currentAddress.longitude,
                    currentAddress.fokontanyName, currentAddress.communeName,
                    currentAddress.districtName, currentAddress.regionName)
                    .show(getSupportFragmentManager(), SaveToListBottomSheet.TAG);
        }
    }

    private void updateBookmarkIcon(boolean saved) {
        if (bookmarkItem == null) return;
        bookmarkItem.setIcon(saved ? R.drawable.ic_bookmark : R.drawable.ic_bookmark_border);
        bookmarkItem.setTitle(saved ? R.string.cd_bookmark_saved : R.string.cd_bookmark_unsaved);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
