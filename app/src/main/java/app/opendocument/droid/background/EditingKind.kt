package app.opendocument.droid.background

/** What the user can change in a document, as the core answers it. Each kind saves its own way. */
enum class EditingKind {
    /** Nothing: the core cannot write this document back. */
    NONE,

    /** A plain text file: its text, and nothing else. */
    TEXT,

    /** A text document or a presentation. */
    DOCUMENT,

    /** A spreadsheet or a csv: its cells, rows and columns. */
    SHEET,

    /** A pdf, which takes marks rather than edits. */
    ANNOTATION;

    val isEditable: Boolean
        get() = this != NONE
}
