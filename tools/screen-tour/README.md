# screen-tour

Walks a build through the handful of screens that carry its design, photographs each one,
and lays two builds' photographs out side by side as a PDF.

This is what produced `main-vs-redesign.pdf`. It exists because the comparison was worth
having twice: the first round of it was a pile of `adb shell input tap 1148 2652` typed by
hand, and every follow-up question - *does the logo stay?*, *is the shadow clipped?* - meant
typing all of it again, against a screen whose buttons had moved.

Like [render-sweep](../render-sweep), it is a **looking** tool. Nothing asserts, nothing
fails a build. It hands you pictures and you decide what you think of them.

## Running it

You need a device or emulator on `adb`, and Pillow for the PDF (`pip install pillow`).

```sh
# the branch you are on
./gradlew assembleProDebug
tools/screen-tour/screen-tour.py --install app/build/outputs/apk/pro/debug/app-pro-debug.apk \
    --out build/screen-tour/redesign

# what you are comparing it against, from a worktree of the other branch
git worktree add ../odr-main main
(cd ../odr-main && ./gradlew assembleProDebug)
tools/screen-tour/screen-tour.py \
    --install ../odr-main/app/build/outputs/apk/pro/debug/app-pro-debug.apk \
    --out build/screen-tour/main

# and the lite flavour, if the page about ads is wanted - a different application id,
# so it sits on the device next to the other one
./gradlew assembleLiteDebug
tools/screen-tour/screen-tour.py --package at.tomtasche.reader \
    --install app/build/outputs/apk/lite/debug/app-lite-debug.apk \
    --out build/screen-tour/lite

tools/screen-tour/collage.py --out build/screen-tour/main-vs-redesign.pdf \
    --set main=build/screen-tour/main \
    --set redesign=build/screen-tour/redesign \
    --set lite=build/screen-tour/lite
```

A tour takes about two minutes. Put the emulator somewhere you can see it: watching it drive
itself is how you notice a step that landed somewhere unintended.

## The screens

Six, and the names are what the collage looks for:

| shot | what it is |
| --- | --- |
| `01-first-launch.png` | a fresh install, nothing opened yet |
| `02-open.png` | whatever the Open action puts on screen first |
| `03-document.png` | `test.odt` rendered, no chrome touched |
| `04-menu.png` | every action the document offers, unfolded |
| `05-search.png` | the find bar, with a term typed and entered |
| `06-recents.png` | where the app keeps what has been opened |

Three documents are opened, in the order `--document` gives them, so the last is the one on
screen for shots 3 to 5 and the recents list has something to sort. They come from
`app/src/androidTest/assets`, which is small, in the repo, and covers three formats.

## What the story file is for

`collage.py` draws no prose of its own. Page titles and the caption under each screenshot
live in `story.json`, which is the file to edit when the design changes - and the file to
copy when the comparison is between two other things entirely (`--story mine.json`).

A page names a `shot` and one or two `columns`, each naming a `--set`. Two columns is the
side-by-side layout; one column puts the screenshot on the left and gives the caption the
rest of the page, which is what the last page of the ads comparison uses.

## Why it works the way it does

**Documents go in through `adb push` to shared storage.** The opposite of what render-sweep
does, deliberately: that tool launches an intent at the app directly, and the app - which
declares no storage permission - cannot read a pushed file. Here every document is opened
through the system picker, and the picker is what grants the app the uri. Photographing a
document the app reached a way no user can would defeat the point.

**Nothing is seeded into the app's own storage.** Writing three entries into
`recent_documents.json` would be quicker than opening three documents through a picker, and
would produce a screenshot of a list the app then throws away: the uris carry no grant, and
`LandingViewModel.reload()` drops what it cannot resolve.

**Each step looks for several things.** The two designs put their actions in different
places - a toolbar and a floating button - so `MORE_BUTTON`, `OPEN_BUTTON` and the rest are
lists, tried in order, first one on screen wins. That is what lets one tour walk both
branches. When a branch grows a screen these do not cover, add to the list rather than
forking the tour.

**Steps wait for what they need instead of sleeping a fixed time.** The exception that
proves it: the lite flavour's consent sheet arrives whenever the ad sdk finishes starting,
sometimes after the landing screen is already drawn, and the first version of this shot the
app with a consent dialog over it.

**The recents shot taps a dead corner first.** A row keeps the pressed highlight of the tap
that opened it, and it photographs as a selected row that nothing selected.

## The PDF is a raster, at 300 dpi

Every page is an image - there are no text objects in the output, so nothing in it is
selectable or searchable, and zooming far enough in will always find pixels. It was 150 dpi
to begin with and looked it, which is the whole reason the resolution is a flag.

`--dpi 300` is the default and is about 4 MB for eight pages. `--dpi 150` is a quarter of
that and fine on a screen at a glance; `--dpi 600` is for printing, and slow. The layout is
written in points and scaled, so the pages look the same at any of them.

Making it vector would mean a PDF library this repo does not otherwise need. Pillow is
already the only dependency, and 300 dpi was cheaper than the argument.
