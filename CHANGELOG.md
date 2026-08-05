# Changelog

User-facing changes since 4.6. Rendering and format support come from the
OpenDocument core engine the app is built on, so changes absorbed from it are
listed here too.

Entries go under `Unreleased` as the change lands, in the same pull request.
The heading is cut when the version is dispatched to the release workflow, not
when it is tagged: a version code can only be uploaded once, so from that point
no later commit can ever ship under that version.

The release run reads that section: it refuses a version without one, and makes
it the body of the GitHub release it drafts. It is still not the store copy -
what Play shows under "What's new" is written in the Play Console when the
release is promoted.

## Unreleased

## 4.13.0

- Saving is safer. A save that fails no longer leaves the original file damaged,
  and saving a document that got shorter no longer leaves the tail of the old
  file behind — which could make an .odt or .docx stop opening entirely.
- A file the app recognises but cannot open now says so directly, with a way to
  get in touch, instead of asking to upload it to the conversion service first.
  The upload offer stays for formats the app genuinely does not support, such as
  iWork, WordPerfect and DXF.
- Audio and video files handed to the app now play, and archives, plain text,
  images and fonts are shown by the engine.
- The review prompt now appears only after a document you opened yourself, or on
  the start screen, and never before the third use.

## 4.12.0

No user-facing changes. The engine is now taken from a prebuilt package, which
makes the app buildable from source without a native toolchain.

## 4.11.0

- Presentations open in the app: .pptx and .ppt, along with legacy .xls and .doc,
  are rendered by the built-in engine.
- The list of formats the app accepts now comes from the engine itself instead of
  a hand-maintained list. Templates, macro-enabled variants, flat XML flavours and
  extensions such as .dot, .pot, .pps and .xlt are recognised where they were not
  before.
- .xlsb is no longer offered, because it cannot be opened. It used to be accepted
  and then fail.
- The Edit action now appears based on what the engine says about the open
  document, rather than on its file type.
- Uploading a file the conversion service refuses now reports the failure instead
  of showing a browser error page.

## 4.10.0

- Engine update. Opening a document no longer fails when a network port is still
  held from an earlier run.

## 4.9.0

- Fixed a crash on startup that could stop the app from launching at all.
- Documents in the recently opened list can be opened again on a later launch.
  Their entries used to become unreadable as soon as the app was closed.
- Duplicates no longer pile up in the recently opened list.

## 4.8.0

No user-facing changes. Release and build process only.

## 4.7

A large engine update, replacing the two external rendering backends with the
app's own implementations.

**PDF**

- Rendered by the built-in engine. Text is placed by its baseline, embedded fonts
  (TrueType, CFF, Type1, Type3) are used where present and substituted where not,
  and non-embedded fonts fall back to standard metrics.
- Images, vector graphics, gradients, tiling patterns, clipping, transparency and
  blend modes are drawn.
- Password-protected PDFs can be opened, and damaged cross-reference tables are
  recovered rather than rejected.
- Links inside the document and to the web are clickable.
- CJK text in older documents displays correctly.
- Long documents are rendered page by page.

**Word, Excel and PowerPoint**

- The legacy .doc, .xls and .ppt formats are read by the built-in engine.
  Character formatting, cell fonts and fills, pictures, slide sizes and slide
  names are carried over.
- .pptx slide dimensions and tables, .xlsx merged cells and cell value types, and
  .docx merged table cells are handled.

**OpenDocument**

- Subscript and superscript, percentage line height and first-line indent are
  applied.

**App**

- PDFs and images open fitted to the screen width on phones.
- The app no longer offers itself for unrelated content such as contact cards. A
  switch on the start screen restores the previous behaviour.
- Pressing back in a document you opened from inside the app returns to the start
  screen instead of closing the app. Documents opened from another app still
  return to that app.
- Page tabs are shown only for spreadsheets, one per sheet. Every other format
  shows the whole document.
- The suggestion to upload the file no longer appears after a document has
  loaded successfully.
- Fixed a crash when uploading a file on Android 6 and 7.
- File type detection falls back to the file extension when the system reports an
  unknown type.

## 4.6

No user-facing changes. Build and engine maintenance.
