package org.github.nynosy.adiresy_mobile.download;

/**
 * Resolved download URL and expected SHA-256 (nullable — absent when the
 * manifest couldn't be fetched, in which case TileDownloadWorker skips
 * verification rather than blocking the download).
 */
public record DownloadTarget(String url, String sha256) {
}
