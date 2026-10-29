package org.github.nynosy.adiresy_mobile.ui.fiche;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;

import org.github.nynosy.adiresy_mobile.R;
import org.github.nynosy.adiresy_mobile.data.BookmarkRepository;
import org.github.nynosy.adiresy_mobile.data.Result;
import org.github.nynosy.adiresy_mobile.data.api.dto.ListingDto;
import org.github.nynosy.adiresy_mobile.data.api.dto.ReviewDto;
import org.github.nynosy.adiresy_mobile.data.api.dto.TipDto;
import org.github.nynosy.adiresy_mobile.data.cache.BookmarkEntity;
import org.github.nynosy.adiresy_mobile.databinding.ActivityFicheDetailBinding;
import org.github.nynosy.adiresy_mobile.map.QrCodeGenerator;
import org.github.nynosy.adiresy_mobile.ui.saved.SaveToListBottomSheet;
import org.github.nynosy.adiresy_mobile.util.ImageLoader;

import java.util.List;
import java.util.Locale;

/**
 * Full-screen fiche detail: every field FicheBottomSheet shows, plus the same
 * address-code block (hierarchy, GPS, QR, Share/Copy/Navigate) as
 * CodeDetailActivity — reached from the bottom sheet's "Full details" link
 * instead of handing off to the code-only screen, so a fiche with a resolved
 * Adiresy code shows both halves together rather than losing the fiche
 * context on the way to the code's admin hierarchy.
 */
public class FicheDetailActivity extends AppCompatActivity {

    public static final String EXTRA_SLUG = "extra_slug";
    public static final String EXTRA_CODE = "extra_code";

    private ActivityFicheDetailBinding binding;
    private FicheDetailViewModel viewModel;
    private BookmarkRepository bookmarkRepository;

    private String slug;
    private String code;
    private double[] coordinates;
    private String communeName;
    private String districtName;
    private BookmarkEntity currentBookmark;
    private MenuItem bookmarkItem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFicheDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.app_name);
        }

        // Edge-to-edge is enforced from API 35+: without this, the toolbar's background
        // extends behind the status bar but its clickable content doesn't — see
        // CodeDetailActivity, which this mirrors.
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(v.getPaddingLeft(), bars.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });

        viewModel = new ViewModelProvider(this).get(FicheDetailViewModel.class);
        bookmarkRepository = BookmarkRepository.getInstance(this);

        slug = getIntent().getStringExtra(EXTRA_SLUG);
        code = getIntent().getStringExtra(EXTRA_CODE);

        if (!TextUtils.isEmpty(slug)) {
            viewModel.load(slug, code);
        }

        observeViewModel();
        binding.btnRetry.setOnClickListener(v -> viewModel.retry(slug, code));
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
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_bookmark) {
            onBookmarkToggle();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void observeViewModel() {
        viewModel.getListing().observe(this, result -> {
            if (result == null) return;
            if (result.isError()) {
                binding.contentGroup.setVisibility(View.GONE);
                binding.errorGroup.setVisibility(View.VISIBLE);
                binding.labelError.setText(errorMessageRes(result));
                return;
            }
            binding.contentGroup.setVisibility(View.VISIBLE);
            binding.errorGroup.setVisibility(View.GONE);
            if (result.data != null) bindListing(result.data);
        });

        viewModel.getAddress().observe(this, result -> {
            if (result == null || result.data == null) return;
            // Best-effort only — see the class doc. A failure here just
            // leaves these two rows blank instead of erroring the screen.
            binding.valueFokontany.setText(result.data.fokontanyName);
            binding.valueRegion.setText(result.data.regionName);
        });
    }

    @StringRes
    private static int errorMessageRes(Result<?> result) {
        if (result.isApiKeyError()) return R.string.error_api_key;
        if (result.isNetworkError()) return R.string.error_no_network;
        if (result.isRateLimited()) return R.string.error_rate_limited;
        return R.string.fiche_load_error;
    }

    /** "Ankatsakantsa Sud (ANKA)" — omits the parens if no code is available. */
    private static String withCode(String name, String code) {
        return (code == null || code.isEmpty()) ? name : name + " (" + code + ")";
    }

    private void bindListing(ListingDto listing) {
        code = listing.addressCode;
        coordinates = (listing.latitude != null && listing.longitude != null)
                ? new double[]{listing.latitude, listing.longitude} : null;
        communeName = listing.communeName;
        districtName = listing.districtName;

        binding.labelFicheName.setText(listing.name);
        loadInto(binding.imageFicheCover, listing.coverImage);
        loadInto(binding.imageFicheLogo, listing.logo);

        StringBuilder subtitle = new StringBuilder();
        if (!TextUtils.isEmpty(listing.categoryIcon)) subtitle.append(listing.categoryIcon).append(' ');
        if (listing.categoryName != null) subtitle.append(listing.categoryName);
        if (!TextUtils.isEmpty(listing.communeName)) {
            if (subtitle.length() > 0) subtitle.append(" · ");
            subtitle.append(listing.communeName);
        }
        binding.labelFicheCategory.setText(subtitle.toString());

        binding.labelFicheVerified.setVisibility(listing.isVerified ? View.VISIBLE : View.GONE);
        setOrHide(binding.labelFicheAddress, listing.legacyAddress);

        if (listing.averageRating != null) {
            binding.labelFicheRating.setText(getString(R.string.fiche_rating_format, listing.averageRating));
            binding.labelFicheRating.setVisibility(View.VISIBLE);
        } else {
            binding.labelFicheRating.setVisibility(View.GONE);
        }

        setOrHide(binding.labelFicheDescription, listing.description);

        if (!TextUtils.isEmpty(listing.phone)) {
            String phone = listing.phone;
            binding.btnFicheCall.setText(getString(R.string.fiche_action_call, phone));
            binding.btnFicheCall.setVisibility(View.VISIBLE);
            binding.btnFicheCall.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone));
                if (intent.resolveActivity(getPackageManager()) != null) startActivity(intent);
            });
        } else {
            binding.btnFicheCall.setVisibility(View.GONE);
        }

        if (!TextUtils.isEmpty(listing.website)) {
            String website = listing.website;
            binding.btnFicheWebsite.setText(R.string.fiche_action_website);
            binding.btnFicheWebsite.setVisibility(View.VISIBLE);
            binding.btnFicheWebsite.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(website));
                if (intent.resolveActivity(getPackageManager()) != null) startActivity(intent);
            });
        } else {
            binding.btnFicheWebsite.setVisibility(View.GONE);
        }

        // Plain info text only — no in-app login/claim flow. That belongs on
        // the website, not here.
        binding.labelFicheClaimNotice.setVisibility(
                "unclaimed".equals(listing.claimState) ? View.VISIBLE : View.GONE);
        if ("unclaimed".equals(listing.claimState)) {
            binding.labelFicheClaimNotice.setText(R.string.fiche_claim_notice);
        }

        boolean hasCode = !TextUtils.isEmpty(code);
        boolean hasLocation = coordinates != null;
        boolean showCodeSection = hasCode && hasLocation;
        binding.sectionFicheCode.setVisibility(showCodeSection ? View.VISIBLE : View.GONE);
        binding.dividerFicheCode.setVisibility(showCodeSection ? View.VISIBLE : View.GONE);

        if (showCodeSection) {
            String finalCode = code;
            binding.labelCode.setText(finalCode);
            binding.imageQrCode.setImageBitmap(
                    QrCodeGenerator.generate(getString(R.string.share_text, finalCode)));
            binding.valueCommune.setText(withCode(listing.communeName, listing.communeShortCode));
            binding.valueDistrict.setText(withCode(listing.districtName, listing.districtShortCode));
            binding.valueLatitude.setText(getString(R.string.value_latitude,
                    String.format(Locale.US, "%.6f", coordinates[0])));
            binding.valueLongitude.setText(getString(R.string.value_longitude,
                    String.format(Locale.US, "%.6f", coordinates[1])));

            binding.btnShare.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TEXT, viewModel.buildShareText(this, finalCode));
                startActivity(Intent.createChooser(intent, getString(R.string.share_chooser_title)));
            });

            binding.btnCopy.setOnClickListener(v -> {
                viewModel.copyCode(this, finalCode);
                Snackbar.make(binding.getRoot(), R.string.copied_to_clipboard, Snackbar.LENGTH_SHORT).show();
            });

            binding.btnNavigate.setOnClickListener(v -> {
                Uri uri = Uri.parse("geo:" + coordinates[0] + "," + coordinates[1]
                        + "?q=" + coordinates[0] + "," + coordinates[1]);
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            });
        }

        refreshBookmarkState();
        renderReviews(listing.reviews);
        renderTips(listing.tips);
    }

    private static void setOrHide(TextView view, String text) {
        if (!TextUtils.isEmpty(text)) {
            view.setText(text);
            view.setVisibility(View.VISIBLE);
        } else {
            view.setVisibility(View.GONE);
        }
    }

    private void loadInto(android.widget.ImageView view, String url) {
        ImageLoader.load(url, bitmap -> {
            if (binding == null) return;
            if (bitmap != null) {
                view.setImageBitmap(bitmap);
                view.setVisibility(View.VISIBLE);
            } else {
                view.setVisibility(View.GONE);
            }
        });
    }

    private void renderReviews(List<ReviewDto> reviews) {
        if (reviews == null || reviews.isEmpty()) {
            binding.sectionFicheReviews.setVisibility(View.GONE);
            return;
        }
        binding.containerFicheReviews.removeAllViews();
        for (ReviewDto review : reviews) {
            binding.containerFicheReviews.addView(buildReviewRow(review));
        }
        binding.sectionFicheReviews.setVisibility(View.VISIBLE);
    }

    private View buildReviewRow(ReviewDto review) {
        android.widget.LinearLayout row = new android.widget.LinearLayout(this);
        row.setOrientation(android.widget.LinearLayout.VERTICAL);
        row.setPadding(0, 0, 0, dp(10));

        TextView header = new TextView(this);
        header.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge);
        String author = !TextUtils.isEmpty(review.authorName) ? review.authorName : "—";
        header.setText(getString(R.string.fiche_review_header, review.rating, author));
        row.addView(header);

        if (!TextUtils.isEmpty(review.comment)) {
            TextView comment = new TextView(this);
            comment.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
            comment.setText(review.comment);
            row.addView(comment);
        }
        return row;
    }

    private void renderTips(List<TipDto> tips) {
        if (tips == null || tips.isEmpty()) {
            binding.sectionFicheTips.setVisibility(View.GONE);
            return;
        }
        binding.containerFicheTips.removeAllViews();
        for (TipDto tip : tips) {
            binding.containerFicheTips.addView(buildTipRow(tip));
        }
        binding.sectionFicheTips.setVisibility(View.VISIBLE);
    }

    private View buildTipRow(TipDto tip) {
        android.widget.LinearLayout row = new android.widget.LinearLayout(this);
        row.setOrientation(android.widget.LinearLayout.VERTICAL);
        row.setPadding(0, 0, 0, dp(10));

        TextView text = new TextView(this);
        text.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        String author = !TextUtils.isEmpty(tip.authorName) ? tip.authorName : "—";
        text.setText(getString(R.string.fiche_tip_text, author, tip.text));
        row.addView(text);

        if (tip.likes > 0) {
            TextView likes = new TextView(this);
            likes.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelMedium);
            android.util.TypedValue secondary = new android.util.TypedValue();
            getTheme().resolveAttribute(android.R.attr.textColorSecondary, secondary, true);
            likes.setTextColor(secondary.data);
            likes.setText(getString(R.string.fiche_tip_likes, tip.likes));
            row.addView(likes);
        }
        return row;
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    // ── Bookmarks ──────────────────────────────────────────────────────────────
    // Mirrors CodeDetailActivity's bookmark handling — fokontany/region are
    // null here since the bookmark is saved from the fiche's own fields
    // (commune/district only; see FicheBottomSheet's equivalent comment).

    private void refreshBookmarkState() {
        if (TextUtils.isEmpty(code)) return;
        String currentCode = code;
        new Thread(() -> {
            BookmarkEntity existing = bookmarkRepository.findByCodeSync(currentCode);
            new Handler(Looper.getMainLooper()).post(() -> {
                if (binding == null) return;
                currentBookmark = existing;
                updateBookmarkIcon(existing != null);
            });
        }).start();
    }

    private void onBookmarkToggle() {
        if (TextUtils.isEmpty(code) || coordinates == null) return;
        String currentCode = code;

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
            SaveToListBottomSheet sheet = SaveToListBottomSheet.forAddress(
                    currentCode, coordinates[0], coordinates[1], null, communeName, districtName, null);
            sheet.setOnBookmarkSavedListener(this::refreshBookmarkState);
            sheet.show(getSupportFragmentManager(), SaveToListBottomSheet.TAG);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
