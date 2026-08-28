# Privacy

eInk GO collects nothing, sends nothing, and asks for no permissions.

That is the whole policy. The rest of this page is only the evidence for it, because a
privacy policy that cannot be checked is just a promise.

## No permissions

`app/src/main/AndroidManifest.xml` declares no `<uses-permission>` at all. Not network, not
storage, not identifiers. Android will not grant the app anything it has not asked for, so
there is no data it is capable of reaching.

## No network

There is no networking code in this app, and no dependency that provides any. Without the
`INTERNET` permission the app could not open a connection even if there were. Nothing about
your games — moves, results, settings, how often you play — can leave the device, because
there is no route off it.

The opponent is not a service. It is [GNU Go](https://www.gnu.org/software/gnugo/), a
program included in the APK, which runs on your phone as a child process and is spoken to
over a pipe.

## What is stored, and where

One file, in the app's own private storage, holding the game currently in progress:

```
files/game-in-progress.txt
```

It contains a format version, your four game settings (opponent, difficulty, your colour,
handicap), and the list of moves played so far — for example `E5 D3 pass F4`. It exists so
that turning your phone off in the middle of a game does not lose the game. It is deleted
when the game is finished, resigned, or left.

Android keeps that directory private to the app. Uninstalling removes it.

Nothing else is written. There are no analytics, no crash reporting, no advertising
identifiers, no logs kept between runs, and no accounts.

## No third-party services

The app has no SDKs for analytics, advertising, attribution, or crash reporting. Its
dependencies are AndroidX and Jetpack Compose, Mudita's MMD design system, and the bundled
GNU Go engine — none of which the app uses to communicate with anything.

## Verifying this yourself

You do not have to take any of it on trust:

- The source is at <https://github.com/wanderwildwood/kuroban>, and each release is tagged.
- `grep -r uses-permission app/src/main/AndroidManifest.xml` returns nothing.
- Any APK can be checked with `aapt dump permissions` or by opening it as a zip.

## Changes

If this ever stops being true, this file changes in the same commit as the code that
changed it, and the release notes will say so plainly.
