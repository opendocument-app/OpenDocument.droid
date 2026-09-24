# render-sweep

Opens every document of a corpus on a connected device, one at a time, and screenshots what
the app made of it. It is a looking tool, not a test: nothing asserts and nothing fails a
build. You get a table of signals and a screenshot per document, and you decide what is
broken.

## Running it

You need a device or emulator on `adb`, the pro debug build, and a checkout of
`OpenDocument.core` with its test submodules initialised.

```sh
./gradlew assembleProDebug
tools/render-sweep/render-sweep.sh --install        # pushes the apk; drop --install later
tools/render-sweep/render-sweep.sh --corpus ~/corpora/docs
tools/render-sweep/render-sweep.sh --filter '\.ods$'
tools/render-sweep/render-sweep.sh --filter 'odr-public' --limit 20
```

The default corpus is `../OpenDocument.core/test/data/input`, and results land in
`build/render-sweep`. A full run over about 225 documents takes about 80 minutes with the
screen on, so put the device on a charger.

## Output

```
build/render-sweep/
  results.tsv     one row per document: launch status, png size, text nodes, signal
  shots/          full resolution screenshot per document
  small/          the same, downscaled
  ui/             uiautomator dump per document: the text the WebView showed
  logs/           crash buffer and error lines per document
```

The `signal` column is a triage hint:

| signal | meaning |
| --- | --- |
| `ok` | launched, survived, nothing obviously wrong |
| `CRASH` / `CRASH-died` | a fatal naming our process, or the process was gone afterwards |
| `notfound` | the app could not read the file |
| `encrypted` | the password dialog came up |
| `unsupported` | the app showed its "try opening it in another app" bar |
| `broken-file` | the app claimed the format and then failed on the file |
| `still-rendering` | the screen was still changing when the shutter gave up |
| `launch-*` | `am start` itself did not report `ok` |

Each signal matches one of the app's strings in full, because a keyword also matches the
document's own text. A blank render has no signal: the run ends by listing the documents
with the fewest text nodes and the smallest screenshots, which is where to start.

Slow is not broken. Each document is shot until two frames agree in size within 1%; if that
never happens the row is `still-rendering` and its screenshot says nothing.

Screenshots of a private corpus stay on your machine.

## Mechanics

- **Files go in through `run-as`, not `adb push`.** The app has no storage permission, so a
  pushed file is unreadable to it. This is why the debug build is needed.
- **The intent carries no mime type**, so the app's own detection (`Odr.mimetype` in
  `FileIdentifier`) is exercised.
- **`uiautomator dump` runs twice per document**, because a WebView builds its accessibility
  tree only once something asks for it.
- **The crash check needs a fatal that names our process.** `uiautomator`'s own launcher
  logs `AndroidRuntime` on every iteration.
- **It never taps.** A dialog is photographed, not answered.
- **It skips extensions outside the script's `CLAIMED` list**, which is kept by hand because
  the real table lives in `libodr_jni` and needs a device to read.
