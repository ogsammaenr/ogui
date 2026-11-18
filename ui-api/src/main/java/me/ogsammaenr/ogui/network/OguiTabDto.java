package me.ogsammaenr.ogui.network;

import me.ogsammaenr.ogui.api.OguiIcon;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;

/**
 * Client-safe tab view DTO serialized inside {@link ScreenSnapshotDto}.
 */
public record OguiTabDto(
	String id,
	OguiIcon icon,
	Component title,
	List<Component> lore,
	boolean enabled
) {
	public OguiTabDto {
		if (id == null || id.isBlank()) {
			throw new IllegalArgumentException("OguiTabDto id must not be blank.");
		}
		Objects.requireNonNull(icon, "icon");
		Objects.requireNonNull(title, "title");
		lore = lore == null ? List.of() : List.copyOf(lore);
	}
}
