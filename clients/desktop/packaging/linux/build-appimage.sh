#!/usr/bin/env bash
# Wraps the app folder from `./gradlew :desktop:packageAppImage` into one RomSafe-<version>-x86_64.AppImage.
# Usage (from clients/): desktop/packaging/linux/build-appimage.sh <version> <output dir>
# An AppImage runs on SteamOS (read-only system, no apt) and most other distros without installing.
set -euo pipefail

version="$1"
out="$(mkdir -p "$2" && cd "$2" && pwd)"
here="$(cd "$(dirname "$0")" && pwd)"
app="$here/../../build/compose/binaries/main/app/RomSafe"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

[ -x "$app/bin/RomSafe" ] || { echo "No app folder at $app; run ./gradlew :desktop:packageAppImage first" >&2; exit 1; }

# Pinned, so a changed download fails the build instead of shipping.
tool_version=1.9.1
tool_sha256=ed4ce84f0d9caff66f50bcca6ff6f35aae54ce8135408b3fa33abfc3cb384eb0
tool="$work/appimagetool"
curl -fsSL -o "$tool" "https://github.com/AppImage/appimagetool/releases/download/$tool_version/appimagetool-x86_64.AppImage"
echo "$tool_sha256  $tool" | sha256sum -c -
chmod +x "$tool"

appdir="$work/RomSafe.AppDir"
cp -a "$app" "$appdir"
cp "$here/romsafe.desktop" "$appdir/romsafe.desktop"
cp "$here/../../icons/romsafe.png" "$appdir/romsafe.png"
ln -s romsafe.png "$appdir/.DirIcon"
cat > "$appdir/AppRun" <<'EOF'
#!/bin/sh
exec "$(dirname "$(readlink -f "$0")")/bin/RomSafe" "$@"
EOF
chmod +x "$appdir/AppRun"

# Extract-and-run: CI runners have no FUSE to mount the tool itself.
ARCH=x86_64 APPIMAGE_EXTRACT_AND_RUN=1 "$tool" --no-appstream "$appdir" "$out/RomSafe-$version-x86_64.AppImage"
