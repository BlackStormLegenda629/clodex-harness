#!/bin/sh
# Downloads Inter and JetBrains Mono once and drops the 5 files the app needs into res/font.
# Build-time only: the app itself never loads fonts from the network.
# Release URLs below are from memory, not verified. If one 404s, download the zips by hand
# and copy the files under the names listed at the bottom.
set -eu
OUT="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/res/font"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$OUT"

curl -fL -o "$TMP/inter.zip" "https://github.com/rsms/inter/releases/download/v4.0/Inter-4.0.zip"
curl -fL -o "$TMP/jb.zip" "https://github.com/JetBrains/JetBrainsMono/releases/download/v2.304/JetBrainsMono-2.304.zip"
unzip -q "$TMP/inter.zip" -d "$TMP/inter"
unzip -q "$TMP/jb.zip" -d "$TMP/jb"

pick() { # pick <dir> <file name> <target name>
  f="$(find "$1" -type f -name "$2" | head -n 1)"
  [ -n "$f" ] || { echo "missing $2" >&2; exit 1; }
  cp "$f" "$OUT/$3"
}
pick "$TMP/inter" "Inter-Regular.ttf" inter_regular.ttf
pick "$TMP/inter" "Inter-Medium.ttf" inter_medium.ttf
pick "$TMP/inter" "Inter-SemiBold.ttf" inter_semibold.ttf
pick "$TMP/jb" "JetBrainsMono-Regular.ttf" jetbrains_mono_regular.ttf
pick "$TMP/jb" "JetBrainsMono-Medium.ttf" jetbrains_mono_medium.ttf
echo "fonts in $OUT"
# Required names: inter_regular.ttf inter_medium.ttf inter_semibold.ttf
#                 jetbrains_mono_regular.ttf jetbrains_mono_medium.ttf
