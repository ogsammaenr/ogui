package me.ogsammaenr.ogui.api;

/**
 * Factory evaluated on the server when opening or refreshing a registered {@link OguiScreenModel}.
 */
@FunctionalInterface
public interface OguiScreenProvider {
	OguiScreenModel build(OguiBuildContext context);
}
