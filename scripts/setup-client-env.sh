#!/usr/bin/env bash
set -euo pipefail

# Sets up a rootless userspace Mesa (llvmpipe OpenGL + lavapipe Vulkan + EGL/GLX)
# runtime prefix for running Minecraft 26.3 Fabric Client GameTests inside headless
# containers without GPU hardware (/dev/dri) or root/sudo privileges.

PREFIX="${OGUI_CLIENT_ENV:-$HOME/.local/ogui-client-env}"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

echo "[ogui] Setting up headless client runtime prefix at: $PREFIX"
mkdir -p "$PREFIX/etc" "$WORK_DIR/lists/partial" "$WORK_DIR/cache/archives/partial" "$WORK_DIR/dpkg"

if [[ -r /var/lib/dpkg/status ]]; then
	cp /var/lib/dpkg/status "$WORK_DIR/dpkg/status"
else
	touch "$WORK_DIR/dpkg/status"
fi

cat <<EOF > "$WORK_DIR/apt.conf"
Dir::State "$WORK_DIR";
Dir::State::status "$WORK_DIR/dpkg/status";
Dir::State::lists "$WORK_DIR/lists";
Dir::Cache "$WORK_DIR/cache";
Dir::Cache::archives "$WORK_DIR/cache/archives";
EOF

apt-get -c "$WORK_DIR/apt.conf" update
apt-get -c "$WORK_DIR/apt.conf" -d -y --reinstall install \
	libgl1 libglx0 libglx-mesa0 libglvnd0 libegl1 libegl-mesa0 libgl1-mesa-dri \
	libvulkan1 mesa-vulkan-drivers \
	libx11-xcb1 libxkbcommon0 libxrender1 libxtst6 libxi6 libxrandr2 libxfixes3 libxcursor1

count=0
for deb in "$WORK_DIR/cache/archives/"*.deb; do
	dpkg-deb -x "$deb" "$PREFIX"
	count=$((count + 1))
done

if [[ -f "$PREFIX/usr/share/vulkan/icd.d/lvp_icd.json" ]]; then
	sed "s|\"/usr/|\"$PREFIX/usr/|g" "$PREFIX/usr/share/vulkan/icd.d/lvp_icd.json" > "$PREFIX/etc/lvp_icd.json"
fi

echo "[ogui] Extracted $count packages into $PREFIX"
echo "[ogui] Run client tests with: ./gradlew :ui-client:runClientGameTest"
