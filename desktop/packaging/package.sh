#!/usr/bin/env bash
# Builds the Muzza Desktop downloads on the devbox (Linux):
#   Muzza-Desktop-<ver>-windows-x64.zip   (unzip, run Muzza.exe; Java and ffmpeg are inside)
#   Muzza-Desktop-<ver>-linux-x64.tar.gz  (unpack, run ./muzza)
# Usage: desktop/packaging/package.sh [output dir]      (default: desktop/build/release)
# Needs: JDK 17 on PATH (for Gradle), curl, unzip, tar, xz, zip, python3 (pip ziglang is installed for the .exe).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
DESKTOP="$(dirname "$HERE")"
ROOT="$(dirname "$DESKTOP")"
VER="$(grep -oP 'val appVersion = "\K[^"]+' "$DESKTOP/build.gradle.kts")"
OUT="${1:-$DESKTOP/build/release}"
CACHE="${MUZZA_CACHE:-$HOME/.cache/muzza-desktop-build}"
WORK="$DESKTOP/build/pkg"
mkdir -p "$OUT" "$CACHE"; rm -rf "$WORK"; mkdir -p "$WORK"
MODULES="java.base,java.desktop,java.logging,java.net.http,java.xml,java.prefs,java.naming,java.management,java.sql,jdk.unsupported,jdk.crypto.ec,jdk.zipfs,jdk.accessibility,jdk.charsets,jdk.localedata"

GRADLE="$ROOT/gradlew"; [ -x "$GRADLE" ] || GRADLE="gradle"
echo "== Muzza Desktop $VER: tests"
"$GRADLE" -p "$DESKTOP" --no-daemon -q test
echo "== jars"
"$GRADLE" -p "$DESKTOP" --no-daemon -q fatJar -Ptarget=windows-x64
"$GRADLE" -p "$DESKTOP" --no-daemon -q fatJar -Ptarget=linux-x64

fetch() { # url file
  [ -s "$CACHE/$2" ] || { echo "   downloading $2"; curl -fL --retry 3 -o "$CACHE/$2.part" "$1" && mv "$CACHE/$2.part" "$CACHE/$2"; }
}
echo "== Java runtimes (Temurin 17, same build for both)"
JV="$(curl -fsSL 'https://api.adoptium.net/v3/info/release_names?release_type=ga&version=%5B17%2C18%29&page_size=1&sort_order=DESC' | python3 -c 'import sys,json;print(json.load(sys.stdin)["releases"][0])')"
echo "   $JV"
fetch "https://api.adoptium.net/v3/binary/version/$JV/linux/x64/jdk/hotspot/normal/eclipse" "$JV-linux.tar.gz"
fetch "https://api.adoptium.net/v3/binary/version/$JV/windows/x64/jdk/hotspot/normal/eclipse" "$JV-windows.zip"
[ -d "$CACHE/$JV-linux" ] || { mkdir -p "$CACHE/$JV-linux"; tar -xzf "$CACHE/$JV-linux.tar.gz" -C "$CACHE/$JV-linux" --strip-components=1; }
[ -d "$CACHE/$JV-windows" ] || { mkdir -p "$CACHE/$JV-windows.tmp"; unzip -q "$CACHE/$JV-windows.zip" -d "$CACHE/$JV-windows.tmp"; mv "$CACHE/$JV-windows.tmp"/* "$CACHE/$JV-windows"; rmdir "$CACHE/$JV-windows.tmp"; }
JLINK="$CACHE/$JV-linux/bin/jlink"
link() { "$JLINK" --module-path "$1" --add-modules "$MODULES" --strip-debug --no-header-files --no-man-pages --compress=2 --output "$2"; }

echo "== ffmpeg (BtbN static GPL builds)"
ff() { # os ext
  for name in "ffmpeg-n7.1-latest-$1-gpl-7.1" "ffmpeg-master-latest-$1-gpl"; do
    if fetch "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/$name.$2" "$name.$2" 2>/dev/null; then echo "$name.$2"; return 0; fi
  done
  return 1
}
FFWIN="$(ff win64 zip | tail -1)"; FFLIN="$(ff linux64 tar.xz | tail -1)"

echo "== Windows"
W="$WORK/Muzza"; mkdir -p "$W/app" "$W/ffmpeg"
cp "$DESKTOP/build/fat/muzza-desktop-windows-x64.jar" "$W/app/muzza.jar"
link "$CACHE/$JV-windows/jmods" "$W/runtime"
unzip -q -j -o "$CACHE/$FFWIN" '*/bin/ffmpeg.exe' -d "$W/ffmpeg"
python3 -m pip install -q --user ziglang >/dev/null 2>&1 || true
( cd "$HERE" && python3 -m ziglang cc -target x86_64-windows-gnu -municode -mwindows -O2 launcher.c launcher.rc -o "$W/Muzza.exe" ) \
  || ( cd "$HERE" && python3 -m ziglang cc -target x86_64-windows-gnu -municode -mwindows -O2 launcher.c -o "$W/Muzza.exe" && echo "   (no icon in Muzza.exe)" )
printf 'Muzza %s for Windows\r\n\r\n1. Unzip this whole folder somewhere (for example Documents).\r\n2. Double-click Muzza.exe. If Windows says "Windows protected your PC", click More info, then Run anyway (the app is not signed by Microsoft).\r\n3. Right-click Muzza.exe > Send to > Desktop (create shortcut) for a desktop icon.\r\n\r\nYour songs: everything in your Music folder (add more folders in Settings).\r\nDownloads go to Music\\Muzza.\r\n' "$VER" > "$W/READ ME.txt"
rm -f "$OUT/Muzza-Desktop-$VER-windows-x64.zip"
( cd "$WORK" && zip -qr -9 "$OUT/Muzza-Desktop-$VER-windows-x64.zip" Muzza )

echo "== Linux"
L="$WORK/linux/Muzza"; mkdir -p "$L/app" "$L/ffmpeg"
cp "$DESKTOP/build/fat/muzza-desktop-linux-x64.jar" "$L/app/muzza.jar"
link "$CACHE/$JV-linux/jmods" "$L/runtime"
tar -xJf "$CACHE/$FFLIN" -C "$L/ffmpeg" --strip-components=2 --wildcards '*/bin/ffmpeg'
cp "$HERE/muzza.sh" "$L/muzza"; cp "$HERE/install-linux.sh" "$L/install-linux.sh"; cp "$HERE/muzza-icon.png" "$L/"
chmod +x "$L/muzza" "$L/install-linux.sh" "$L/ffmpeg/ffmpeg"
tar -czf "$OUT/Muzza-Desktop-$VER-linux-x64.tar.gz" -C "$WORK/linux" Muzza

( cd "$OUT" && sha256sum "Muzza-Desktop-$VER-windows-x64.zip" "Muzza-Desktop-$VER-linux-x64.tar.gz" > "Muzza-Desktop-$VER-SHA256.txt" )
echo "== done"
ls -la "$OUT"
