package org.github.nynosy.adiresy_mobile.ui.home;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.snackbar.Snackbar;

import org.github.nynosy.adiresy_mobile.R;
import org.github.nynosy.adiresy_mobile.data.api.dto.ListingDto;
import org.github.nynosy.adiresy_mobile.databinding.BottomSheetFicheBinding;

/**
 * Directory listing ("fiche") detail sheet — Phase A, proof of concept.
 *
 * <p>Renders only the fields confirmed on {@link ListingDto}, each one hidden
 * when absent ("show what's filled, hide what's blank" — see
 * docs/directory-api-integration.md §5 Q2) — plus {@code description} as the
 * one detail-only field wired up to prove that pattern end-to-end. The rest of
 * the detail schema is still unconfirmed; see the TODOs in
 * {@code bottom_sheet_fiche.xml} and {@link ListingDto}.
 */
public class FicheBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "FicheDetail";

    private static final String ARG_NAME          = "name";
    private static final String ARG_CATEGORY_NAME = "category_name";
    private static final String ARG_CATEGORY_ICON = "category_icon";
    private static final String ARG_COMMUNE       = "commune_name";
    private static final String ARG_LEGACY_ADDR   = "legacy_address";
    private static final String ARG_VERIFIED      = "is_verified";
    private static final String ARG_RATING        = "average_rating";
    private static final String ARG_DESCRIPTION   = "description";
    private static final String ARG_PHONE         = "phone";
    private static final String ARG_WEBSITE       = "website";
    private static final String ARG_CODE          = "address_code";
    private static final String ARG_LAT           = "latitude";
    private static final String ARG_LNG           = "longitude";

    private BottomSheetFicheBinding binding;

    public static FicheBottomSheet forListing(ListingDto listing) {
        Bundle args = new Bundle();
        args.putString(ARG_NAME,          listing.name);
        args.putString(ARG_CATEGORY_NAME, listing.categoryName);
        args.putString(ARG_CATEGORY_ICON, listing.categoryIcon);
        args.putString(ARG_COMMUNE,       listing.communeName);
        args.putString(ARG_LEGACY_ADDR,   listing.legacyAddress);
        args.putBoolean(ARG_VERIFIED,     listing.isVerified);
        if (listing.averageRating != null) args.putDouble(ARG_RATING, listing.averageRating);
        args.putString(ARG_DESCRIPTION,   listing.description);
        args.putString(ARG_PHONE,         listing.phone);
        args.putString(ARG_WEBSITE,       listing.website);
        args.putString(ARG_CODE,          listing.addressCode);
        if (listing.latitude  != null) args.putDouble(ARG_LAT, listing.latitude);
        if (listing.longitude != null) args.putDouble(ARG_LNG, listing.longitude);
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
        String name          = args.getString(ARG_NAME, "");
        String categoryName  = args.getString(ARG_CATEGORY_NAME);
        String categoryIcon  = args.getString(ARG_CATEGORY_ICON);
        String communeName   = args.getString(ARG_COMMUNE);
        String legacyAddress = args.getString(ARG_LEGACY_ADDR);
        boolean verified      = args.getBoolean(ARG_VERIFIED, false);
        Double rating         = args.containsKey(ARG_RATING) ? args.getDouble(ARG_RATING) : null;
        String description    = args.getString(ARG_DESCRIPTION);
        String phone          = args.getString(ARG_PHONE);
        String website        = args.getString(ARG_WEBSITE);
        String code           = args.getString(ARG_CODE);
        double lat            = args.getDouble(ARG_LAT, 0.0);
        double lng            = args.getDouble(ARG_LNG, 0.0);

        binding.labelFicheName.setText(name);

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

        if (!TextUtils.isEmpty(code)) {
            binding.rowFicheCodeActions.setVisibility(View.VISIBLE);

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
        }
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
