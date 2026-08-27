#!/usr/bin/env bash
#
# Build GNU Go 3.8 for the Kompakt (arm64-v8a) and drop it into the app as
# app/src/main/jniLibs/arm64-v8a/libgnugo.so
#
# The .so name is a lie of convenience: this is a plain executable, not a shared
# library. Android only extracts and grants exec permission to files matching
# lib*.so inside jniLibs, and only the app's nativeLibraryDir is exec-able for a
# non-debuggable app, so the engine has to be shaped like a library to be
# runnable at all. Chess+ ships Stockfish exactly this way.
#
# Two passes are needed:
#
#   1. A host build. GNU Go's pattern databases (patterns.c, eyes.c, fuseki9.c,
#      josekidb.c, ...) are not in the tarball - they are generated at build time
#      by helper programs (mkpat, mkeyes, joseki, ...) that must RUN, so they
#      must be compiled for the machine doing the building, not for the phone.
#   2. A cross build. Everything, generated files included, recompiled for arm64.
#
# Both passes need -fcommon. GNU Go is from 2009 and relies on tentative
# definitions in headers being merged at link time; compilers defaulted to
# -fno-common from GCC 10 / Clang 11 onwards, which turns those into duplicate
# symbol errors.
#
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD="$HERE/build"
SRC="$BUILD/gnugo-3.8"
OUT="$HERE/../app/src/main/jniLibs/arm64-v8a"

NDK="${ANDROID_NDK_HOME:-$HOME/Android/Sdk/ndk/28.2.13676358}"
API=31   # matches minSdk; the Kompakt runs Android 12 (API 31)
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64"
CC="$TOOLCHAIN/bin/aarch64-linux-android$API-clang"
STRIP="$TOOLCHAIN/bin/llvm-strip"

[ -x "$CC" ] || { echo "No NDK clang at $CC (set ANDROID_NDK_HOME)" >&2; exit 1; }

echo "==> Unpacking"
rm -rf "$BUILD"
mkdir -p "$BUILD"
tar xzf "$HERE/gnugo-3.8.tar.gz" -C "$BUILD"

echo "==> Pass 1: host build (generates the pattern databases)"
cd "$SRC"
./configure --quiet CFLAGS="-fcommon -O2"
make -j"$(nproc)" >/dev/null

# Sanity: the generated sources pass 2 depends on must now exist.
for f in patterns.c eyes.c fuseki9.c josekidb.c mcpat.c; do
  [ -s "patterns/$f" ] || { echo "Pattern generation failed: patterns/$f missing" >&2; exit 1; }
done

echo "==> Pass 2: cross build for arm64-v8a"

# Source lists lifted from the upstream Makefile.am files, minus every target
# with its own main(): the pattern generators in patterns/, and sgf/sgfgen.c.
ENGINE_SRC="aftermath board boardlib breakin cache clock combination dragon
  endgame filllib fuseki genmove globals handicap hash influence interface
  matchpat montecarlo move_reasons movelist optics oracle owl persistent
  printutils readconnect reading semeai sgfdecide sgffile shapes showbord
  surround unconditional utils value_moves worm"

# connections.c, helpers.c and transform.c are hand-written; the rest of this
# list is what pass 1 just generated.
PATTERNS_SRC="connections helpers transform
  conn patterns apatterns dpatterns eyes influence barriers endgame
  aa_attackpat owl_attackpat owl_vital_apat owl_defendpat fusekipat
  fuseki9 fuseki13 fuseki19 josekidb handipat oraclepat mcpat"

SGF_SRC="sgf_utils sgfnode sgftree"
UTILS_SRC="getopt getopt1 random gg_utils winsocket"
INTERFACE_SRC="main play_ascii play_gmp play_gtp play_solo play_test gmp gtp"

OBJDIR="$BUILD/obj"
mkdir -p "$OBJDIR"

CFLAGS="-fcommon -O2 -DHAVE_CONFIG_H -DNDEBUG
  -I$SRC -I$SRC/engine -I$SRC/patterns -I$SRC/sgf -I$SRC/utils -I$SRC/interface"

objs=()
compile_dir() {
  local dir="$1"; shift
  for base in $*; do
    local src="$SRC/$dir/$base.c"
    [ -f "$src" ] || { echo "Missing source $src" >&2; exit 1; }
    local obj="$OBJDIR/${dir}_${base}.o"
    "$CC" $CFLAGS -c "$src" -o "$obj"
    objs+=("$obj")
  done
}

compile_dir engine    "$ENGINE_SRC"
compile_dir patterns  "$PATTERNS_SRC"
compile_dir sgf       "$SGF_SRC"
compile_dir utils     "$UTILS_SRC"
compile_dir interface "$INTERFACE_SRC"

echo "==> Linking"
mkdir -p "$OUT"
"$CC" -o "$OUT/libgnugo.so" "${objs[@]}" -lm
"$STRIP" "$OUT/libgnugo.so"

ls -la "$OUT/libgnugo.so"
file "$OUT/libgnugo.so" 2>/dev/null || true
echo "==> Done"
