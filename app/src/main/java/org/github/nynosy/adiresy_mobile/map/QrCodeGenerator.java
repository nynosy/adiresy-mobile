package org.github.nynosy.adiresy_mobile.map;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.EnumMap;
import java.util.Map;

/**
 * Encodes a short address link into a scannable QR bitmap. Always renders
 * black-on-white regardless of the app theme — a QR code needs guaranteed
 * light/dark module contrast to stay scannable, which a dark-theme tint would break.
 */
public final class QrCodeGenerator {

    private static final int SIZE_PX = 300;

    private QrCodeGenerator() {}

    /** Returns null if the content can't be encoded (e.g. too long for a QR code). */
    public static Bitmap generate(String content) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);

            BitMatrix matrix = new QRCodeWriter()
                    .encode(content, BarcodeFormat.QR_CODE, SIZE_PX, SIZE_PX, hints);

            Bitmap bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.RGB_565);
            for (int x = 0; x < SIZE_PX; x++) {
                for (int y = 0; y < SIZE_PX; y++) {
                    bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            return bitmap;
        } catch (WriterException e) {
            return null;
        }
    }
}
