#!/usr/bin/env bash
# Builds the obfuscated Storage Spaces release zip with MCP 7.2, the same way it is done by hand:
#   decompile.sh -> copy Kirmod + Storage Spaces sources into src/minecraft -> recompile.sh
#   -> reobfuscate.sh -> zip the Storage Spaces classes from reobf/minecraft
#
# Storage Spaces is a Kirmod plugin, so it compiles against Kirmod's sources (and the CommandLib stubs
# in Kirmod's ci/libs). Only Storage Spaces' own classes end up in the zip.
#
# Needs: Python 2.7, a Java 8 JDK (javac must accept -source 1.6), curl, patch, sha1sum/sha256sum.
# Run from anywhere; output lands in dist/ at the repository root.
#
# Environment overrides:
#   PYTHON     Python 2.7 interpreter MCP runs with (default: python)
#   WORK_DIR   scratch directory for the MCP workspace (default: <repo>/build)
#   MCP_ZIP    use a local mcp72.zip instead of downloading it
#   KIRMOD_DIR checkout of the Kirmod repository (default: <repo>/kirmod if present, else <repo>/../Kirmod)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PYTHON="${PYTHON:-python}"
WORK_DIR="${WORK_DIR:-$ROOT/build}"
MCP_DIR="$WORK_DIR/mcp"
DIST_DIR="$ROOT/dist"
if [ -z "${KIRMOD_DIR:-}" ]; then
    if [ -d "$ROOT/kirmod/src" ]; then KIRMOD_DIR="$ROOT/kirmod"; else KIRMOD_DIR="$ROOT/../Kirmod"; fi
fi
KIRMOD_DIR="$(cd "$KIRMOD_DIR" && pwd)"
test -f "$KIRMOD_DIR/src/net/minecraft/src/mod_Kirmod.java" || { echo "Kirmod sources not found in $KIRMOD_DIR (set KIRMOD_DIR)" >&2; exit 1; }

MCP_URL="https://archive.org/download/minecraftcoderpack/minecraftcoderpack.zip/minecraftcoderpack%2F1.3.2%2Fmcp72.zip"
MCP_SHA256="393a4ec7e18baf0e129afb8f0508e77a520cb9cdd5bd6e24d662152498258273"
CLIENT_URL="https://launcher.mojang.com/v1/objects/c2efd57c7001ddf505ca534e54abf3d006e48309/client.jar"
CLIENT_SHA1="c2efd57c7001ddf505ca534e54abf3d006e48309"
LIBS="https://libraries.minecraft.net"
# Risugami's ModLoader for 1.3.2, as archived on archive.org (no compiled classes live in this repo).
MODLOADER_URL="https://archive.org/download/risugamis-mod-loader-all-versions/ModLoader-r1.3.2.00%2Bbuild.1.jar"
MODLOADER_SHA256="cb188a0b760152c07a8583bdb39886f49211e7bd34fe7b615b2a0dc18ff728da"

log() { printf '\n==> %s\n' "$*"; }

fetch() { # url dest
    curl -fsSL --retry 3 -o "$2" "$1"
}

log "Preparing MCP 7.2 workspace in $MCP_DIR"
rm -rf "$MCP_DIR"
mkdir -p "$WORK_DIR" "$MCP_DIR"
if [ -n "${MCP_ZIP:-}" ]; then
    cp "$MCP_ZIP" "$WORK_DIR/mcp72.zip"
elif [ ! -f "$WORK_DIR/mcp72.zip" ]; then
    fetch "$MCP_URL" "$WORK_DIR/mcp72.zip"
fi
echo "$MCP_SHA256  $WORK_DIR/mcp72.zip" | sha256sum -c -
"$PYTHON" -m zipfile -e "$WORK_DIR/mcp72.zip" "$MCP_DIR"
chmod +x "$MCP_DIR"/*.sh
# MCP refuses to run as root (to protect wine prefixes), but CI containers run as root.
if [ "$(id -u)" = 0 ]; then
    CMDS="$MCP_DIR/runtime/commands.py"
    sed -i 's/if not os\.getuid():/if False:/' "$CMDS"
    grep -q 'if False:  # pylint' "$CMDS" || { echo "MCP root-check patch did not apply" >&2; exit 1; }
fi

log "Downloading Minecraft 1.3.2 client and LWJGL"
BIN="$MCP_DIR/jars/bin"
mkdir -p "$BIN/natives"
fetch "$CLIENT_URL" "$WORK_DIR/client.jar"
echo "$CLIENT_SHA1  $WORK_DIR/client.jar" | sha1sum -c -
fetch "$LIBS/org/lwjgl/lwjgl/lwjgl/2.9.0/lwjgl-2.9.0.jar" "$BIN/lwjgl.jar"
fetch "$LIBS/org/lwjgl/lwjgl/lwjgl_util/2.9.0/lwjgl_util-2.9.0.jar" "$BIN/lwjgl_util.jar"
fetch "$LIBS/net/java/jinput/jinput/2.0.5/jinput-2.0.5.jar" "$BIN/jinput.jar"

log "Installing ModLoader into minecraft.jar"
fetch "$MODLOADER_URL" "$WORK_DIR/modloader.jar"
echo "$MODLOADER_SHA256  $WORK_DIR/modloader.jar" | sha256sum -c -
# Same as installing ModLoader by hand: drop its classes into the jar and remove META-INF.
"$PYTHON" - "$WORK_DIR/client.jar" "$WORK_DIR/modloader.jar" "$BIN/minecraft.jar" <<'EOF'
import sys, zipfile
src, modloader, dest = sys.argv[1:4]
zml = zipfile.ZipFile(modloader)
added = [n for n in zml.namelist() if n.endswith('.class') and '/' not in n]
zin = zipfile.ZipFile(src)
zout = zipfile.ZipFile(dest, 'w', zipfile.ZIP_DEFLATED)
for item in zin.infolist():
    if item.filename.startswith('META-INF/') or item.filename in added:
        continue
    zout.writestr(item, zin.read(item.filename))
for name in added:
    zout.writestr(name, zml.read(name))
zout.close()
print('added %d ModLoader classes' % len(added))
EOF

cd "$MCP_DIR"

log "Decompiling (decompile.sh)"
"$PYTHON" runtime/decompile.py

log "Applying vanilla source tweaks"
# Kirmod's KirTessellator subclasses Tessellator, whose constructor is private in vanilla. Neither
# release ships a modified Tessellator, so this edit is only needed to compile.
TESS=src/minecraft/net/minecraft/src/Tessellator.java
sed -i "s/private Tessellator(int par1)/protected Tessellator(int par1)/" "$TESS"
grep -q 'protected Tessellator(int par1)' "$TESS" || { echo "Tessellator tweak did not apply" >&2; exit 1; }

log "Copying Kirmod ($KIRMOD_DIR) and Storage Spaces sources into src/minecraft"
cp -R "$KIRMOD_DIR/src/." src/minecraft/
cp -R "$KIRMOD_DIR/ci/libs/." src/minecraft/
cp -R "$ROOT/src/." src/minecraft/

log "Recompiling (recompile.sh)"
LOG_START=$(($(wc -l < logs/mcp.log) + 1))
"$PYTHON" runtime/recompile.py
# recompile.py exits 0 even when javac fails, so check what it logged.
if tail -n "+$LOG_START" logs/mcp.log | grep -q '== ERRORS FOUND =='; then
    echo "Compilation failed:" >&2
    tail -n "+$LOG_START" logs/mcp.log | sed -n '/== ERRORS FOUND ==/,/==================/p' >&2
    exit 1
fi

log "Reobfuscating (reobfuscate.sh)"
"$PYTHON" runtime/reobfuscate.py
test -f reobf/minecraft/mod_KirmodStorageSpaces.class || { echo "reobf/minecraft/mod_KirmodStorageSpaces.class missing" >&2; exit 1; }

# The version is the second argument of mod_KirmodStorageSpaces.PLUGIN: new KirPlugin("Storage Spaces", "1.2", ...).
VERSION="$(sed -n 's/.*new KirPlugin("Storage Spaces", "\([^"]*\)".*/\1/p' "$ROOT/src/net/minecraft/src/mod_KirmodStorageSpaces.java" | head -n1)"
VERSION="${VERSION:-dev}"
ZIP_NAME="mod_KirmodStorageSpaces-$VERSION.zip"

log "Packaging $ZIP_NAME"
STAGE="$WORK_DIR/stage"
rm -rf "$STAGE"
mkdir -p "$STAGE" "$DIST_DIR"
# Ship only classes whose outer class has a source file in this repo's src/ (no Kirmod, CommandLib or
# vanilla classes: those come from Kirmod's own zip).
SHIPPED=0
for f in reobf/minecraft/*.class; do
    name="$(basename "$f" .class)"
    if [ -f "$ROOT/src/net/minecraft/src/${name%%\$*}.java" ]; then
        cp "$f" "$STAGE/"
        SHIPPED=$((SHIPPED + 1))
    fi
done
if [ -d "$ROOT/resources" ]; then cp -R "$ROOT/resources/." "$STAGE/"; fi
rm -f "$DIST_DIR/$ZIP_NAME"
"$PYTHON" - "$STAGE" "$DIST_DIR/$ZIP_NAME" <<'EOF'
import os, sys, zipfile
stage, dest = sys.argv[1:3]
z = zipfile.ZipFile(dest, 'w', zipfile.ZIP_DEFLATED)
for base, dirs, files in os.walk(stage):
    dirs.sort()
    for name in sorted(files):
        path = os.path.join(base, name)
        z.write(path, os.path.relpath(path, stage))
z.close()
EOF

echo "zip_name=$ZIP_NAME" >> "${GITHUB_OUTPUT:-/dev/null}"
echo "version=$VERSION" >> "${GITHUB_OUTPUT:-/dev/null}"
log "Built $DIST_DIR/$ZIP_NAME ($SHIPPED classes)"
