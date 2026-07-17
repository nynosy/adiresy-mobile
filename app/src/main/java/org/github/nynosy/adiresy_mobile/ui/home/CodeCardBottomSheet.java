package org.github.nynosy.adiresy_mobile.ui.home;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.snackbar.Snackbar;

import org.github.nynosy.adiresy_mobile.R;
import org.github.nynosy.adiresy_mobile.data.BookmarkRepository;
import org.github.nynosy.adiresy_mobile.data.cache.AddressEntity;
import org.github.nynosy.adiresy_mobile.data.cache.BookmarkEntity;
import org.github.nynosy.adiresy_mobile.databinding.BottomSheetCodeCardBinding;
import org.github.nynosy.adiresy_mobile.map.QrCodeGenerator;
import org.github.nynosy.adiresy_mobile.ui.code.CodeDetailActivity;
import org.github.nynosy.adiresy_mobile.ui.saved.SaveToListBottomSheet;

public class CodeCardBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "CodeCard";
    private static final String ARG_CODE      = "code";
    private static final String ARG_FOKONTANY = "fokontany";
    private static final String ARG_COMMUNE   = "commune";
    private static final String ARG_DISTRICT  = "district";
    private static final String ARG_REGION    = "region";
    private static final String ARG_LAT       = "lat";
    private static final String ARG_LNG       = "lng";

    private BottomSheetCodeCardBinding binding;
    private BookmarkRepository bookmarkRepository;

    // Loaded on background thread; only read on main thread after the post-back
    private BookmarkEntity currentBookmark;

    public static CodeCardBottomSheet forAddress(AddressEntity address) {
        Bundle args = new Bundle();
        args.putString(ARG_CODE,      address.canonicalCode);
        args.putString(ARG_FOKONTANY, address.fokontanyName);
        args.putString(ARG_COMMUNE,   address.communeName);
        args.putString(ARG_DISTRICT,  address.districtName);
        args.putString(ARG_REGION,    address.regionName);
        args.putDouble(ARG_LAT,       address.latitude);
        args.putDouble(ARG_LNG,       address.longitude);
        CodeCardBottomSheet sheet = new CodeCardBottomSheet();
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetCodeCardBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Bundle args = requireArguments();
        String code      = args.getString(ARG_CODE, "");
        String fokontany = args.getString(ARG_FOKONTANY, "");
        String commune   = args.getString(ARG_COMMUNE, "");
        String district  = args.getString(ARG_DISTRICT, "");
        String region    = args.getString(ARG_REGION, "");
        double lat       = args.getDouble(ARG_LAT, 0.0);
        double lng       = args.getDouble(ARG_LNG, 0.0);

        binding.labelCode.setText(code);
        binding.labelFokontany.setText(fokontany);
        binding.labelHierarchy.setText(commune + " › " + district);
        binding.imageQrCode.setImageBitmap(
                QrCodeGenerator.generate(getString(R.string.share_text, code)));

        bookmarkRepository = BookmarkRepository.getInstance(requireContext());

        binding.btnBookmark.setEnabled(false);
        new Thread(() -> {
            BookmarkEntity existing = bookmarkRepository.findByCodeSync(code);
            new Handler(Looper.getMainLooper()).post(() -> {
                if (binding == null) return;
                currentBookmark = existing;
                updateBookmarkIcon(existing != null);
                binding.btnBookmark.setEnabled(true);
            });
        }).start();

        binding.btnBookmark.setOnClickListener(v -> onBookmarkClicked(
                code, lat, lng, fokontany, commune, district, region));

        binding.btnFullDetails.setOnClickListener(v -> {
            // Deliberately not dismissed — the sheet reappears as-is when the user
            // navigates back from Code Detail, instead of losing their place on the map.
            Intent intent = new Intent(requireContext(), CodeDetailActivity.class);
            intent.putExtra(CodeDetailActivity.EXTRA_CODE, code);
            startActivity(intent);
        });

        binding.btnShare.setOnClickListener(v -> {
            String text = getString(R.string.share_text, code);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, text);
            startActivity(Intent.createChooser(intent, getString(R.string.share_chooser_title)));
        });

        binding.btnCopy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) requireContext()
                    .getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("adiresy_code", code));
            Snackbar.make(requireView(), R.string.copied_to_clipboard, Snackbar.LENGTH_SHORT).show();
        });

        binding.btnNavigate.setOnClickListener(v -> {
            Uri geoUri = Uri.parse(
                    "geo:" + lat + "," + lng + "?q=" + lat + "," + lng + "(" + code + ")");
            Intent intent = new Intent(Intent.ACTION_VIEW, geoUri);
            if (intent.resolveActivity(requireContext().getPackageManager()) != null) {
                startActivity(intent);
            }
        });
    }

    private void onBookmarkClicked(String code, double lat, double lng,
                                   String fokontany, String commune,
                                   String district, String region) {
        if (currentBookmark != null) {
            BookmarkEntity snapshot = currentBookmark;
            currentBookmark = null;
            updateBookmarkIcon(false);
            binding.btnBookmark.setEnabled(false);

            bookmarkRepository.deleteBookmarkByCode(code, () -> {
                binding.btnBookmark.setEnabled(true);
                if (getView() == null) return;
                Snackbar.make(requireView(),
                        getString(R.string.bookmark_removed_from, ""),
                        Snackbar.LENGTH_LONG)
                        .setAction(R.string.bookmark_undo, v ->
                                bookmarkRepository.insertBookmark(snapshot, () -> {
                                    currentBookmark = snapshot;
                                    updateBookmarkIcon(true);
                                }))
                        .show();
            });
        } else {
            SaveToListBottomSheet.forAddress(code, lat, lng, fokontany, commune, district, region)
                    .show(getChildFragmentManager(), SaveToListBottomSheet.TAG);
        }
    }

    private void updateBookmarkIcon(boolean saved) {
        if (binding == null) return;
        binding.btnBookmark.setImageResource(
                saved ? R.drawable.ic_bookmark : R.drawable.ic_bookmark_border);
        binding.btnBookmark.setContentDescription(getString(
                saved ? R.string.cd_bookmark_saved : R.string.cd_bookmark_unsaved));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
