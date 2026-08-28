A Go game for the Mudita Kompakt. 9x9, against GNU Go or two people sharing the
phone, with [GNU Go](https://www.gnu.org/software/gnugo/) as the engine.

**1.0.4** — games can be counted again. If you have 1.0.3 or earlier, please update.

Every release before this one shipped an engine that could not score a finished game. Both
players pass, the app asks GNU Go for the result, and GNU Go aborts. Not sometimes - every
game, every time. 1.0.3 added a second engine to fall back on when the first one died, and
that one died in the same place, so the fallback never helped.

Nothing in the app was wrong, and nothing in the engine's source was wrong. The engine is
built from the GNU Go tarball in this repository by the script beside it, and that build
was being done with whatever compiler the build machine happened to have. The machine
picked up a newer one, and GNU Go - which is C from 2009 - compiled without complaint into
something that plays normally and then fails the moment a game is scored. The same source
built with the previous compiler scores every game correctly and agrees to the point with a
build for an ordinary desktop.

So the compiler is now named in the build script, which refuses to use another, and the
build machine installs that one instead of reaching for the newest. The engine binary this
release ships is byte-for-byte the one that build produces.

Worth saying plainly: this got out because nothing that runs automatically ever plays a game
to the end. The tests pass on a broken engine. It was found by finishing a game on the phone.

No permissions, no network — see [PRIVACY.md](PRIVACY.md).

arm64-v8a, which is what the Kompakt is. Verify the download against the `.sha256` beside
it if you like.

`kuroban.apk` and `kuroban-<version>.apk` are the same file. The unversioned one is there so
that a link to it keeps working after the next release.

Free software under the GPLv3. The engine's complete source is in `engine/` in this repo,
unmodified, along with the script that builds it.
