# ogui — Minecraft 26.3 Hybrid GUI Library & Client Renderer

`ogui` is a domain-agnostic, zero-trust, server-authoritative GUI library and optional client renderer for **Minecraft 26.3** (Fabric Loader `0.20.1`, Fabric API `0.166.2+26.3`, Java `25`).

## Modules (Two Independent Mod JARs)

1. **`:ui-api` (`me.ogsammaenr:ogui-api:1.0.0`, `environment: *`)**
   - Declarative server-side screen/widget/theme API (`OguiService`, `OguiScreenModel`, `OguiWidget`, `OguiTheme`, `OguiOpenRequest`, `OguiShortcut`).
   - Play-phase protocol handshake (`PROTOCOL_VERSION = 1`) and automatic vanilla fallback (`OguiReadOnlyChestMenu` + `ServerGamePacketListenerImplMixin` anti-dupe protections).
   - Packet-only Fake Sign (`FakeSignInputManager`) text input fallback for vanilla clients without modifying server world chunks.
   - Consumable both as a standalone mod JAR and via Jar-in-Jar (`include`) or `mavenLocal()` (`./gradlew :ui-api:publishToMavenLocal`).

2. **`:ui-client` (`ogui-client`, `environment: client`)**
   - Independent client-only mod depending on `ogui-api` (not bundled via Jar-in-Jar).
   - Server-driven ViewModel renderer (`OguiScreen`) supporting `TABBED_PANEL`, `PAGED_GRID`, `DETAIL_FORM`, and `FIXED_GRID` with responsive scaling, mouse-wheel scrolling, `EditBox` input, quantity step pills, and inline feedback banners.
   - Local preferences (`config/ogui-client.json`) for theme preset, UI scale, and sound effects, plus server-announced quick-menu shortcuts bound by default to `G` (`key.ogui.quick_menu`).

## Verification Commands

```bash
./gradlew test
./gradlew :ui-api:runGameTest
./gradlew :ui-client:runClientGameTest
./gradlew build :ui-api:publishToMavenLocal
```

### Headless Container Client Tests

- **Docker image (`/workspace/Dockerfile`):** Installs Mesa (`llvmpipe`/`lavapipe`), EGL/GLX, X11 client libraries, and `Xvfb` system-wide and sets `SDL_VIDEODRIVER=offscreen`, `LIBGL_ALWAYS_SOFTWARE=1`, and `ALSOFT_DRIVERS=null`.
- **Rootless fallback (pre-built containers without Mesa installed):** Run once inside the container:

```bash
./scripts/setup-client-env.sh
```

`ui-client/build.gradle` configures `RunGameTask` for `SDL_VIDEODRIVER=offscreen` + Mesa software rendering whenever `DISPLAY`/`WAYLAND_DISPLAY` are absent, and automatically adds `~/.local/ogui-client-env` (or `OGUI_CLIENT_ENV`) if present.

### In-Game Client Test Commands (`/oguitest`)

Once loaded in a world (`./gradlew :ui-client:runClient` or PrismLauncher with `ogui-api` + `ogui-client`), use client-side commands to inspect every archetype and feature interactively:

- `/oguitest open <tabbed|paged|detail|fixed>` — Opens an interactive client-side preview of the chosen `LayoutArchetype` (works on any server or singleplayer without server registration).
- `/oguitest prefs` — Opens `OguiPreferencesScreen` (theme preset, UI scale, sounds, volume).
- `/oguitest launcher` — Opens `OguiQuickLauncherScreen` with sample quick-menu entries.
- `/oguitest status` — Prints protocol handshake state, registered server shortcut count, bound Quick Menu key, and active preferences.
- `/oguitest live <tabbed|paged|detail|fixed|shortcuts>` — Singleplayer only: registers screens/shortcuts on `Ogui.service()` and runs over the live `S2C`/`C2S` packet pipeline (use `shortcuts` then press `G`).
