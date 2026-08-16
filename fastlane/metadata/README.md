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

## What is not uploaded

`images/` holds an icon, a feature graphic and four phone screenshots that predate
the 4.14 redesign, so uploading them would put the old screenshots back over the
current ones. The upload leaves them alone. Graphics are their own job.
