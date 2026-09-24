# screen-tour

Walks a build through six screens, photographs each, and lays two builds' photographs side
by side as a PDF. Like [render-sweep](../render-sweep) it is a looking tool: nothing asserts.

## Running it

You need a device or emulator on `adb`, and Pillow (`pip install pillow`). `collage.py` also
needs a TrueType font; macOS has one, on Debian `apt install fonts-dejavu-core`.

```sh
# the branch you are on
./gradlew assembleProDebug
tools/screen-tour/screen-tour.py --install app/build/outputs/apk/pro/debug/app-pro-debug.apk \
    --out build/screen-tour/redesign

# the branch you compare against, from a worktree
git worktree add ../odr-main main
(cd ../odr-main && ./gradlew assembleProDebug)
tools/screen-tour/screen-tour.py \
    --install ../odr-main/app/build/outputs/apk/pro/debug/app-pro-debug.apk \
    --out build/screen-tour/main

# the lite flavour, a different application id, sits beside the other on the device
./gradlew assembleLiteDebug
tools/screen-tour/screen-tour.py --package at.tomtasche.reader \
    --install app/build/outputs/apk/lite/debug/app-lite-debug.apk \
    --out build/screen-tour/lite

tools/screen-tour/collage.py --out build/screen-tour/main-vs-redesign.pdf \
    --set main=build/screen-tour/main \
    --set redesign=build/screen-tour/redesign \
    --set lite=build/screen-tour/lite
```

A tour takes about two minutes. Keep the emulator visible, so you notice a step that lands
somewhere unintended.

## The screens

| shot | what it is |
| --- | --- |
| `01-first-launch.png` | a fresh install, nothing opened yet |
| `02-open.png` | what the Open action shows first |
| `03-document.png` | `test.odt` rendered |
| `04-menu.png` | every action the document offers, unfolded |
| `05-search.png` | the find bar, with a term entered |
| `06-recents.png` | the recent documents list |

Three documents from `app/src/androidTest/assets` are opened, in the order `--document` gives
them. The last one is on screen for shots 3 to 5.

`story.json` holds the page titles and captions of the collage. Edit it when the design
changes, or copy it for another comparison (`--story mine.json`). A page names a `shot` and
one or two `columns`, each naming a `--set`.

## Mechanics

- **Documents go in through `adb push` and the system picker**, which grants the app the uri.
  The app has no storage permission, and a screenshot of a document it reached another way
  proves nothing. Nothing is seeded into `recent_documents.json` for the same reason.
- **Each step looks for several things.** `OPEN_BUTTON`, `MORE_BUTTON` and the rest are lists
  tried in order, so one tour walks more than one design. Add to the list rather than fork.
- **Steps wait for what they need** instead of sleeping. The lite consent sheet arrives
  whenever the ad sdk finishes starting, sometimes over a drawn landing screen.
- **The recents shot taps a dead corner first**, or the row keeps its pressed highlight.
- **The PDF is a raster.** `--dpi 300` is the default, about 4 MB for eight pages; `--dpi 150`
  is fine on a screen, `--dpi 600` for print. Layout is in points, so pages look the same at
  any of them. Vector output would need a PDF library the repo does not otherwise have.
