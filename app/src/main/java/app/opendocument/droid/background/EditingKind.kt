package app.opendocument.droid.background

/**
 * What the user can change in a document, as the core answers it for that document. The page has
 * one editor per kind, and each kind saves through its own core call - see `CoreLoader.writeEdits`.
 */
enum class EditingKind {
    /** Nothing: the core cannot write this document back. */
    NONE,

    /** A plain text file: its text, and nothing else. */
    TEXT,

    /** A text document or a presentation: its text, and in pro its formatting too. */
    DOCUMENT,

    /** A spreadsheet: one cell at a time. */
    SHEET,

    /** A pdf, which takes marks drawn over it rather than edits. Pro only. */
    ANNOTATION;

    val isEditable: Boolean
        get() = this != NONE
}
