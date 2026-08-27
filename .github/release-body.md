A Go game for the Mudita Kompakt. 9x9, against GNU Go or two people sharing the
phone, with [GNU Go](https://www.gnu.org/software/gnugo/) as the engine.

**1.0.3** — a finished game is no longer lost when the engine fails to count it.

GNU Go can abort inside its own scoring code: `findstones` handed a pass where a stone
belongs, in `final_score`. It is not reliable - the same finished position aborted twice
and then counted correctly on the third attempt - and nothing is actually lost when it
happens, because every move of the game is known. So the app now builds a second engine,
replays the game into it, and asks that one to count instead. Only if that fails too does
it give up.

The line under a result explaining the count - the komi, the handicap, whether stones were
counted as dead - has never once appeared. It does now.

A crash also names the right game: GNU Go reports its own seed as 0 over GTP, so the number
offered for replaying a failure was 0 and no use to anyone. It is now the seed the app
actually gave it. Alongside what the engine said as it died, the app now records what it
was asked.

No permissions, no network — see [PRIVACY.md](PRIVACY.md).

arm64-v8a, which is what the Kompakt is. Verify the download against the `.sha256` beside
it if you like.

`eink-go.apk` and `eink-go-<version>.apk` are the same file. The unversioned one is there so
that a link to it keeps working after the next release.

Free software under the GPLv3. The engine's complete source is in `engine/` in this repo,
unmodified, along with the script that builds it.
