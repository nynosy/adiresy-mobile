package org.github.nynosy.adiresy_mobile.ui.home;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;

import org.github.nynosy.adiresy_mobile.R;
import org.github.nynosy.adiresy_mobile.data.BookmarkRepository;
import org.github.nynosy.adiresy_mobile.data.api.dto.ListingDto;
import org.github.nynosy.adiresy_mobile.data.api.dto.ReviewDto;
import org.github.nynosy.adiresy_mobile.data.api.dto.TipDto;
import org.github.nynosy.adiresy_mobile.data.cache.BookmarkEntity;
import org.github.nynosy.adiresy_mobile.databinding.BottomSheetFicheBinding;
import org.github.nynosy.adiresy_mobile.map.QrCodeGenerator;
import org.github.nynosy.adiresy_mobile.ui.fiche.FicheDetailActivity;
import org.github.nynosy.adiresy_mobile.ui.saved.SaveToListBottomSheet;
import org.github.nynosy.adiresy_mobile.util.ImageLoader;

/**
 * Directory listing ("fiche") detail sheet.
 *
 * <p>Renders every field confirmed on {@link ListingDto}'s detail schema, each
 * one hidden rather than shown blank when absent — see that class's doc and
 * docs/api-reply-2026-10-27-tasks.md §2 for exactly what's wired up here vs.
 * deliberately left out (opening_hours, claim/ownership fields).
 */
public class FicheBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "FicheDetail";

    private static final String ARG_SLUG          = "slug";
    private static final String ARG_NAME          = "name";
    private static final String ARG_CATEGORY_NAME = "category_name";
    private static final String ARG_CATEGORY_ICON = "category_icon";
    private static final String ARG_COMMUNE       = "commune_name";
    private static final String ARG_DISTRICT      = "district_name";
    private static final String ARG_LEGACY_ADDR   = "legacy_address";
    private static final String ARG_VERIFIED      = "is_verified";
    private static final String ARG_RATING        = "average_rating";
    private static final String ARG_DESCRIPTION   = "description";
    private static final String ARG_PHONE         = "phone";
    private static final String ARG_WEBSITE       = "website";
    private static final String ARG_CODE          = "address_code";
    private static final String ARG_LAT           = "latitude";
    private static final String ARG_LNG           = "longitude";
    private static final String ARG_COVER_IMAGE   = "cover_image";
    private static final String ARG_LOGO          = "logo";
    // reviews/tips aren't Bundle-friendly as-is (List<ReviewDto>/List<TipDto>
    // aren't Parcelable) — round-tripped through Gson instead, same library
    // BookmarkRepository already uses for its own JSON work.
    private static final String ARG_REVIEWS_JSON  = "reviews_json";
    private static final String ARG_TIPS_JSON     = "tips_json";

    private static final Gson GSON = new Gson();
    private static final Type REVIEW_LIST_TYPE = new TypeToken<List<ReviewDto>>() {}.getType();
    private static final Type TIP_LIST_TYPE    = new TypeToken<List<TipDto>>() {}.getType();

    private BottomSheetFicheBinding binding;
    private BookmarkRepository bookmarkRepository;

    // Loaded on a background thread; only read on the main thread after the post-back.
    private BookmarkEntity currentBookmark;

    public static FicheBottomSheet forListing(ListingDto listing) {
        Bundle args = new Bundle();
        args.putString(ARG_SLUG,          listing.slug);
        args.putString(ARG_NAME,          listing.name);
        args.putString(ARG_CATEGORY_NAME, listing.categoryName);
        args.putString(ARG_CATEGORY_ICON, listing.categoryIcon);
        args.putString(ARG_COMMUNE,       listing.communeName);
        args.putString(ARG_DISTRICT,      listing.districtName);
        args.putString(ARG_LEGACY_ADDR,   listing.legacyAddress);
        args.putBoolean(ARG_VERIFIED,     listing.isVerified);
        if (listing.averageRating != null) args.putDouble(ARG_RATING, listing.averageRating);
        args.putString(ARG_DESCRIPTION,   listing.description);
        args.putString(ARG_PHONE,         listing.phone);
        args.putString(ARG_WEBSITE,       listing.website);
        args.putString(ARG_CODE,          listing.addressCode);
        if (listing.latitude  != null) args.putDouble(ARG_LAT, listing.latitude);
        if (listing.longitude != null) args.putDouble(ARG_LNG, listing.longitude);
        args.putString(ARG_COVER_IMAGE,   listing.coverImage);
        args.putString(ARG_LOGO,          listing.logo);
        if (listing.reviews != null && !listing.reviews.isEmpty()) {
            args.putString(ARG_REVIEWS_JSON, GSON.toJson(listing.reviews));
        }
        if (listing.tips != null && !listing.tips.isEmpty()) {
            args.putString(ARG_TIPS_JSON, GSON.toJson(listing.tips));
        }
        FicheBottomSheet sheet = new FicheBottomSheet();
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetFicheBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Bundle args = requireArguments();
        String slug           = args.getString(ARG_SLUG);
        String name          = args.getString(ARG_NAME, "");
        String categoryName  = args.getString(ARG_CATEGORY_NAME);
        String categoryIcon  = args.getString(ARG_CATEGORY_ICON);
        String communeName   = args.getString(ARG_COMMUNE);
        String districtName  = args.getString(ARG_DISTRICT);
        String legacyAddress = args.getString(ARG_LEGACY_ADDR);
        boolean verified      = args.getBoolean(ARG_VERIFIED, false);
        Double rating         = args.containsKey(ARG_RATING) ? args.getDouble(ARG_RATING) : null;
        String description    = args.getString(ARG_DESCRIPTION);
        String phone          = args.getString(ARG_PHONE);
        String website        = args.getString(ARG_WEBSITE);
        String code           = args.getString(ARG_CODE);
        boolean hasLocation   = args.containsKey(ARG_LAT) && args.containsKey(ARG_LNG);
        double lat            = args.getDouble(ARG_LAT, 0.0);
        double lng            = args.getDouble(ARG_LNG, 0.0);
        String coverImageUrl  = args.getString(ARG_COVER_IMAGE);
        String logoUrl        = args.getString(ARG_LOGO);

        binding.labelFicheName.setText(name);

        loadInto(binding.imageFicheCover, coverImageUrl);
        loadInto(binding.imageFicheLogo, logoUrl);

        StringBuilder subtitle = new StringBuilder();
        if (categoryIcon != null && !categoryIcon.isEmpty()) subtitle.append(categoryIcon).append(' ');
        if (categoryName != null) subtitle.append(categoryName);
        if (communeName != null && !communeName.isEmpty()) {
            if (subtitle.length() > 0) subtitle.append(" · ");
            subtitle.append(communeName);
        }
        binding.labelFicheCategory.setText(subtitle.toString());

        binding.labelFicheVerified.setVisibility(verified ? View.VISIBLE : View.GONE);

        setOrHide(binding.labelFicheAddress, legacyAddress);

        if (rating != null) {
            binding.labelFicheRating.setText(getString(R.string.fiche_rating_format, rating));
            binding.labelFicheRating.setVisibility(View.VISIBLE);
        } else {
            binding.labelFicheRating.setVisibility(View.GONE);
        }

        setOrHide(binding.labelFicheDescription, description);

        if (!TextUtils.isEmpty(phone)) {
            binding.btnFicheCall.setText(getString(R.string.fiche_action_call, phone));
            binding.btnFicheCall.setVisibility(View.VISIBLE);
            binding.btnFicheCall.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone));
                if (intent.resolveActivity(requireContext().getPackageManager()) != null) {
                    startActivity(intent);
                }
            });
        } else {
            binding.btnFicheCall.setVisibility(View.GONE);
        }

        if (!TextUtils.isEmpty(website)) {
            binding.btnFicheWebsite.setText(R.string.fiche_action_website);
            binding.btnFicheWebsite.setVisibility(View.VISIBLE);
            binding.btnFicheWebsite.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(website));
                if (intent.resolveActivity(requireContext().getPackageManager()) != null) {
                    startActivity(intent);
                }
            });
        } else {
            binding.btnFicheWebsite.setVisibility(View.GONE);
        }

        bookmarkRepository = BookmarkRepository.getInstance(requireContext());

        // Navigate needs real coordinates too, so the whole row — not just
        // bookmarking — requires both the code and a location.
        if (!TextUtils.isEmpty(code) && hasLocation) {
            binding.rowFicheCodeActions.setVisibility(View.VISIBLE);

            binding.sectionFicheCode.setVisibility(View.VISIBLE);
            binding.labelFicheCode.setText(code);
            binding.imageFicheQrCode.setImageBitmap(
                    QrCodeGenerator.generate(getString(R.string.share_text, code)));
            binding.btnFicheFullDetails.setOnClickListener(v -> {
                // Deliberately not dismissed — mirrors CodeCardBottomSheet's
                // btnFullDetails, so the fiche sheet reappears as-is on back.
                // Goes to FicheDetailActivity (fiche info + the same code
                // block shown here), not the code-only CodeDetailActivity —
                // that screen has no fiche context at all.
                Intent intent = new Intent(requireContext(), FicheDetailActivity.class);
                intent.putExtra(FicheDetailActivity.EXTRA_SLUG, slug);
                intent.putExtra(FicheDetailActivity.EXTRA_CODE, code);
                startActivity(intent);
            });

            refreshBookmarkState(code);
            binding.btnFicheBookmark.setOnClickListener(v -> onBookmarkClicked(
                    code, lat, lng, communeName, districtName));

            binding.btnFicheShare.setOnClickListener(v -> {
                String text = getString(R.string.share_text, code);
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TEXT, text);
                startActivity(Intent.createChooser(intent, getString(R.string.share_chooser_title)));
            });

            binding.btnFicheCopy.setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) requireContext()
                        .getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("adiresy_code", code));
                Snackbar.make(requireView(), R.string.copied_to_clipboard, Snackbar.LENGTH_SHORT).show();
            });

            binding.btnFicheNavigate.setOnClickListener(v -> {
                Uri geoUri = Uri.parse(
                        "geo:" + lat + "," + lng + "?q=" + lat + "," + lng + "(" + code + ")");
                Intent intent = new Intent(Intent.ACTION_VIEW, geoUri);
                if (intent.resolveActivity(requireContext().getPackageManager()) != null) {
                    startActivity(intent);
                }
            });
        } else {
            binding.rowFicheCodeActions.setVisibility(View.GONE);
            binding.sectionFicheCode.setVisibility(View.GONE);
        }

        renderReviews(args.getString(ARG_REVIEWS_JSON));
        renderTips(args.getString(ARG_TIPS_JSON));
    }

    private void renderReviews(@Nullable String reviewsJson) {
        if (TextUtils.isEmpty(reviewsJson)) {
            binding.sectionFicheReviews.setVisibility(View.GONE);
            return;
        }
        List<ReviewDto> reviews = GSON.fromJson(reviewsJson, REVIEW_LIST_TYPE);
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
        Context ctx = requireContext();
        android.widget.LinearLayout row = new android.widget.LinearLayout(ctx);
        row.setOrientation(android.widget.LinearLayout.VERTICAL);
        row.setPadding(0, 0, 0, dp(10));

        TextView header = new TextView(ctx);
        header.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge);
        String author = !TextUtils.isEmpty(review.authorName) ? review.authorName : "—";
        header.setText(getString(R.string.fiche_review_header, review.rating, author));
        row.addView(header);

        if (!TextUtils.isEmpty(review.comment)) {
            TextView comment = new TextView(ctx);
            comment.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
            comment.setText(review.comment);
            row.addView(comment);
        }
        return row;
    }

    private void renderTips(@Nullable String tipsJson) {
        if (TextUtils.isEmpty(tipsJson)) {
            binding.sectionFicheTips.setVisibility(View.GONE);
            return;
        }
        List<TipDto> tips = GSON.fromJson(tipsJson, TIP_LIST_TYPE);
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
        Context ctx = requireContext();
        android.widget.LinearLayout row = new android.widget.LinearLayout(ctx);
        row.setOrientation(android.widget.LinearLayout.VERTICAL);
        row.setPadding(0, 0, 0, dp(10));

        TextView text = new TextView(ctx);
        text.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        String author = !TextUtils.isEmpty(tip.authorName) ? tip.authorName : "—";
        text.setText(getString(R.string.fiche_tip_text, author, tip.text));
        row.addView(text);

        if (tip.likes > 0) {
            TextView likes = new TextView(ctx);
            likes.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelMedium);
            android.util.TypedValue secondary = new android.util.TypedValue();
            ctx.getTheme().resolveAttribute(android.R.attr.textColorSecondary, secondary, true);
            likes.setTextColor(secondary.data);
            likes.setText(getResources().getQuantityString(
                    R.plurals.fiche_tip_likes, tip.likes, tip.likes));
            row.addView(likes);
        }
        return row;
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    /** Mirrors CodeCardBottomSheet.onBookmarkClicked — fokontany/region are null
     *  since ListingDto carries neither; SaveToListBottomSheet and BookmarkEntity
     *  both tolerate a null hierarchy field. */
    private void onBookmarkClicked(String code, double lat, double lng,
                                   String commune, String district) {
        if (currentBookmark != null) {
            BookmarkEntity snapshot = currentBookmark;
            currentBookmark = null;
            updateBookmarkIcon(false);
            binding.btnFicheBookmark.setEnabled(false);

            bookmarkRepository.deleteBookmarkByCode(code, snapshot.listId, listName -> {
                binding.btnFicheBookmark.setEnabled(true);
                if (getView() == null) return;
                Snackbar.make(requireView(),
                        getString(R.string.bookmark_removed_from, listName),
                        Snackbar.LENGTH_LONG)
                        .setAction(R.string.bookmark_undo, v ->
                                bookmarkRepository.insertBookmark(snapshot, () -> {
                                    currentBookmark = snapshot;
                                    updateBookmarkIcon(true);
                                }))
                        .show();
            });
        } else {
            SaveToListBottomSheet sheet = SaveToListBottomSheet.forAddress(
                    code, lat, lng, null, commune, district, null);
            sheet.setOnBookmarkSavedListener(() -> refreshBookmarkState(code));
            sheet.show(getChildFragmentManager(), SaveToListBottomSheet.TAG);
        }
    }

    private void refreshBookmarkState(String code) {
        binding.btnFicheBookmark.setEnabled(false);
        new Thread(() -> {
            BookmarkEntity existing = bookmarkRepository.findByCodeSync(code);
            new Handler(Looper.getMainLooper()).post(() -> {
                if (binding == null) return;
                currentBookmark = existing;
                updateBookmarkIcon(existing != null);
                binding.btnFicheBookmark.setEnabled(true);
            });
        }).start();
    }

    private void updateBookmarkIcon(boolean saved) {
        if (binding == null) return;
        binding.btnFicheBookmark.setImageResource(
                saved ? R.drawable.ic_bookmark : R.drawable.ic_bookmark_border);
        binding.btnFicheBookmark.setContentDescription(getString(
                saved ? R.string.cd_bookmark_saved : R.string.cd_bookmark_unsaved));
    }

    /** Hidden by default in the layout; shown only on a successful decode, so a
     *  missing or broken image URL just leaves the row absent. */
    private void loadInto(android.widget.ImageView view, @Nullable String url) {
        ImageLoader.load(url, bitmap -> {
            if (binding == null) return; // view destroyed before the load finished
            if (bitmap != null) {
                view.setImageBitmap(bitmap);
                view.setVisibility(View.VISIBLE);
            } else {
                view.setVisibility(View.GONE);
            }
        });
    }

    private static void setOrHide(android.widget.TextView view, @Nullable String text) {
        if (!TextUtils.isEmpty(text)) {
            view.setText(text);
            view.setVisibility(View.VISIBLE);
        } else {
            view.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
