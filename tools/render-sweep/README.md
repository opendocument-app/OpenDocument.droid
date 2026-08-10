# render-sweep

Opens every document of a corpus on a connected device, one at a time, and screenshots
what the app made of it.

The instrumented tests open ten files. `OpenDocument.core`'s own input corpus has a couple
of hundred, across formats no test here touches, and the app now hands almost all of them to
`CoreLoader` (see the supported-file-types section of `CLAUDE.md`). This walks that corpus so
a format that renders blank, renders half, or takes the process down with it is something you
can look at rather than something a user reports.

It is a **looking** tool, not a test: nothing here asserts, and nothing here fails a build.
The output is a table of signals plus a screenshot per document, and a human (or a model)
decides what "broken" means. There is deliberately no attempt to diff against the core's
reference output — that comparison already exists in the core's own suite, and the question
here is what the *app* shows, WebView, chrome and all.

## Running it

You need a device or emulator on `adb`, the pro debug build installed on it, and a checkout
of `OpenDocument.core` with its test submodules initialised.

```sh
./gradlew assembleProDebug
tools/render-sweep/render-sweep.sh --install
```

`--install` pushes the apk it just built; drop it on later runs. By default the corpus is the
sibling checkout `../OpenDocument.core/test/data/input` and results land in
`build/render-sweep` (gitignored, like the rest of `build/`).

```sh
# somewhere else, or just one format, or just a few
tools/render-sweep/render-sweep.sh --corpus ~/corpora/docs
tools/render-sweep/render-sweep.sh --filter '\.ods$'
tools/render-sweep/render-sweep.sh --filter 'odr-public' --limit 20
```

A full run over ~225 documents takes about 80 minutes, most of it the fixed wait after each
launch. The screen is on and rendering the whole time, so put the device on a charger.

## What you get

```
build/render-sweep/
  results.tsv     one row per document: launch status, png size, text nodes, signal
  shots/          full resolution screenshot per document
  small/          the same, downscaled, for flipping through quickly
  ui/             uiautomator dump per document - the text the WebView actually showed
  logs/           crash buffer and error lines per document
```

The `signal` column is a triage hint, not a verdict:

| signal | what it means |
| --- | --- |
| `ok` | launched, survived, nothing obviously wrong — still worth a look |
| `CRASH` / `CRASH-died` | a fatal naming our process, or the process was gone afterwards |
| `notfound` | the app could not read the file at all |
| `encrypted` | the password dialog came up (expected for the encrypted fixtures) |
| `unsupported` | the app put up its "try opening it in another app" snackbar |
| `upload-offer` | the core declined it and the app offered to convert it online |
| `broken-file` | the app claimed the format and then failed on the file — "Couldn't open this file" |
| `still-rendering` | the screen was still changing when the shutter gave up — see below |
| `launch-*` | `am start` itself did not report `ok` |

Each of these matches one of the app's strings in full rather than a keyword, because a
keyword matches the *document* too: the app's own `about.odt` and changelog fixtures contain
the words "upload" and "password-protected", and an earlier version of this reported them as
failures they were not. Anything matching on document text will do the same.

**A blank render has no signal of its own** — that is the one thing the device will not tell
you. The run ends by printing the documents with the fewest text nodes and the smallest
screenshots, which is where blank and near-blank pages sort to. Start there, then look at the
rest.

Slow is not broken, and the shutter cannot tell them apart on its own. A 5 MB `.doc` was
still showing "Loading…" after 11 seconds and a 284 KB `.csv` was still a blank white page
after 6, and both render fine given a minute. So each document is shot repeatedly until two
frames agree in size to within 1%; if that never happens the row is `still-rendering` and its
screenshot says nothing about the document. Do not read a `still-rendering` blank as a bug.

Screenshots of the private corpus stay on your machine. Do not paste them into an issue
without checking what is in them.

## Why it works the way it does

Three of the mechanics look arbitrary and are not. The script says the same thing at each
site, in more detail:

- **Files go in through `run-as`, not `adb push`.** The app declares only `INTERNET`, so a
  file pushed to shared storage is unreadable to it — even under its own
  `/sdcard/Android/data/<pkg>`, which comes back `EACCES` when shell owns the file. Piping
  into internal storage via `run-as` is the route that works, and it is why this needs the
  debug build rather than the store one.
- **The intent carries no mime type.** That leaves the app's own detection (`Odr.mimetype`, run
  by `FileIdentifier`) in the path instead of taking a caller's word for the type, which is the
  half of the app worth exercising.
- **`uiautomator dump` runs twice per document.** A WebView only builds its accessibility
  tree once something asks for one, so the first dump after a load has no text in it and the
  second has the document.

And one about the signals: the crash check requires a fatal that *names our process*. The
first version of this matched `AndroidRuntime` anywhere in logcat and reported all 225
documents as crashes, because `uiautomator`'s own launcher logs that line on every iteration.

## What it does not do

It never taps. When a load fails the app offers to upload the document to the conversion
service, and that offer is a dialog with a positive button — photographing it sends nothing,
accepting it would send someone's test document to a third party. There is no code path here
that sends a tap, and adding one would change what running this means.

It also skips formats the app does not claim (`ttf`, `otf`, `svm`, `pages`, `wpd`, `sxw`, and
so on). Those reach the app only if a user picks one deliberately; checking that they are
declined gracefully is a different sweep from this one.
