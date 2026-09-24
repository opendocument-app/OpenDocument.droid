# The Play listing

What the two apps say on the Play Store, in fifteen locales. The release run uploads it and
overwrites the console, so this tree is the source.

## Layout

```
fastlane/metadata/android/<locale>/     shared by both apps
    short_description.txt               80 characters
    full_description.txt                4000 characters, holds ${ads} and ${editing}
    video.txt                           a promo video
    changelogs/<version code>.txt       500 characters, one per release
    images/icon.png                     en-US only, not uploaded
fastlane/metadata-pro/android/
    all/title.txt                       OpenDocument Reader Pro
    <locale>/editing.txt                what pro edits that lite does not
fastlane/metadata-lite/android/
    all/title.txt                       OpenDocument Reader - view ODT
    <locale>/ads.txt                    the sentences ${ads} stands for
    <locale>/editing.txt                that the same edits come with pro
```

`scripts/store-listing.py` reads three passes per app: the shared locale directory, the
app's `all/`, then the app's own `<locale>/`. The last pass that holds a file wins. `LOCALES`
there names the fifteen locales and their languages. The release fails on a locale directory
missing or unlisted, and on a locale with no title, short or full description.

## What the apps do not share

- **Title.** Not translated. Play refuses a title over 30 characters, and lite's is exactly
  30, so `... Pro - view ODT` does not fit. Search terms such as `LibreOffice` go in the
  short description, which Play indexes too.
- **Ads.** The shared description holds `${ads}`. Lite fills it from `ads.txt`; pro has no
  file, and an unfilled placeholder leaves nothing behind, not even its leading space.
- **Editing.** `${editing}` says what pro adds. Lite's file names it as pro's; pro's names it
  as its own.

`FILL_INS` in `scripts/store-listing.py` lists the allowed placeholder names, so a misspelt
`${adds}` is an error.

## Release notes

One file per locale per version code, `<locale>/changelogs/41500.txt`. `scripts/store-copy.py`
writes them from the version's `CHANGELOG.md` section, and the release refuses a version any
locale lacks.

- Play allows 500 characters. Translations run up to a quarter longer than English, so keep
  the English under 400.
- The notes are the same in both apps. Say what changed, not who gets it: no "free", no
  "pro".

## Screenshots and the feature graphic

Not committed. The release run takes them from the build it ships, and
`scripts/store_screenshots.py` stages them under `<locale>/images/phoneScreenshots/`,
`tenInchScreenshots/`, `sevenInchScreenshots/` and `featureGraphic.png`. The tablet set fills
both tablet slots, because Play falls back to the phone set only where a slot is empty. See
the README's Screenshots section to take them by hand.

`images/icon.png` predates the 4.14 redesign. Nothing stages it, and supply leaves alone what
it is not handed, so the store keeps its current icon.
