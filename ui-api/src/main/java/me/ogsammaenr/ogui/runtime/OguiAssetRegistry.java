package me.ogsammaenr.ogui.runtime;

import me.ogsammaenr.ogui.api.OguiAssetKind;
import me.ogsammaenr.ogui.api.OguiAssetRef;
import me.ogsammaenr.ogui.api.OguiStructureShell;
import me.ogsammaenr.ogui.network.OguiWidgetDto;
import me.ogsammaenr.ogui.network.ScreenSnapshotDto;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe server-side store of content-addressed binary assets (PNG thumbnails and compressed 3D structure shells)
 * with per-player session access tracking so clients can only fetch assets referenced by active screens.
 */
public final class OguiAssetRegistry {
	public static final int CHUNK_SIZE_BYTES = 24 * 1024;
	private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

	public record StoredAsset(OguiAssetRef ref, byte[] bytes) {
	}

	private final Map<String, StoredAsset> assets = new ConcurrentHashMap<>();
	private final Map<UUID, Set<String>> playerAllowedHashes = new ConcurrentHashMap<>();

	public OguiAssetRef registerPng(byte[] pngBytes) {
		Objects.requireNonNull(pngBytes, "pngBytes");
		if (pngBytes.length == 0 || pngBytes.length > OguiAssetRef.MAX_ASSET_BYTES) {
			throw new IllegalArgumentException("PNG asset length out of bounds: " + pngBytes.length);
		}
		if (!hasPngMagic(pngBytes)) {
			throw new IllegalArgumentException("Invalid PNG asset header.");
		}
		return registerRaw(OguiAssetKind.PNG, pngBytes);
	}

	public OguiAssetRef registerStructure(OguiStructureShell shell) {
		Objects.requireNonNull(shell, "shell");
		byte[] compressed = shell.toCompressedBytes();
		return registerRaw(OguiAssetKind.STRUCTURE_SHELL, compressed);
	}

	private OguiAssetRef registerRaw(OguiAssetKind kind, byte[] rawBytes) {
		byte[] copy = rawBytes.clone();
		String sha256 = sha256Hex(copy);
		OguiAssetRef ref = new OguiAssetRef(sha256, kind, copy.length);
		assets.putIfAbsent(sha256, new StoredAsset(ref, copy));
		return ref;
	}

	public Optional<StoredAsset> find(String sha256) {
		if (sha256 == null) {
			return Optional.empty();
		}
		try {
			return Optional.ofNullable(assets.get(OguiAssetRef.normalizeAndValidateSha256(sha256)));
		} catch (IllegalArgumentException ignored) {
			return Optional.empty();
		}
	}

	public void allowAssetsFromSnapshot(UUID playerUuid, ScreenSnapshotDto snapshot) {
		Set<String> referenced = collectReferencedHashes(snapshot);
		if (referenced.isEmpty()) {
			return;
		}
		playerAllowedHashes
			.computeIfAbsent(playerUuid, ignored -> ConcurrentHashMap.newKeySet())
			.addAll(referenced);
	}

	public boolean isAllowedForPlayer(UUID playerUuid, String sha256) {
		Set<String> allowed = playerAllowedHashes.get(playerUuid);
		return allowed != null && allowed.contains(sha256);
	}

	public void evictPlayer(UUID playerUuid) {
		playerAllowedHashes.remove(playerUuid);
	}

	public void clearRuntime() {
		playerAllowedHashes.clear();
	}

	public void clearAll() {
		playerAllowedHashes.clear();
		assets.clear();
	}

	public static Set<String> collectReferencedHashes(ScreenSnapshotDto snapshot) {
		Set<String> hashes = new LinkedHashSet<>();
		collectFromWidget(snapshot.heroCard(), hashes);
		snapshot.headerWidgets().forEach(w -> collectFromWidget(w, hashes));
		snapshot.entries().forEach(w -> collectFromWidget(w, hashes));
		snapshot.footerWidgets().forEach(w -> collectFromWidget(w, hashes));
		snapshot.infoBadges().forEach(w -> collectFromWidget(w, hashes));
		snapshot.formControls().forEach(w -> collectFromWidget(w, hashes));
		snapshot.actionButtons().forEach(w -> collectFromWidget(w, hashes));
		snapshot.slottedWidgets().values().forEach(w -> collectFromWidget(w, hashes));
		collectFromWidget(snapshot.emptyState(), hashes);
		return hashes;
	}

	private static void collectFromWidget(OguiWidgetDto widget, Set<String> out) {
		if (widget == null) {
			return;
		}
		switch (widget) {
			case OguiWidgetDto.ButtonDto b -> {
				if (b.previewImage() != null) {
					out.add(b.previewImage().sha256());
				}
			}
			case OguiWidgetDto.StructurePreviewDto sp -> {
				if (sp.structureAsset() != null) {
					out.add(sp.structureAsset().sha256());
				}
			}
			default -> {
			}
		}
	}

	public static boolean hasPngMagic(byte[] bytes) {
		if (bytes == null || bytes.length < PNG_MAGIC.length) {
			return false;
		}
		for (int i = 0; i < PNG_MAGIC.length; i++) {
			if (bytes[i] != PNG_MAGIC[i]) {
				return false;
			}
		}
		return true;
	}

	public static String sha256Hex(byte[] bytes) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 unavailable", e);
		}
	}
}
