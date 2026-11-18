package me.ogsammaenr.ogui;

import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

final class OguiTestBootstrap {
	private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
	private static RegistryAccess registryAccess;

	private OguiTestBootstrap() {
	}

	static RegistryAccess ensureBootstrapped() {
		if (INITIALIZED.compareAndSet(false, true)) {
			SharedConstants.tryDetectVersion();
			Bootstrap.bootStrap();
			registryAccess = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
			HolderLookup.Provider worldLookup = VanillaRegistries.createWorldLookup();
			HolderLookup.Provider tagSafeLookup = HolderLookup.Provider.create(
				worldLookup.listRegistries().map(OguiTestBootstrap::wrapWithEmptyTags)
			);
			BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(tagSafeLookup)
				.forEach(DataComponentInitializers.PendingComponents::apply);
		}
		return registryAccess;
	}

	private static <T> HolderLookup.RegistryLookup<T> wrapWithEmptyTags(HolderLookup.RegistryLookup<T> delegate) {
		return new HolderLookup.RegistryLookup.Delegate<>() {
			@Override
			public HolderLookup.RegistryLookup<T> parent() {
				return delegate;
			}

			@Override
			public Optional<HolderSet.Named<T>> get(TagKey<T> id) {
				Optional<HolderSet.Named<T>> existing = delegate.get(id);
				return existing.isPresent() ? existing : Optional.of(HolderSet.emptyNamed(this, id));
			}
		};
	}
}
