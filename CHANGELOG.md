# Changelog

User-facing changes since 4.6. Rendering and format support come from the
OpenDocument core engine the app is built on, so changes absorbed from it are
listed here too.

Entries go under `Unreleased` as the change lands, in the same pull request. That
heading is cut to the version when the version is dispatched to the release
workflow, which refuses a version without a section and makes it the body of the
GitHub release.

It is not the store copy, but it is what the store copy is written from: Play's
"What's new" is this section said for the people using the app, one file per
version code per locale under `fastlane/metadata/android/<locale>/changelogs/`,
written by `scripts/store-copy.py` before the release and uploaded by it. Play
takes 500 characters, so not everything here reaches the store.

## Unreleased

- Charts in OpenDocument files are drawn from the chart itself - bar, line,
  area, scatter, pie and ring, with their titles, legends, axes and colours -
  rather than from the flat picture saved beside it.
- Shapes in OpenDocument drawings and presentations appear as the shapes they
  are, and where the file puts them. Arrows, stars, callouts, curves,
  connectors, ellipses and measures used to come out as plain rectangles or not
  at all, and a rotated or mirrored one sat square.
- Pictures and charts stored as Windows metafiles draw: their lines, curves,
  gradients, bitmaps and labels, each in the encoding the file names. Such an
  image used to be an empty frame, or a table's rules a row of floating numbers.
- A floating picture in a Word document floats where it was anchored, with the
  text wrapping around it, instead of sitting in the line.
- A Word table's own borders are drawn, and at the thickness the file asks for
  rather than four times it. A table cell's content starts at the top of the
  cell, as Word and OpenDocument show it.
- A manual page break starts a new page, on screen and in print.
- Lines in a PDF are drawn at the width the file sets. A PDF from Canva had
  every stroke at the same weight, whatever it was meant to be.
- Spreadsheets open in about half the memory, and two more of them open at all:
  a sheet whose repeated cells claimed billions of positions, and one with a
  merged range naming more cells than the sheet has.
- Pictures in a document load side by side rather than one after another: two
  parts of the same file can be read at once now.
- A password-protected document opens read-only. Saving it wrote the content
  back out without the protection its author asked for, and said nothing.

## 4.18.0

- A spreadsheet shows ten times as many rows: the cap rises from 10,000 to
  100,000, bounded by how wide the sheet is.
- Rich text (.rtf), Apple Pages, Numbers and Keynote files, and OpenDocument
  files saved as flat XML, all open.
- Markdown files open as prose - headings, emphasis, lists, tables and links -
  rather than as their own source.
- Markdown and CSV files no longer offer to open themselves in another app. That
  offer is for a file the app shows rather than reads, and both are read.
- PowerPoint presentations render in colour, and line breaks in them are kept.
- PDFs open faster, zoom faster, and their text can be selected and searched
  where it came out garbled before.
- A document saved by the app opens in LibreOffice again.
- A document that fails to open while the app is in the background no longer
  crashes it on the way back.
- Sharing a document, or opening it in another app, no longer freezes the app
  while its copy is written. A large document could freeze it long enough for
  Android to offer to close it.
- Handing the same document on twice no longer empties the copy.

## 4.17.0

- A PDF saved straight out of a browser opens. The web server's response was
  still sitting in front of the file, so it was read as plain text and shown as
  pages of its own source, or refused as an unsupported format.
- A password-protected Word, Excel or PowerPoint file says so, instead of
  failing to open for no stated reason. No password opens one yet, so the app no
  longer asks for one either.
- A table's repeating header row is no longer dropped, in documents and
  spreadsheets alike.
- Text copied out of a PDF reads as words rather than as letters spaced apart,
  where the file draws it one glyph at a time.
- Selecting or finding text in a PDF highlights the words themselves rather than
  a trail of narrow boxes beside them.
- More of a PDF's writing shows: text the file places with nothing in between
  was dropped and now appears.
- More PDFs open at all, including files whose writing nests brackets inside it.
- Pictures in a Word or Excel document appear, instead of a broken-image mark
  where each one should be.

## 4.16.0

- Dark mode reaches the document again, not just the screen around it.
- Night mode is the app's own now, switched over the open document: reading at
  night no longer means turning the whole phone dark first.
- Whether the document itself is darkened is a switch over it too, remembered
  for documents, PDFs and images apart, so a scanned page or a photograph can
  stay as it was written while text goes dark.
- The page borders are switched over the open document as well as from the
  landing screen, and the document comes back where you were reading it.
- A large text file appears at once. A megabyte took the better part of a minute
  to lay out and now takes under a second.
- A text file of prose is read as text, not mistaken for a spreadsheet because
  its sentences contain commas. Short comma-separated values still open as one.
- Text reflowed to the screen keeps a small margin from the edge instead of
  starting at the very first pixel.
- PDFs that do not carry their own fonts read properly: the words no longer
  drift further right along each line, and Find lands on the word you looked for
  rather than the one beside it. Bold and italic writing is drawn bold and
  italic, instead of at the plain font's widths.
- A PDF line that mixes fonts or sizes sits straight: subscripts and
  superscripts land where the file puts them, and the text after a bullet stays
  with the highlight that selects it.
- Selecting or finding across words in a PDF covers the spaces between them
  rather than leaving a sliver of white in every gap.
- A filled-in PDF form shows what was filled in, and a marked-up one shows its
  notes and highlights.
- A scanned PDF shows the scan rather than a blank page.
- Justified writing in a PDF is spaced the way the file asks instead of falling
  short of the margin, and a page written sideways is turned the right way up.
- More PDFs come out right: writing set in a cut-down font shows the characters
  it should, and files whose compressed parts end short open at all.
- Tapping the text of a paged document reaches it, so selecting and copying work
  where the page used to swallow the tap.
- A Word document is spaced the way Word spaces it: the gaps above and below
  paragraphs, the height of a line, lists that stay tight, and table rows that
  keep the height they were given.
- A Word table follows the style it was built with, so its writing looks the way
  the document sets it up.

## 4.15.0

- PDFs look much closer to the original: text sits at the right place and size,
  colours match, pages are trimmed the way a PDF viewer trims them, and more of
  their images come through. Some PDFs that opened blank now open properly.
- Text is shown in the font the document asks for. Slides whose writing was
  there but invisible now show it, and spreadsheet cells are drawn in their own
  font rather than the sheet's.
- Documents built on a template keep their layout: a letter's address and date
  land in their boxes, and the page keeps the margins its letterhead needs.
- Images sit where the document puts them, centred if the file centres them, and
  stay inside their frames instead of covering the page.
- Blank lines survive being copied out of a document.
- Large or unusually built documents open instead of crashing the app, and take
  less memory while they do. A single unreadable character no longer costs you
  the whole document.

## 4.14.0

- The app opens on the documents you had open recently, instead of a welcome
  screen there was nothing to do on.
- A new look throughout, following your phone's light or dark setting, with what
  you can do to an open document moved down within reach of your thumb.
- Leaving a document with unsaved changes now asks first, instead of dropping
  them.
- Saving asks only once where to put the file.
- A .csv now opens as a real spreadsheet: the separator is worked out from the
  file, quoted fields stay whole, and a value that only looks like a number is
  left as text. It used to be listed line by line.
- Spreadsheets are drawn in a quieter grid, under a row and column ruler that
  stays put while you scroll.
- A .docx now breaks onto the pages it was written for, and the markers of a
  numbered list come along when you copy text out of one.
- A document that is not saved in UTF-8 comes out as readable text instead of
  garbled characters.
- Opening an archive lists its entries as files instead of one page of gibberish.
- Embedded fonts in a PDF no longer come out as boxes or as the wrong letters.
- svg, ico, jxl, jp2, psd, wmf and emf are recognised and shown as images.
- An .xml file opens properly laid out instead of as one long line.
- A file the app cannot open is no longer offered for upload to our conversion
  service, so no document leaves your device. This affected .rtf and WordPerfect.
- The paid app no longer carries Google's advertising code at all, and there is
  now a build with nothing proprietary in it for F-Droid.
- Turning down the consent form in the free app leaves a limited ad, which
  carries no identifiers, rather than an empty space.
- Smaller fixes to plain text, to the margin documents open with, and to the
  promotion in the free app, which varies again from one launch to the next.

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
