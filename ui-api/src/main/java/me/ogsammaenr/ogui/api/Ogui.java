package me.ogsammaenr.ogui.api;

import me.ogsammaenr.ogui.runtime.OguiSessionManager;
import net.minecraft.resources.Identifier;

/**
 * Global facade and constants for the {@code ogui-api} hybrid GUI library.
 */
public final class Ogui {
	public static final String MOD_ID = "ogui-api";
	public static final String NAMESPACE = "ogui";
	public static final int PROTOCOL_VERSION = 2;

	private Ogui() {
	}

	/**
	 * Creates a namespaced {@link Identifier} under the {@code ogui} namespace.
	 */
	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(NAMESPACE, path);
	}

	/**
	 * Returns the singleton {@link OguiService} instance managing screens, sessions, and shortcuts.
	 */
	public static OguiService service() {
		return OguiSessionManager.instance();
	}
}
