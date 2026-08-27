# eInk GO

A game of Go for the [Mudita Kompakt](https://mudita.com/products/kompakt/), built for its
E Ink screen. 9x9, against the computer or two people passing one phone back and forth.

Not a fork. Written from scratch in Kotlin and Jetpack Compose, using Mudita's own
[MMD](https://github.com/mudita/MMD) design system so it looks like the apps the phone
already ships with, with GNU Go bundled as the engine.

## What it does

- **9x9 board.** Only. The engine handles any size and the board would scale, but 19x19 on
  a 360dp-wide panel gives 18dp touch targets, which is not a game anyone enjoys playing.
- **Against the computer**, at three strengths, playing either colour.
- **Two players** on one device, pass-and-play.
- **Tap to preview, tap again to place.** A stone is never played by a stray touch. The
  Place button does the same thing for anyone who would rather press a button.
- **Take back a move** - and against the computer, its reply along with it. A finished
  game can be taken back into, so a game lost to one careless move is still worth looking at.
- **Proper scoring.** Two passes end the game and GNU Go scores it, including working out
  which stones are dead. Those are marked with a × on the board.
- **No permissions, no network.** Nothing leaves the phone. The engine is a local binary.

## The engine

GNU Go 3.8 runs as a child process and is spoken to over
[GTP](https://www.lysator.liu.se/~gunnar/gtp/) on its stdin/stdout, the same way Chess+
talks UCI to Stockfish.

The engine is authoritative for *everything*: legality, ko, captures, and scoring. This
app implements no Go rules of its own - it asks. That is only affordable because GNU Go
answers a 9x9 position in single-digit milliseconds on this phone, and it means the board
you see can never disagree with the rules being enforced.

`engine/build-gnugo.sh` builds it. See [engine/README.md](engine/README.md) for why that
takes two passes and what it does to the binary's name.

## Building

Needs the Android SDK, and the NDK plus a host C compiler if you are rebuilding the engine.

```sh
./engine/build-gnugo.sh     # only needed if lib/arm64-v8a/libgnugo.so is missing or stale
./gradlew assembleRelease
```

Release builds are signed with a keystore in `signing/`, which is gitignored. Without it
the build falls back to the default debug key rather than to a checked-in one, because a
signing key committed to a public repo is not a signing key, it is a formality.

## Licence

GPLv3. See [LICENSE](LICENSE).

This app bundles [GNU Go](https://www.gnu.org/software/gnugo/), which is
Copyright 1999-2009 by the Free Software Foundation, Inc. and licensed under the GPLv3.
Bundling it makes this whole work GPLv3 too. GNU Go's complete corresponding source is in
`engine/gnugo-3.8.tar.gz`, unmodified, along with the script that builds it.
