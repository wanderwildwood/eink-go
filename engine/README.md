# The engine

[GNU Go 3.8](https://www.gnu.org/software/gnugo/), the last release, from February 2009.

`gnugo-3.8.tar.gz` is the pristine upstream tarball, fetched from
`https://ftp.gnu.org/gnu/gnugo/gnugo-3.8.tar.gz`. Its checksum is in
`gnugo-3.8.tar.gz.sha256`.

**Upstream is not modified.** No patches are applied and no source file is edited. The
only thing this project does differently is compile it for arm64 Android and pass it
`-fcommon`. That matters for the GPL: the complete corresponding source for the engine in
the shipped APK is exactly this tarball plus `build-gnugo.sh`.

## Why the build takes two passes

Run `./build-gnugo.sh` and it builds GNU Go twice.

GNU Go's pattern databases - `patterns.c`, `eyes.c`, `fuseki9.c`, `josekidb.c` and the
rest - are not in the tarball. They are generated at build time by helper programs
(`mkpat`, `mkeyes`, `joseki`, ...) that are themselves compiled from the tarball. Those
helpers have to *run*, so they must be built for the machine doing the building, not for
the phone. So:

1. **Host pass.** A normal `./configure && make` on this workstation, which produces the
   generated pattern sources as a side effect.
2. **Cross pass.** Everything, those generated files included, recompiled for arm64-v8a
   with the NDK's clang and linked into one executable.

## Two things that will bite you

**`-fcommon` is required, on both passes.** GNU Go relies on tentative definitions in
headers being merged at link time. GCC 10 and Clang 11 changed the default to
`-fno-common`, which turns those into duplicate-symbol errors. Without the flag the host
pass dies at `mkmcpat` with `multiple definition of 'meaningless_white_moves'`.

**The output is named `libgnugo.so` and is not a library.** It is a plain PIE executable.
Android only unpacks files matching `lib*.so` out of `jniLibs` onto disk and marks them
executable, and for a non-debuggable app the app's `nativeLibraryDir` is the only place a
binary can be exec'd from at all. So the engine has to be shaped like a shared library to
be runnable. `useLegacyPackaging = true` in the app's Gradle config is what forces it to
be extracted rather than loaded from inside the APK.

## Size

The engine is about 8 MB, most of it pattern tables, and compresses to roughly 2.5 MB in
the APK. A 9x9-only app does not need the 19x19 fuseki book or the Monte Carlo pattern
tables, and dropping them would cut it appreciably - but that would mean editing upstream
source, which is a trade this project has not taken.
