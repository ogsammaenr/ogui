package me.ogsammaenr.ogui.client.assets;

import com.mojang.blaze3d.platform.NativeImage;
import me.ogsammaenr.ogui.api.Ogui;
import me.ogsammaenr.ogui.api.OguiAssetKind;
import me.ogsammaenr.ogui.api.OguiAssetRef;
import me.ogsammaenr.ogui.api.OguiStructureShell;
import me.ogsammaenr.ogui.client.render.OguiStructureMesh;
import me.ogsammaenr.ogui.network.C2SRequestAssetPayload;
import me.ogsammaenr.ogui.network.S2CAssetChunkPayload;
import me.ogsammaenr.ogui.runtime.OguiAssetRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Client-side disk + memory asset cache managing chunk reassembly, SHA-256 verification,
 * {@link DynamicTexture} registration for PNG thumbnails, and baked {@link OguiStructureMesh} caching.
 */
public final class OguiClientAssetManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(OguiClientAssetManager.class);
	private static final int MAX_MEMORY_ENTRIES = 64;
	private static final long MAX_DISK_CACHE_BYTES = 64L * 1024L * 1024L;
	private static final long REQUEST_TIMEOUT_MILLIS = 15_000L;
	private static volatile @Nullable OguiClientAssetManager sharedInstance;

	public enum Status {
		LOADING,
		READY,
		FAILED
	}

	public record PngAsset(Identifier textureId, int width, int height) {
	}

	public record StructureAsset(OguiStructureShell shell, @Nullable OguiStructureMesh mesh) {
	}

	public static final class AssetSlot {
		private final OguiAssetRef ref;
		private Status status = Status.LOADING;
		private float progress;
		private @Nullable PngAsset png;
		private @Nullable StructureAsset structure;
		private long requestedAtMillis;

		private AssetSlot(OguiAssetRef ref) {
			this.ref = ref;
		}

		public OguiAssetRef ref() {
			return ref;
		}

		public Status status() {
			return status;
		}

		public float progress() {
			return progress;
		}

		public @Nullable PngAsset png() {
			return png;
		}

		public @Nullable StructureAsset structure() {
			return structure;
		}

		public @Nullable OguiStructureMesh ensureMesh(Minecraft mc) {
			if (status != Status.READY || structure == null) {
				return null;
			}
			if (structure.mesh() != null) {
				return structure.mesh();
			}
			OguiStructureMesh baked = OguiStructureMesh.bake(structure.shell(), mc);
			this.structure = new StructureAsset(structure.shell(), baked);
			return baked;
		}
	}

	private static final class PendingTransfer {
		final OguiAssetKind kind;
		final int totalBytes;
		final int totalChunks;
		final byte[] @Nullable [] chunks;
		int receivedChunks;
		int receivedBytes;

		PendingTransfer(OguiAssetKind kind, int totalBytes, int totalChunks) {
			this.kind = kind;
			this.totalBytes = totalBytes;
			this.totalChunks = totalChunks;
			this.chunks = new byte[totalChunks][];
		}

		boolean acceptChunk(int index, byte[] data) {
			if (index < 0 || index >= totalChunks || chunks[index] != null) {
				return false;
			}
			chunks[index] = data;
			receivedChunks++;
			receivedBytes += data.length;
			return true;
		}

		float progress() {
			return totalBytes <= 0 ? 0.0F : Math.clamp((float) receivedBytes / totalBytes, 0.0F, 1.0F);
		}

		boolean isComplete() {
			return receivedChunks == totalChunks;
		}

		byte @Nullable [] assemble() {
			if (!isComplete()) {
				return null;
			}
			ByteArrayOutputStream out = new ByteArrayOutputStream(totalBytes);
			for (byte[] chunk : chunks) {
				if (chunk == null) {
					return null;
				}
				out.writeBytes(chunk);
			}
			byte[] full = out.toByteArray();
			return full.length == totalBytes ? full : null;
		}
	}

	private final Path diskCacheDir;
	private final Consumer<String> networkRequester;
	private final BiFunction<OguiAssetRef, byte[], PngAsset> pngLoader;
	private final Consumer<AssetSlot> slotReleaser;
	private final Map<String, byte[]> localSeeds = new HashMap<>();
	private final Map<String, PendingTransfer> pendingTransfers = new HashMap<>();
	private final Map<String, AssetSlot> memoryCache = new LinkedHashMap<>(32, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, AssetSlot> eldest) {
			if (size() > MAX_MEMORY_ENTRIES) {
				slotReleaser.accept(eldest.getValue());
				return true;
			}
			return false;
		}
	};

	public static OguiClientAssetManager shared() {
		OguiClientAssetManager existing = sharedInstance;
		if (existing == null) {
			synchronized (OguiClientAssetManager.class) {
				existing = sharedInstance;
				if (existing == null) {
					existing = new OguiClientAssetManager();
					sharedInstance = existing;
				}
			}
		}
		return existing;
	}

	public OguiClientAssetManager() {
		this(
			FabricLoader.getInstance().getGameDir().resolve("cache").resolve("ogui-assets"),
			sha256 -> {
				if (ClientPlayNetworking.canSend(C2SRequestAssetPayload.TYPE)) {
					ClientPlayNetworking.send(new C2SRequestAssetPayload(sha256));
				}
			},
			OguiClientAssetManager::loadGpuPng,
			OguiClientAssetManager::releaseSlotResources
		);
	}

	public OguiClientAssetManager(Path diskCacheDir, Consumer<String> networkRequester) {
		this(diskCacheDir, networkRequester, OguiClientAssetManager::loadGpuPng, OguiClientAssetManager::releaseSlotResources);
	}

	public OguiClientAssetManager(
		Path diskCacheDir,
		Consumer<String> networkRequester,
		BiFunction<OguiAssetRef, byte[], PngAsset> pngLoader,
		Consumer<AssetSlot> slotReleaser
	) {
		this.diskCacheDir = Objects.requireNonNull(diskCacheDir, "diskCacheDir");
		this.networkRequester = Objects.requireNonNull(networkRequester, "networkRequester");
		this.pngLoader = Objects.requireNonNull(pngLoader, "pngLoader");
		this.slotReleaser = Objects.requireNonNull(slotReleaser, "slotReleaser");
	}

	/**
	 * Seeds raw bytes for offline/local client previews ({@code /oguitest open}) so local previews bypass server asset packets.
	 */
	public void seedLocalAsset(OguiAssetRef ref, byte[] rawBytes) {
		Objects.requireNonNull(ref, "ref");
		Objects.requireNonNull(rawBytes, "rawBytes");
		localSeeds.put(ref.sha256(), rawBytes.clone());
	}

	/**
	 * Resolves a content-addressed asset slot, loading from memory, local seeds, or disk cache,
	 * or dispatching a single network request if not yet cached.
	 */
	public AssetSlot resolveOrRequest(OguiAssetRef ref) {
		Objects.requireNonNull(ref, "ref");
		AssetSlot slot = memoryCache.get(ref.sha256());
		if (slot != null && slot.ref().equals(ref)) {
			if (slot.status == Status.READY || slot.status == Status.FAILED) {
				return slot;
			}
			long now = System.currentTimeMillis();
			if (slot.requestedAtMillis > 0 && now - slot.requestedAtMillis > REQUEST_TIMEOUT_MILLIS
				&& !pendingTransfers.containsKey(ref.sha256())) {
				slot.requestedAtMillis = now;
				networkRequester.accept(ref.sha256());
			}
			return slot;
		}

		slot = new AssetSlot(ref);
		memoryCache.put(ref.sha256(), slot);

		byte[] seeded = localSeeds.get(ref.sha256());
		if (seeded != null && verifyBytes(ref, seeded)) {
			populateSlotFromBytes(slot, seeded);
			return slot;
		}

		byte[] cached = readVerifiedFromDisk(ref);
		if (cached != null) {
			populateSlotFromBytes(slot, cached);
			return slot;
		}

		slot.requestedAtMillis = System.currentTimeMillis();
		networkRequester.accept(ref.sha256());
		return slot;
	}

	public void onAssetChunk(S2CAssetChunkPayload payload) {
		String sha = payload.sha256();
		AssetSlot existing = memoryCache.get(sha);
		if (existing != null && existing.status == Status.READY) {
			pendingTransfers.remove(sha);
			return;
		}

		PendingTransfer transfer = pendingTransfers.get(sha);
		if (transfer == null
			|| transfer.totalBytes != payload.totalBytes()
			|| transfer.totalChunks != payload.totalChunks()
			|| transfer.kind != payload.kind()) {
			transfer = new PendingTransfer(payload.kind(), payload.totalBytes(), payload.totalChunks());
			pendingTransfers.put(sha, transfer);
		}
		transfer.acceptChunk(payload.chunkIndex(), payload.data());

		if (existing != null) {
			existing.progress = transfer.progress();
		}

		if (!transfer.isComplete()) {
			return;
		}
		pendingTransfers.remove(sha);

		byte[] assembled = transfer.assemble();
		OguiAssetRef expectedRef = new OguiAssetRef(sha, transfer.kind, transfer.totalBytes);
		AssetSlot slot = memoryCache.computeIfAbsent(sha, ignored -> new AssetSlot(expectedRef));

		if (assembled == null || !verifyBytes(expectedRef, assembled)) {
			slot.status = Status.FAILED;
			LOGGER.warn("Discarded corrupt or mismatched Ogui asset {}", sha);
			return;
		}

		writeToDisk(expectedRef, assembled);
		populateSlotFromBytes(slot, assembled);
	}

	public void onAssetUnavailable(String sha256) {
		pendingTransfers.remove(sha256);
		AssetSlot slot = memoryCache.get(sha256);
		if (slot != null && slot.status != Status.READY) {
			slot.status = Status.FAILED;
		}
	}

	public void resetSessionTransfers() {
		pendingTransfers.clear();
		for (AssetSlot slot : memoryCache.values()) {
			if (slot.status == Status.LOADING) {
				slot.requestedAtMillis = 0L;
				slot.progress = 0.0F;
			}
		}
	}

	public void releaseAllGpuAssets() {
		pendingTransfers.clear();
		for (AssetSlot slot : memoryCache.values()) {
			slotReleaser.accept(slot);
		}
		memoryCache.clear();
	}

	private void populateSlotFromBytes(AssetSlot slot, byte[] bytes) {
		OguiAssetRef ref = slot.ref();
		try {
			switch (ref.kind()) {
				case PNG -> {
					if (!OguiAssetRegistry.hasPngMagic(bytes)) {
						slot.status = Status.FAILED;
						return;
					}
					slot.png = pngLoader.apply(ref, bytes);
					slot.progress = 1.0F;
					slot.status = Status.READY;
				}
				case STRUCTURE_SHELL -> {
					OguiStructureShell shell = OguiStructureShell.fromCompressedBytes(bytes);
					slot.structure = new StructureAsset(shell, null);
					slot.progress = 1.0F;
					slot.status = Status.READY;
				}
			}
		} catch (Exception e) {
			slot.status = Status.FAILED;
			LOGGER.warn("Failed to decode Ogui asset {} ({})", ref.shortHash(), ref.kind(), e);
		}
	}

	private static PngAsset loadGpuPng(OguiAssetRef ref, byte[] bytes) {
		try {
			NativeImage image = NativeImage.read(bytes);
			int w = image.getWidth();
			int h = image.getHeight();
			Identifier texId = Ogui.id("dynamic/asset_" + ref.sha256());
			Minecraft mc = Minecraft.getInstance();
			DynamicTexture texture = new DynamicTexture(() -> "ogui_asset_" + ref.shortHash(), image);
			mc.getTextureManager().register(texId, texture);
			return new PngAsset(texId, w, h);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to decode PNG image", e);
		}
	}

	private static void releaseSlotResources(AssetSlot slot) {
		if (slot.png != null) {
			Minecraft mc = Minecraft.getInstance();
			if (mc != null && mc.getTextureManager() != null) {
				mc.getTextureManager().release(slot.png.textureId());
			}
			slot.png = null;
		}
	}

	private static boolean verifyBytes(OguiAssetRef ref, byte[] bytes) {
		return bytes.length == ref.byteSize() && OguiAssetRegistry.sha256Hex(bytes).equals(ref.sha256());
	}

	private Path cachePath(OguiAssetRef ref) {
		String ext = ref.kind() == OguiAssetKind.PNG ? ".png" : ".shl";
		return diskCacheDir.resolve(ref.sha256() + ext);
	}

	private byte @Nullable [] readVerifiedFromDisk(OguiAssetRef ref) {
		Path path = cachePath(ref);
		if (!Files.isRegularFile(path)) {
			return null;
		}
		try {
			long size = Files.size(path);
			if (size != ref.byteSize() || size > OguiAssetRef.MAX_ASSET_BYTES) {
				Files.deleteIfExists(path);
				return null;
			}
			byte[] data = Files.readAllBytes(path);
			if (!verifyBytes(ref, data)) {
				Files.deleteIfExists(path);
				return null;
			}
			return data;
		} catch (IOException e) {
			LOGGER.debug("Failed to read cached asset {}: {}", ref.shortHash(), e.getMessage());
			return null;
		}
	}

	private void writeToDisk(OguiAssetRef ref, byte[] bytes) {
		try {
			Files.createDirectories(diskCacheDir);
			Path target = cachePath(ref);
			Files.write(target, bytes);
			pruneDiskCacheIfNeeded();
		} catch (IOException e) {
			LOGGER.debug("Failed to persist cached asset {}: {}", ref.shortHash(), e.getMessage());
		}
	}

	private void pruneDiskCacheIfNeeded() {
		try (Stream<Path> stream = Files.list(diskCacheDir)) {
			var files = stream.filter(Files::isRegularFile).toList();
			long totalBytes = 0L;
			for (Path file : files) {
				totalBytes += Files.size(file);
			}
			if (totalBytes <= MAX_DISK_CACHE_BYTES) {
				return;
			}
			var sorted = files.stream()
				.sorted(Comparator.comparing(p -> {
					try {
						return Files.getLastModifiedTime(p);
					} catch (IOException e) {
						return FileTime.fromMillis(0L);
					}
				}))
				.toList();
			for (Path oldest : sorted) {
				if (totalBytes <= MAX_DISK_CACHE_BYTES) {
					break;
				}
				long size = Files.size(oldest);
				if (Files.deleteIfExists(oldest)) {
					totalBytes -= size;
				}
			}
		} catch (IOException ignored) {
		}
	}
}
