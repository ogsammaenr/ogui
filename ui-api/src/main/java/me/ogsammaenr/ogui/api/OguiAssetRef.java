package me.ogsammaenr.ogui.api;

import java.util.Locale;
import java.util.Objects;

/**
 * Immutable content-addressed handle to a server-registered binary asset (PNG preview or 3D structure shell).
 */
public record OguiAssetRef(
	String sha256,
	OguiAssetKind kind,
	int byteSize
) {
	public static final int MAX_ASSET_BYTES = 2 * 1024 * 1024;
	public static final int SHA256_HEX_LENGTH = 64;

	public OguiAssetRef {
		sha256 = normalizeAndValidateSha256(sha256);
		Objects.requireNonNull(kind, "kind");
		if (byteSize <= 0 || byteSize > MAX_ASSET_BYTES) {
			throw new IllegalArgumentException(
				"Asset byteSize " + byteSize + " must be in range (1.." + MAX_ASSET_BYTES + ")"
			);
		}
	}

	public String shortHash() {
		return sha256.substring(0, 12);
	}

	public static String normalizeAndValidateSha256(String raw) {
		Objects.requireNonNull(raw, "sha256");
		String trimmed = raw.trim().toLowerCase(Locale.ROOT);
		if (trimmed.length() != SHA256_HEX_LENGTH) {
			throw new IllegalArgumentException("Expected 64-char SHA-256 hex digest, got length " + trimmed.length());
		}
		for (int i = 0; i < SHA256_HEX_LENGTH; i++) {
			char c = trimmed.charAt(i);
			boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
			if (!hex) {
				throw new IllegalArgumentException("Invalid SHA-256 hex character at index " + i);
			}
		}
		return trimmed;
	}
}
