package org.github.nynosy.adiresy_mobile.download;

/**
 * Resolved download URL, expected SHA-256 (nullable — absent when the
 * manifest couldn't be fetched, in which case TileDownloadWorker skips
 * verification rather than blocking the download), and size in bytes
 * (0 when unknown — manifest unavailable, or this tier isn't
 * published yet — callers must show a placeholder rather than "~0 MB").
 */
public record DownloadTarget(String url, String sha256, long sizeBytes) {
}
