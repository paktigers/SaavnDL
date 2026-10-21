#!/usr/bin/env bash
# Cross-compiles LAME (libmp3lame.a) for Android into app/src/main/cpp/lame/<abi>/.
# Run on a Linux machine/runner that has the Android NDK. Safe to fail: the app then builds without MP3 support.
set -euo pipefail

LAME_VER=3.100
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/app/src/main/cpp/lame"
WORK="$ROOT/build_lame"
API=24

NDK="${ANDROID_NDK_ROOT:-${ANDROID_NDK_HOME:-}}"
if [ -z "$NDK" ] || [ ! -d "$NDK" ]; then
  NDK="$(ls -d "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"/ndk/* 2>/dev/null | sort -V | tail -1)"
fi
[ -d "$NDK" ] || { echo "Android NDK not found"; exit 1; }
TC="$NDK/toolchains/llvm/prebuilt/linux-x86_64"
echo "Using NDK: $NDK"

mkdir -p "$WORK" && cd "$WORK"
rm -f lame.tgz
for url in \
  "https://downloads.sourceforge.net/project/lame/lame/$LAME_VER/lame-$LAME_VER.tar.gz" \
  "https://sourceforge.net/projects/lame/files/lame/$LAME_VER/lame-$LAME_VER.tar.gz/download" \
  "https://fossies.org/linux/misc/lame-$LAME_VER.tar.gz"; do
  echo "Downloading $url"
  if curl -fL --retry 3 --connect-timeout 25 -o lame.tgz "$url" && tar tzf lame.tgz >/dev/null 2>&1; then break; fi
  rm -f lame.tgz
done
[ -f lame.tgz ] || { echo "Could not download LAME"; exit 1; }
rm -rf "lame-$LAME_VER" && tar xzf lame.tgz

build_abi() {
  local abi="$1" host="$2" clang="$3"
  echo "=== Building LAME for $abi ==="
  rm -rf "src-$abi" && cp -r "lame-$LAME_VER" "src-$abi"
  pushd "src-$abi" >/dev/null
  # lame-3.100 ships an old config.sub that does not know the *-android triplets
  for f in config.sub config.guess; do
    for d in /usr/share/misc /usr/share/automake-*; do
      [ -f "$d/$f" ] && cp "$d/$f" "$f" && break
    done
  done
  export CC="$TC/bin/${clang}${API}-clang"
  export AR="$TC/bin/llvm-ar"
  export RANLIB="$TC/bin/llvm-ranlib"
  export STRIP="$TC/bin/llvm-strip"
  ./configure --host="$host" --enable-static --disable-shared --disable-frontend --disable-decoder \
    --disable-analyzer-hooks --with-pic --prefix="$OUT/$abi" CFLAGS="-O2 -fPIC" >/dev/null
  make -j4 >/dev/null
  make install >/dev/null
  popd >/dev/null
  test -f "$OUT/$abi/lib/libmp3lame.a"
}

rm -rf "$OUT"
build_abi arm64-v8a aarch64-linux-android aarch64-linux-android
build_abi armeabi-v7a arm-linux-androideabi armv7a-linux-androideabi
echo "LAME ready in $OUT"
