package me.ogsammaenr.ogui.client;

import me.ogsammaenr.ogui.api.Ogui;
import me.ogsammaenr.ogui.api.OguiAssetKind;
import me.ogsammaenr.ogui.api.OguiAssetRef;
import me.ogsammaenr.ogui.api.OguiStructureShell;
import me.ogsammaenr.ogui.client.assets.OguiClientAssetManager;
import me.ogsammaenr.ogui.network.S2CAssetChunkPayload;
import me.ogsammaenr.ogui.runtime.OguiAssetRegistry;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OguiClientAssetManagerTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void reassemblesChunksPersistsToDiskAndRejectsCorruptCache(@TempDir Path tempDir) throws Exception {
		OguiStructureShell shell = OguiStructureShell.fromBlocks(List.of(
			new OguiStructureShell.PositionedBlock(0, 0, 0, Blocks.GRASS_BLOCK.defaultBlockState()),
			new OguiStructureShell.PositionedBlock(1, 0, 0, Blocks.OAK_LOG.defaultBlockState())
		));
		byte[] bytes = shell.toCompressedBytes();
		String sha = OguiAssetRegistry.sha256Hex(bytes);
		OguiAssetRef ref = new OguiAssetRef(sha, OguiAssetKind.STRUCTURE_SHELL, bytes.length);

		List<String> requested = new ArrayList<>();
		OguiClientAssetManager manager = new OguiClientAssetManager(
			tempDir,
			requested::add,
			(r, raw) -> new OguiClientAssetManager.PngAsset(Ogui.id("test"), 1, 1),
			slot -> {}
		);

		OguiClientAssetManager.AssetSlot initial = manager.resolveOrRequest(ref);
		assertEquals(OguiClientAssetManager.Status.LOADING, initial.status());
		assertEquals(List.of(sha), requested);

		// Calling again while still loading must not spam network requests
		manager.resolveOrRequest(ref);
		assertEquals(1, requested.size());

		// Split into 3 chunks and deliver out of order
		int p1 = bytes.length / 3;
		int p2 = (bytes.length * 2) / 3;
		byte[] c0 = Arrays.copyOfRange(bytes, 0, p1);
		byte[] c1 = Arrays.copyOfRange(bytes, p1, p2);
		byte[] c2 = Arrays.copyOfRange(bytes, p2, bytes.length);

		manager.onAssetChunk(new S2CAssetChunkPayload(sha, OguiAssetKind.STRUCTURE_SHELL, bytes.length, 2, 3, c2));
		assertEquals(OguiClientAssetManager.Status.LOADING, initial.status());
		assertTrue(initial.progress() > 0.0F);

		manager.onAssetChunk(new S2CAssetChunkPayload(sha, OguiAssetKind.STRUCTURE_SHELL, bytes.length, 0, 3, c0));
		manager.onAssetChunk(new S2CAssetChunkPayload(sha, OguiAssetKind.STRUCTURE_SHELL, bytes.length, 1, 3, c1));

		assertEquals(OguiClientAssetManager.Status.READY, initial.status());
		assertNotNull(initial.structure());
		assertEquals(shell, initial.structure().shell());

		// Second manager using same disk cache should load offline with zero network requests
		AtomicInteger cacheMissRequests = new AtomicInteger();
		OguiClientAssetManager warmManager = new OguiClientAssetManager(
			tempDir,
			h -> cacheMissRequests.incrementAndGet(),
			(r, raw) -> new OguiClientAssetManager.PngAsset(Ogui.id("test"), 1, 1),
			slot -> {}
		);
		OguiClientAssetManager.AssetSlot cachedSlot = warmManager.resolveOrRequest(ref);
		assertEquals(OguiClientAssetManager.Status.READY, cachedSlot.status());
		assertEquals(0, cacheMissRequests.get());

		// Corrupt cached file on disk -> third manager must detect SHA-256 mismatch and re-request
		Path cachedFile = tempDir.resolve(sha + ".shl");
		byte[] corrupted = Files.readAllBytes(cachedFile);
		corrupted[0] ^= 0x5A;
		Files.write(cachedFile, corrupted);

		AtomicInteger reRequests = new AtomicInteger();
		OguiClientAssetManager corruptManager = new OguiClientAssetManager(
			tempDir,
			h -> reRequests.incrementAndGet(),
			(r, raw) -> new OguiClientAssetManager.PngAsset(Ogui.id("test"), 1, 1),
			slot -> {}
		);
		OguiClientAssetManager.AssetSlot recoverySlot = corruptManager.resolveOrRequest(ref);
		assertEquals(OguiClientAssetManager.Status.LOADING, recoverySlot.status());
		assertEquals(1, reRequests.get());

		corruptManager.onAssetUnavailable(sha);
		assertEquals(OguiClientAssetManager.Status.FAILED, recoverySlot.status());
	}
}
