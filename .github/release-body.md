A Go game for the Mudita Kompakt. 9x9, against GNU Go or two people sharing the
phone, with [GNU Go](https://www.gnu.org/software/gnugo/) as the engine.

**1.0.2** — the opponent is called GNU Go now, rather than "Computer": it is a particular
engine with a particular way of playing, and it is the reason a finished game can be
counted properly.

The rest is about an engine that stops. GNU Go can hit an assertion inside itself and
abort - it happened twice here - and everything it says on its way out, including the
position it died on, used to be thrown away. It is now kept: the game gets a number that
replays it move for move, and that number is on the screen when it happens. A game that
ends this way says so in a dialog rather than in a line of title bar too narrow to hold
it, and an answer from the engine that is not a move is no longer mistaken for a pass.

Tapping the result at the top of a finished game brings back the full score.

No permissions, no network — see [PRIVACY.md](PRIVACY.md).

arm64-v8a, which is what the Kompakt is. Verify the download against the `.sha256` beside
it if you like.

`eink-go.apk` and `eink-go-<version>.apk` are the same file. The unversioned one is there so
that a link to it keeps working after the next release.

Free software under the GPLv3. The engine's complete source is in `engine/` in this repo,
unmodified, along with the script that builds it.
