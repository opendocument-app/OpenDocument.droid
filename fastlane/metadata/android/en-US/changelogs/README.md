# Release notes

One file per version code, holding the "What's new" text for that release.
Written for users of the app, not for this repository: the developer-facing
record of the same release is in `CHANGELOG.md` at the root.

Named by version code (`41400.txt` for 4.14.0), which is what Play keys a
release on, and what `app/build.gradle` derives from the version name.

**Play allows 500 characters, and refuses the release with "Release note for
en-US is too long" until it fits.** The box is a single field per language and
the count includes the blank lines between paragraphs.

`supply` does not read this directory - the upload sets
`skip_upload_changelogs: true`, because promoting a release is a deliberate step
in the Play Console rather than something an upload does. The text here is
pasted into the console at promotion, per language, inside the `<en-US>` tags
the console's box comes prefilled with.

Only en-US is kept here, and 4.14.0 was promoted with only the `<en-US>` block
filled in; Play shows the release notes it has and falls back to the listing's
own language elsewhere. Translations are outstanding.
