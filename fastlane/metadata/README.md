# The play listing

What the two apps say on the Play Store, in fifteen locales. The release run
uploads it, so this is where the listing is written - not the console.

## The layout

```
fastlane/metadata/android/<locale>/     what both apps say
    title.txt                           - not here; a title belongs to an app
    short_description.txt               80 characters
    full_description.txt                4000 characters, holds ${ads}
    video.txt                           a promo video, de-DE only
    changelogs/<version code>.txt       500 characters, one per release
    images/                             en-US only, and not uploaded - see below
fastlane/metadata-pro/android/
    all/title.txt                       OpenDocument Reader Pro
fastlane/metadata-lite/android/
    all/title.txt                       OpenDocument Reader
    <locale>/ads.txt                    the sentences ${ads} stands for
```

`scripts/store-listing.py` reads it in three passes - the shared locale
directory, then the app's `all/`, then the app's own `<locale>/` - and the last
one to hold a file wins. So a title that is the same in every language is one
file, and a sentence that has to be translated is fifteen.

The fifteen locales are named in `LOCALES` there, with the language each is
written in, and the release checks the tree against that list: a directory gone
missing fails it rather than quietly shrinking it, and so does one beside them the
list does not name. A locale left with no title, short description or full
description in any of the three passes fails it too - supply uploads what it is
handed and leaves the rest of the console alone, so the missing one is not a blank
listing but the old one still standing. Adding or dropping a language is a line
changed in `LOCALES`.

## The two things the apps do not share

**The title.** Play has served `OpenDocument Reader Pro` in every storefront for
years, and lite `OpenDocument Reader`. Neither is translated: OpenDocument is the
format's own name, and one name is one app people can pass to each other. Nothing
is lost to search by it - play indexes the title, the short description and the
full description alike, so `LibreOffice` and the local words live in the short
description, where there is room for them.

Play refuses a title over 30 characters. The ones that used to be checked in here
were written when the limit was 50 and eleven of the fifteen were over it.

**Advertising.** Pro links no ad SDK and shows none, but every description said
ads were shown, in all fifteen languages. Rather than keep two descriptions per
locale and let them drift, the shared one holds `${ads}` and each app fills it in
from its own `ads.txt` - lite has fifteen, pro has none, and a fill-in nobody
answers leaves nothing behind, the space in front of it included.

`FILL_INS` in `scripts/store-listing.py` lists the names a `${...}` may have, so a
misspelt `${adds}` is an error rather than a sentence that quietly vanishes from
the store.

## Release notes

One file per locale per version code, `<locale>/changelogs/41500.txt`, which is
the name supply reads and what `app/build.gradle` derives from the version name.
They are written before the release by `scripts/store-copy.py`, from the
`CHANGELOG.md` section of that version, and the release run refuses to build a
version any locale is missing.

**Play allows 500 characters and refuses the release over it.** That is an eighth
of what the App Store allows, and every language here is longer than English -
measured over 4.15.0, French came back a quarter longer and German a fifth. So the
English is written under 400 rather than at 500; at 497, as 4.15.0 first was,
there is no translation of it that fits at all.

`CHANGELOG.md` at the root is the other record of the same release, written for
this repository rather than for the store.

## Screenshots and the feature graphic

Not here, and not committed anywhere: they are taken during the release run, from
the build going out, and staged into this tree beside the text - one directory to
supply, one edit to Play. `scripts/store_screenshots.py` puts the screenshots under
`<locale>/images/phoneScreenshots/`, `.../tenInchScreenshots/` and
`.../sevenInchScreenshots/`, and the feature graphic at
`<locale>/images/featureGraphic.png`, which is where supply reads a locale's
pictures from.

The tablet's pictures go into both tablet slots. Play falls back to the phone set
only where a slot is *empty*, and the 7" one was not - it held five pictures of the
pre-4.14 app, and went on showing them through every release that rewrote the rest.

The feature graphic is the one Play shows above the listing. It is drawn from the
first screenshot's capture, by `scripts/frame-screenshots.py`, and says what that
screenshot says in the same fifteen languages - so it cannot be a picture of an app
that no longer looks like that, which is exactly what the committed one had become.

The copy is written; a picture is taken. A picture of the app is worth what the
build it came off is worth, so it is not a file that sits in git going quietly out
of date. See the README's "Screenshots" section for how to take them by hand.

## What is not uploaded

`images/` holds an icon that predates the 4.14 redesign. Nothing stages it, and
what supply does not find in the staged tree it leaves alone, so it stays where it
is - the app's own launcher icon is a job of its own and a release is a poor moment
for it.

The feature graphic and the four phone screenshots that used to sit beside it are
gone: the release now draws and uploads its own, from the build it is shipping, so
a stale copy in the tree could only ever disagree with the store.
