#!/usr/bin/env python3
"""Builds the documents the play store screenshots are taken of.

A reader's screenshots are mostly the document it is reading, so these are
written rather than borrowed: a short report, a small spreadsheet and a three
slide deck, in every language the app speaks and the store has a listing for.
The screenshot of the German store shows a German document.

Kept small on purpose - a few kilobytes each, no images, no third party
material - because they are read once, on an emulator, to be photographed.

They are written into the *test* apk's assets, not the app's, and laid out on
the device by `ScreenshotTests`. An instrumented test runs in the app's own
process, so nothing about the screenshots has to exist in a build that ships:
no back door, no debug-only asset, no line in `MainActivity`.

    python3 scripts/make-screenshot-documents.py
    python3 scripts/make-screenshot-documents.py --language en    one of them

What it writes is not committed - the screenshot lane runs this before it
builds. The packages are byte for byte reproducible, so a rerun that changes
no wording writes the same bytes.
"""

import argparse
import json
import re
import unicodedata
import zipfile
from pathlib import Path
from xml.sax.saxutils import escape

import store_screenshots as store

# 1980-01-01, what zip stores when it is given nothing: a rerun with the same
# words has to produce the same bytes, or every run is a commit
EPOCH = (1980, 1, 1, 0, 0, 0)

SAMPLES = (
    Path(__file__).resolve().parent.parent / "app" / "src" / "androidTest" / "assets" / "screenshots"
)

NAMESPACES = " ".join(
    [
        'xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"',
        'xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0"',
        'xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0"',
        'xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0"',
        'xmlns:draw="urn:oasis:names:tc:opendocument:xmlns:drawing:1.0"',
        'xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0"',
        'xmlns:svg="urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0"',
    ]
)

MANIFEST = """<?xml version="1.0" encoding="UTF-8"?>
<manifest:manifest xmlns:manifest="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0" manifest:version="1.2">
 <manifest:file-entry manifest:full-path="/" manifest:media-type="{mimetype}"/>
 <manifest:file-entry manifest:full-path="content.xml" manifest:media-type="text/xml"/>
 <manifest:file-entry manifest:full-path="styles.xml" manifest:media-type="text/xml"/>
</manifest:manifest>
"""

# A4 upright for the report and the sheet, 16:9 for the deck.
#
# A page is fitted to the width of the screen, so a page with little on it reads
# as a smudge in the top third of an empty sheet. The answer is words rather
# than smaller paper: these documents are written long enough to fill A4.
PAGE_LAYOUTS = {
    "document": '<style:page-layout-properties fo:page-width="21cm" fo:page-height="29.7cm"'
    ' fo:margin-top="2cm" fo:margin-bottom="2cm" fo:margin-left="2cm" fo:margin-right="2cm"/>',
    "slide": '<style:page-layout-properties fo:page-width="28cm" fo:page-height="15.75cm"/>',
}

# One accent, used for the report's headings and the slide titles, so the three
# documents read as one set. Blue, because the app's own tint is.
ACCENT = "#1c6fd6"
RULE = "#d4d9e0"


def styles(kind: str) -> str:
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<office:document-styles {NAMESPACES} office:version="1.2">
 <office:styles/>
 <office:automatic-styles>
  <style:page-layout style:name="PM1">
   {PAGE_LAYOUTS[kind]}
  </style:page-layout>
 </office:automatic-styles>
 <office:master-styles>
  <style:master-page style:name="Default" style:page-layout-name="PM1"/>
 </office:master-styles>
</office:document-styles>
"""


def content(body: str, automatic: str = "") -> str:
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<office:document-content {NAMESPACES} office:version="1.2">
 <office:automatic-styles>
{automatic}
 </office:automatic-styles>
 <office:body>
{body}
 </office:body>
</office:document-content>
"""


def paragraph_style(name: str, *, size: str, weight: str = "normal", colour: str = "#1a1a1a", space: str = "0.4cm") -> str:
    return f"""  <style:style style:name="{name}" style:family="paragraph">
   <style:paragraph-properties fo:margin-bottom="{space}"/>
   <style:text-properties fo:font-size="{size}" fo:font-weight="{weight}" fo:color="{colour}"/>
  </style:style>"""


def report(words: dict) -> str:
    """A page of text: a title, a lead, two headed sections and a closing line."""
    automatic = "\n".join(
        [
            paragraph_style("Title", size="26pt", weight="bold", space="0.8cm"),
            paragraph_style("Heading", size="16pt", weight="bold", colour=ACCENT, space="0.3cm"),
            paragraph_style("Body", size="12pt", space="0.5cm"),
        ]
    )

    lines = [
        f'   <text:p text:style-name="Title">{escape(words["title"])}</text:p>',
        f'   <text:p text:style-name="Body">{escape(words["lead"])}</text:p>',
    ]
    for heading, paragraphs in words["sections"]:
        lines.append(
            f'   <text:h text:style-name="Heading" text:outline-level="1">{escape(heading)}</text:h>'
        )
        lines += [f'   <text:p text:style-name="Body">{escape(text)}</text:p>' for text in paragraphs]
    lines.append(f'   <text:p text:style-name="Body">{escape(words["closing"])}</text:p>')

    return content("  <office:text>\n" + "\n".join(lines) + "\n  </office:text>", automatic)


def table(words: dict, columns: int = 4, rows: int = 0, scale: int = 1) -> tuple:
    """The figures as rows of cells: a header, the items across as many periods
    as are asked for with their totals, and a totals row under them.

    Narrowed and shortened for the files that are not the budget, so the .xlsx
    and the invoice hold their own figures rather than the .ods twice.

    As wide as the header a language has words for, so one translated ahead of
    the others comes out short rather than out of step.
    """
    periods = words["periods"][:columns]
    columns = len(periods)
    taken = FIGURES[:rows] if rows else FIGURES
    names = words["rows"][: len(taken)]

    head = [words["item"]] + periods + [words["total"]]
    body = [
        [name] + [value * scale for value in figures[:columns]] + [sum(figures[:columns]) * scale]
        for name, figures in zip(names, taken)
    ]
    foot = (
        [words["total"]]
        + [sum(line[column + 1] for line in body) for column in range(len(periods))]
        + [sum(line[-1] for line in body)]
    )

    return head, body, foot


def sheet(words: dict) -> str:
    """Two sheets, so the tab bar under the document has something to show."""
    automatic = "\n".join(
        [
            # two widths: eight at the label's width put half the sheet off
            # the right edge, and a figure needs less room than its row's name
            """  <style:style style:name="coLabel" style:family="table-column">
   <style:table-column-properties style:column-width="3.2cm"/>
  </style:style>""",
            """  <style:style style:name="coFigure" style:family="table-column">
   <style:table-column-properties style:column-width="2.2cm"/>
  </style:style>""",
            """  <style:style style:name="ceHead" style:family="table-cell">
   <style:table-cell-properties fo:background-color="#eef3fa" fo:border-bottom="0.06pt solid %s"/>
   <style:text-properties fo:font-weight="bold" fo:color="%s"/>
  </style:style>""" % (RULE, ACCENT),
            """  <style:style style:name="ceTotal" style:family="table-cell">
   <style:table-cell-properties fo:border-top="0.06pt solid %s"/>
   <style:text-properties fo:font-weight="bold"/>
  </style:style>""" % (RULE,),
        ]
    )

    def row(cells: list, style: str | None = None) -> str:
        marked = f' table:style-name="{style}"' if style else ""
        out = ["    <table:table-row>"]
        for cell in cells:
            if isinstance(cell, int):
                out.append(
                    f'     <table:table-cell{marked} office:value-type="float" office:value="{cell}">'
                    f"<text:p>{cell}</text:p></table:table-cell>"
                )
            else:
                out.append(
                    f'     <table:table-cell{marked} office:value-type="string">'
                    f"<text:p>{escape(cell)}</text:p></table:table-cell>"
                )
        out.append("    </table:table-row>")

        return "\n".join(out)

    head, body, foot = table(words, columns=6)

    overview = [row(head, "ceHead")] + [row(line) for line in body] + [row(foot, "ceTotal")]
    costs = [row([words["item"], words["total"]], "ceHead")]
    costs += [row([line[0], line[-1]]) for line in body]

    tables = []
    for name, rows, columns in (
        (words["sheets"][0], overview, len(head)),
        (words["sheets"][1], costs, 2),
    ):
        # the label column, then a figure column for each of the rest
        marks = "\n".join(
            ['    <table:table-column table:style-name="coLabel"/>']
            + ['    <table:table-column table:style-name="coFigure"/>'] * (columns - 1)
        )
        tables.append(
            f'   <table:table table:name="{escape(name)}">\n{marks}\n'
            + "\n".join(rows)
            + "\n   </table:table>"
        )

    return content("  <office:spreadsheet>\n" + "\n".join(tables) + "\n  </office:spreadsheet>", automatic)


def deck(words: dict) -> str:
    """Three slides, each with a title and its bullets."""
    automatic = "\n".join(
        [
            paragraph_style("SlideTitle", size="32pt", weight="bold", colour=ACCENT, space="0.6cm"),
            paragraph_style("Bullet", size="18pt", space="0.35cm"),
        ]
    )

    pages = []
    for title, bullets in words["slides"]:
        lines = [f'     <text:p text:style-name="SlideTitle">{escape(title)}</text:p>']
        lines += [
            f'     <text:p text:style-name="Bullet">• {escape(point)}</text:p>' for point in bullets
        ]
        pages.append(
            f'   <draw:page draw:name="{escape(title)}" draw:master-page-name="Default">\n'
            '    <draw:frame svg:width="24cm" svg:height="12cm" svg:x="2cm" svg:y="2cm">\n'
            "     <draw:text-box>\n" + "\n".join(lines) + "\n     </draw:text-box>\n"
            "    </draw:frame>\n   </draw:page>"
        )

    return content("  <office:presentation>\n" + "\n".join(pages) + "\n  </office:presentation>", automatic)


# Short and plain on purpose: this is a document over someone's shoulder in a
# store screenshot, not copy that has to sell anything.
WORDS = {
    "en": {
        "title": "Quarterly report",
        "lead": "The team met every goal of the second quarter, and the new release went out on time.",
        "sections": [
            ["Highlights", [
                "Costs stayed below budget, and two new partners joined the project.",
                "The new release reached more people in its first week than the last one did in a month.",
                "Support answered nine of ten questions the same day.",]],
            ["Costs and budget", [
                "Spending on software rose with the new licences, while travel fell again.",
                "Hardware was replaced once, and support stayed steady through the quarter.",
                "Two servers moved to the new provider without a day of downtime.",]],
            ["Next quarter", [
                "The release in September is the last one planned this year.",
                "Two positions open in support, and one in design.",
                "The office moves in November, and the budget for it is agreed.",
            ]],
            ["The people", [
                "Six people worked on the release, two of them new this year.",
                "Holiday cover was arranged in April and held through the summer.",
                "Everyone has taken the training the new licence requires.",
            ]],
            ["Risks", [
                "The move in November is the one date nothing else can slip past.",
                "One supplier has not signed the new terms, and is being chased.",
                "Hosting costs rise in January unless the contract is renewed early.",
            ]],
        ],
        "closing": "The next meeting is at the end of July.",
        "sheets": ["Overview", "Costs"],
        "item": "Item",
        "total": "Total",
        "periods": ["Jan", "Feb", "Mar", "Apr", "May", "Jun"],
        "rows": ["Software", "Travel", "Hardware", "Marketing", "Support", "Training", "Licences", "Hosting", "Events", "Office", "Cloud", "Recruiting", "Legal", "Insurance", "Catering", "Shipping", "Advertising", "Consulting", "Maintenance", "Utilities", "Equipment", "Subscriptions", "Telephony", "Internet", "Security", "Backups", "Domains", "Certificates", "Printing", "Stationery", "Postage", "Cleaning", "Repairs", "Furniture", "Storage", "Bank fees", "Memberships", "Conferences", "Translation", "Design"],
        "slides": [
            ["Project plan", ["Goals for the quarter", "Budget and costs", "Next steps"]],
            ["Schedule", ["Release in June", "Review in July", "Planning in August"]],
            ["Team", ["Two new partners", "Support in three languages", "Training in autumn"]],
        ],
    },
    "de": {
        "title": "Quartalsbericht",
        "lead": "Das Team hat alle Ziele des zweiten Quartals erreicht, und die neue Version ist pünktlich erschienen.",
        "sections": [
            ["Das Wichtigste", [
                "Die Kosten blieben unter dem Budget, und zwei neue Partner sind zum Projekt gestoßen.",
                "Die neue Version erreichte in der ersten Woche mehr Menschen als die letzte in einem Monat.",
                "Der Support beantwortete neun von zehn Anfragen noch am selben Tag.",]],
            ["Kosten und Budget", [
                "Die Ausgaben für Software stiegen mit den neuen Lizenzen, die Reisekosten sanken erneut.",
                "Die Hardware wurde einmal ersetzt, der Support blieb das ganze Quartal über stabil.",
                "Zwei Server sind ohne einen Tag Ausfall zum neuen Anbieter umgezogen.",]],
            ["Nächstes Quartal", [
                "Die Version im September ist die letzte für dieses Jahr.",
                "Zwei Stellen im Support sind offen, eine im Design.",
                "Der Umzug ins neue Büro ist für November geplant und budgetiert.",
            ]],
            ["Das Team", [
                "An der Version arbeiteten sechs Personen, zwei davon neu in diesem Jahr.",
                "Die Urlaubsvertretung wurde im April geregelt und hat den ganzen Sommer über gehalten.",
                "Alle haben die Schulung absolviert, die die neue Lizenz verlangt.",
            ]],
            ["Risiken", [
                "Der Umzug im November ist der einzige Termin, der sich nicht verschieben lässt.",
                "Ein Lieferant hat die neuen Bedingungen noch nicht unterschrieben; wir haken nach.",
                "Die Hostingkosten steigen im Januar, wenn der Vertrag nicht vorzeitig verlängert wird.",
            ]],
        ],
        "closing": "Das nächste Treffen findet Ende Juli statt.",
        "sheets": ["Übersicht", "Kosten"],
        "item": "Position",
        "total": "Gesamt",
        "periods": ["Jan", "Feb", "Mär", "Apr", "Mai", "Jun"],
        "rows": ["Software", "Reisen", "Hardware", "Marketing", "Support", "Schulung", "Lizenzen", "Hosting", "Veranstaltungen", "Büro", "Cloud", "Personalsuche", "Recht", "Versicherung", "Verpflegung", "Versand", "Werbung", "Beratung", "Wartung", "Nebenkosten", "Ausstattung", "Abonnements", "Telefonie", "Internet", "Sicherheit", "Backups", "Domains", "Zertifikate", "Druck", "Büromaterial", "Porto", "Reinigung", "Reparaturen", "Möbel", "Lager", "Bankgebühren", "Mitgliedschaften", "Konferenzen", "Übersetzung", "Design"],
        "slides": [
            ["Projektplan", ["Ziele für das Quartal", "Budget und Kosten", "Nächste Schritte"]],
            ["Zeitplan", ["Version im Juni", "Rückblick im Juli", "Planung im August"]],
            ["Team", ["Zwei neue Partner", "Support in drei Sprachen", "Schulung im Herbst"]],
        ],
    },
    "es": {
        "title": "Informe trimestral",
        "lead": "El equipo cumplió todos los objetivos del segundo trimestre y la nueva versión salió a tiempo.",
        "sections": [
            ["Lo más destacado", [
                "Los costes se mantuvieron por debajo del presupuesto y dos nuevos socios se unieron al proyecto.",
                "La nueva versión llegó a más gente en su primera semana que la anterior en un mes.",
                "El soporte respondió nueve de cada diez consultas el mismo día.",]],
            ["Costes y presupuesto", [
                "El gasto en software subió con las nuevas licencias, mientras que los viajes volvieron a bajar.",
                "El hardware se sustituyó una vez y el soporte se mantuvo estable durante el trimestre.",
                "Dos servidores pasaron al nuevo proveedor sin una sola interrupción del servicio.",]],
            ["Próximo trimestre", [
                "La versión de septiembre es la última prevista este año.",
                "Hay dos vacantes en soporte y una en diseño.",
                "La mudanza de oficina es en noviembre y ya tiene presupuesto.",
            ]],
            ["Las personas", [
                "En la versión trabajaron seis personas, dos de ellas nuevas este año.",
                "La cobertura de vacaciones se organizó en abril y aguantó todo el verano.",
                "Todos han hecho la formación que exige la nueva licencia.",
            ]],
            ["Riesgos", [
                "La mudanza de noviembre es la única fecha que no puede moverse.",
                "Un proveedor aún no ha firmado las nuevas condiciones y se le está reclamando.",
                "El alojamiento sube en enero si no se renueva antes el contrato.",
            ]],
        ],
        "closing": "La próxima reunión es a finales de julio.",
        "sheets": ["Resumen", "Costes"],
        "item": "Concepto",
        "total": "Total",
        "periods": ["Ene", "Feb", "Mar", "Abr", "May", "Jun"],
        "rows": ["Software", "Viajes", "Hardware", "Marketing", "Soporte", "Formación", "Licencias", "Alojamiento", "Eventos", "Oficina", "Nube", "Contratación", "Legal", "Seguros", "Catering", "Envíos", "Publicidad", "Consultoría", "Mantenimiento", "Suministros", "Equipamiento", "Suscripciones", "Telefonía", "Internet", "Seguridad", "Copias de seguridad", "Dominios", "Certificados", "Impresión", "Papelería", "Franqueo", "Limpieza", "Reparaciones", "Mobiliario", "Almacenamiento", "Comisiones bancarias", "Cuotas", "Congresos", "Traducción", "Diseño"],
        "slides": [
            ["Plan del proyecto", ["Objetivos del trimestre", "Presupuesto y costes", "Próximos pasos"]],
            ["Calendario", ["Versión en junio", "Revisión en julio", "Planificación en agosto"]],
            ["Equipo", ["Dos nuevos socios", "Soporte en tres idiomas", "Formación en otoño"]],
        ],
    },
    "fr": {
        "title": "Rapport trimestriel",
        "lead": "L'équipe a atteint tous les objectifs du deuxième trimestre et la nouvelle version est sortie à temps.",
        "sections": [
            ["Points forts", [
                "Les coûts sont restés dans le budget et deux nouveaux partenaires ont rejoint le projet.",
                "La nouvelle version a touché plus de monde en une semaine que la précédente en un mois.",
                "Le support a répondu à neuf demandes sur dix le jour même.",]],
            ["Coûts et budget", [
                "Les dépenses en logiciels ont augmenté avec les nouvelles licences, tandis que les déplacements ont encore baissé.",
                "Le matériel a été remplacé une fois et le support est resté stable sur le trimestre.",
                "Deux serveurs ont migré vers le nouveau prestataire sans la moindre interruption de service.",]],
            ["Trimestre prochain", [
                "La version de septembre est la dernière prévue cette année.",
                "Deux postes sont ouverts au support, un au design.",
                "Le déménagement est prévu en novembre, et le budget est validé.",
            ]],
            ["Les personnes", [
                "Six personnes ont travaillé sur la version, dont deux arrivées cette année.",
                "Les remplacements pour les congés ont été organisés en avril et ont tenu tout l'été.",
                "Tout le monde a suivi la formation qu'exige la nouvelle licence.",
            ]],
            ["Risques", [
                "Le déménagement de novembre est la seule date qui ne peut pas bouger.",
                "Un prestataire n'a pas encore signé les nouvelles conditions.",
                "Le coût de l'hébergement augmente en janvier sans renouvellement anticipé.",
            ]],
        ],
        "closing": "La prochaine réunion aura lieu fin juillet.",
        "sheets": ["Aperçu", "Coûts"],
        "item": "Poste",
        "total": "Total",
        "periods": ["Janv.", "Févr.", "Mars", "Avr.", "Mai", "Juin"],
        "rows": ["Logiciels", "Déplacements", "Matériel", "Marketing", "Support", "Formation", "Licences", "Hébergement", "Événements", "Bureau", "Cloud", "Recrutement", "Juridique", "Assurance", "Traiteur", "Expédition", "Publicité", "Conseil", "Maintenance", "Charges", "Équipement", "Abonnements", "Téléphonie", "Internet", "Sécurité", "Sauvegardes", "Domaines", "Certificats", "Impression", "Fournitures", "Affranchissement", "Nettoyage", "Réparations", "Mobilier", "Stockage", "Frais bancaires", "Cotisations", "Conférences", "Traduction", "Design"],
        "slides": [
            ["Plan du projet", ["Objectifs du trimestre", "Budget et coûts", "Prochaines étapes"]],
            ["Calendrier", ["Version en juin", "Bilan en juillet", "Planification en août"]],
            ["Équipe", ["Deux nouveaux partenaires", "Support en trois langues", "Formation à l'automne"]],
        ],
    },
    "it": {
        "title": "Relazione trimestrale",
        "lead": "Il team ha raggiunto tutti gli obiettivi del secondo trimestre e la nuova versione è uscita in tempo.",
        "sections": [
            ["In evidenza", [
                "I costi sono rimasti sotto il budget e due nuovi partner si sono uniti al progetto.",
                "La nuova versione ha raggiunto in una settimana più persone di quante ne avesse raggiunte la precedente in un mese.",
                "Il supporto ha risposto a nove richieste su dieci in giornata.",]],
            ["Costi e budget", [
                "La spesa per il software è cresciuta con le nuove licenze, mentre quella per i viaggi è di nuovo calata.",
                "L'hardware è stato sostituito una volta e il supporto è rimasto stabile per tutto il trimestre.",
                "Due server sono passati al nuovo fornitore senza un giorno di fermo.",]],
            ["Prossimo trimestre", [
                "La versione di settembre è l'ultima prevista quest'anno.",
                "Ci sono due posizioni aperte nel supporto e una nel design.",
                "Il trasloco è a novembre e il budget è approvato.",
            ]],
            ["Le persone", [
                "Alla versione hanno lavorato sei persone, due delle quali nuove quest'anno.",
                "Le sostituzioni estive sono state organizzate ad aprile e hanno retto.",
                "Tutti hanno seguito il corso richiesto dalla nuova licenza.",
            ]],
            ["Rischi", [
                "Il trasloco di novembre è l'unica data che non può slittare.",
                "Un fornitore non ha ancora firmato le nuove condizioni.",
                "I costi di hosting aumentano a gennaio se il contratto non viene rinnovato in anticipo.",
            ]],
        ],
        "closing": "Il prossimo incontro è a fine luglio.",
        "sheets": ["Panoramica", "Costi"],
        "item": "Voce",
        "total": "Totale",
        "periods": ["Gen", "Feb", "Mar", "Apr", "Mag", "Giu"],
        "rows": ["Software", "Viaggi", "Hardware", "Marketing", "Supporto", "Formazione", "Licenze", "Hosting", "Eventi", "Ufficio", "Cloud", "Selezione", "Legale", "Assicurazione", "Catering", "Spedizioni", "Pubblicità", "Consulenza", "Manutenzione", "Utenze", "Attrezzature", "Abbonamenti", "Telefonia", "Internet", "Sicurezza", "Backup", "Domini", "Certificati", "Stampa", "Cancelleria", "Affrancature", "Pulizie", "Riparazioni", "Arredi", "Archiviazione", "Spese bancarie", "Quote associative", "Conferenze", "Traduzioni", "Design"],
        "slides": [
            ["Piano di progetto", ["Obiettivi del trimestre", "Budget e costi", "Prossimi passi"]],
            ["Calendario", ["Versione a giugno", "Revisione a luglio", "Pianificazione ad agosto"]],
            ["Team", ["Due nuovi partner", "Supporto in tre lingue", "Formazione in autunno"]],
        ],
    },
    "pl": {
        "title": "Raport kwartalny",
        "lead": "Zespół osiągnął wszystkie cele drugiego kwartału, a nowa wersja ukazała się na czas.",
        "sections": [
            ["Najważniejsze", [
                "Koszty pozostały poniżej budżetu, a do projektu dołączyło dwóch nowych partnerów.",
                "Nowa wersja dotarła w pierwszym tygodniu do większej liczby osób niż poprzednia w miesiąc.",
                "Wsparcie odpowiedziało na dziewięć z dziesięciu zgłoszeń tego samego dnia.",]],
            ["Koszty i budżet", [
                "Wydatki na oprogramowanie wzrosły wraz z nowymi licencjami, a koszty podróży znów spadły.",
                "Sprzęt wymieniono raz, a wsparcie było stabilne przez cały kwartał.",
                "Dwa serwery przeniesiono do nowego dostawcy bez ani jednego dnia przestoju.",]],
            ["Następny kwartał", [
                "Wersja z września jest ostatnią zaplanowaną w tym roku.",
                "Otwarte są dwa etaty we wsparciu i jeden w dziale projektowym.",
                "Przeprowadzka biura wypada w listopadzie i ma już budżet.",
            ]],
            ["Ludzie", [
                "Nad wersją pracowało sześć osób, z czego dwie dołączyły w tym roku.",
                "Zastępstwa urlopowe ustalono w kwietniu i utrzymały się przez całe lato.",
                "Wszyscy przeszli szkolenie wymagane przez nową licencję.",
            ]],
            ["Ryzyka", [
                "Przeprowadzka w listopadzie to jedyny termin, który nie może się przesunąć.",
                "Jeden dostawca nie podpisał jeszcze nowych warunków i jest ponaglany.",
                "Koszty hostingu wzrosną w styczniu, jeśli umowa nie zostanie odnowiona wcześniej.",
            ]],
        ],
        "closing": "Następne spotkanie odbędzie się pod koniec lipca.",
        "sheets": ["Przegląd", "Koszty"],
        "item": "Pozycja",
        "total": "Razem",
        "periods": ["sty", "lut", "mar", "kwi", "maj", "cze"],
        "rows": ["Oprogramowanie", "Podróże", "Sprzęt", "Marketing", "Wsparcie", "Szkolenia", "Licencje", "Hosting", "Wydarzenia", "Biuro", "Chmura", "Rekrutacja", "Prawo", "Ubezpieczenie", "Catering", "Wysyłka", "Reklama", "Doradztwo", "Utrzymanie", "Media", "Wyposażenie", "Subskrypcje", "Telefonia", "Internet", "Bezpieczeństwo", "Kopie zapasowe", "Domeny", "Certyfikaty", "Druk", "Artykuły biurowe", "Opłaty pocztowe", "Sprzątanie", "Naprawy", "Meble", "Magazyn", "Opłaty bankowe", "Składki członkowskie", "Konferencje", "Tłumaczenia", "Projektowanie"],
        "slides": [
            ["Plan projektu", ["Cele na kwartał", "Budżet i koszty", "Kolejne kroki"]],
            ["Harmonogram", ["Wersja w czerwcu", "Podsumowanie w lipcu", "Planowanie w sierpniu"]],
            ["Zespół", ["Dwóch nowych partnerów", "Wsparcie w trzech językach", "Szkolenia jesienią"]],
        ],
    },
    "pt-BR": {
        "title": "Relatório trimestral",
        "lead": "A equipe alcançou todas as metas do segundo trimestre e a nova versão saiu no prazo.",
        "sections": [
            ["Destaques", [
                "Os custos ficaram abaixo do orçamento e dois novos parceiros entraram no projeto.",
                "A nova versão alcançou mais pessoas na primeira semana do que a anterior em um mês.",
                "O suporte respondeu nove de cada dez chamados no mesmo dia.",]],
            ["Custos e orçamento", [
                "Os gastos com software subiram com as novas licenças, enquanto as viagens caíram de novo.",
                "O hardware foi substituído uma vez e o suporte se manteve estável no trimestre.",
                "Dois servidores migraram para o novo provedor sem um dia de indisponibilidade.",]],
            ["Próximo trimestre", [
                "A versão de setembro é a última prevista para este ano.",
                "Há duas vagas no suporte e uma no design.",
                "A mudança de escritório é em novembro e já tem orçamento.",
            ]],
            ["As pessoas", [
                "Seis pessoas trabalharam na versão, duas delas novas este ano.",
                "A escala de férias foi definida em abril e valeu o verão todo.",
                "Todos fizeram o treinamento que a nova licença exige.",
            ]],
            ["Riscos", [
                "A mudança de novembro é a única data que não pode atrasar.",
                "Um fornecedor ainda não assinou as novas condições.",
                "A hospedagem sobe em janeiro se o contrato não for renovado antes.",
            ]],
        ],
        "closing": "A próxima reunião é no fim de julho.",
        "sheets": ["Visão geral", "Custos"],
        "item": "Item",
        "total": "Total",
        "periods": ["Jan", "Fev", "Mar", "Abr", "Mai", "Jun"],
        "rows": ["Software", "Viagens", "Hardware", "Marketing", "Suporte", "Treinamento", "Licenças", "Hospedagem", "Eventos", "Escritório", "Nuvem", "Recrutamento", "Jurídico", "Seguros", "Buffet", "Frete", "Publicidade", "Consultoria", "Manutenção", "Água e luz", "Equipamentos", "Assinaturas", "Telefonia", "Internet", "Segurança", "Backups", "Domínios", "Certificados", "Impressão", "Papelaria", "Correios", "Limpeza", "Reparos", "Móveis", "Armazenamento", "Tarifas bancárias", "Associações", "Conferências", "Tradução", "Design"],
        "slides": [
            ["Plano do projeto", ["Metas do trimestre", "Orçamento e custos", "Próximos passos"]],
            ["Cronograma", ["Versão em junho", "Revisão em julho", "Planejamento em agosto"]],
            ["Equipe", ["Dois novos parceiros", "Suporte em três idiomas", "Treinamento no outono"]],
        ],
    },
    "ru": {
        "title": "Квартальный отчёт",
        "lead": "Команда достигла всех целей второго квартала, и новая версия вышла в срок.",
        "sections": [
            ["Главное", [
                "Расходы остались в рамках бюджета, а к проекту присоединились два новых партнёра.",
                "За первую неделю новая версия охватила больше людей, чем предыдущая за месяц.",
                "Поддержка ответила на девять из десяти обращений в тот же день.",]],
            ["Расходы и бюджет", [
                "Затраты на ПО выросли из-за новых лицензий, а расходы на поездки снова снизились.",
                "Оборудование меняли один раз, поддержка работала стабильно весь квартал.",
                "Два сервера переехали к новому провайдеру без единого дня простоя.",]],
            ["Следующий квартал", [
                "Версия в сентябре — последняя из запланированных в этом году.",
                "Открыты две вакансии в поддержке и одна в дизайне.",
                "Переезд офиса намечен на ноябрь, бюджет утверждён.",
            ]],
            ["Люди", [
                "Над версией работали шесть человек, двое из них пришли в этом году.",
                "Замены на время отпусков согласовали в апреле, и график продержался всё лето.",
                "Все прошли обучение, которого требует новая лицензия.",
            ]],
            ["Риски", [
                "Переезд в ноябре — единственная дата, которую нельзя сдвинуть.",
                "Один поставщик так и не подписал новые условия, ему напоминают.",
                "Хостинг подорожает в январе, если не продлить договор заранее.",
            ]],
        ],
        "closing": "Следующая встреча — в конце июля.",
        "sheets": ["Обзор", "Расходы"],
        "item": "Статья",
        "total": "Итого",
        "periods": ["Янв.", "Февр.", "Март", "Апр.", "Май", "Июнь"],
        "rows": ["ПО", "Поездки", "Оборудование", "Маркетинг", "Поддержка", "Обучение", "Лицензии", "Хостинг", "Мероприятия", "Офис", "Облако", "Наём", "Юристы", "Страхование", "Кейтеринг", "Доставка", "Реклама", "Консалтинг", "Обслуживание", "Коммунальные услуги", "Оснащение", "Подписки", "Телефония", "Интернет", "Безопасность", "Резервное копирование", "Домены", "Сертификаты", "Печать", "Канцтовары", "Почтовые расходы", "Уборка", "Ремонт", "Мебель", "Хранение", "Банковские комиссии", "Членские взносы", "Конференции", "Перевод", "Дизайн"],
        "slides": [
            ["План проекта", ["Цели на квартал", "Бюджет и расходы", "Следующие шаги"]],
            ["График", ["Версия в июне", "Итоги в июле", "Планирование в августе"]],
            ["Команда", ["Два новых партнёра", "Поддержка на трёх языках", "Обучение осенью"]],
        ],
    },
    "tr": {
        "title": "Üç aylık rapor",
        "lead": "Ekip ikinci çeyreğin tüm hedeflerine ulaştı ve yeni sürüm zamanında yayınlandı.",
        "sections": [
            ["Öne çıkanlar", [
                "Maliyetler bütçenin altında kaldı ve projeye iki yeni ortak katıldı.",
                "Yeni sürüm ilk haftasında, öncekinin bir ayda ulaştığından daha fazla kişiye ulaştı.",
                "Destek, on sorudan dokuzunu aynı gün yanıtladı.",]],
            ["Maliyetler ve bütçe", [
                "Yeni lisanslarla yazılım harcamaları arttı, seyahat giderleri yeniden düştü.",
                "Donanım bir kez yenilendi ve destek çeyrek boyunca istikrarlı kaldı.",
                "İki sunucu, bir gün bile kesinti olmadan yeni sağlayıcıya taşındı.",]],
            ["Gelecek çeyrek", [
                "Eylüldeki sürüm bu yıl planlanan son sürüm.",
                "Destekte iki, tasarımda bir pozisyon açık.",
                "Ofis taşınması kasımda ve bütçesi onaylandı.",
            ]],
            ["Ekip", [
                "Sürüm üzerinde altı kişi çalıştı, ikisi bu yıl katıldı.",
                "İzin dönemi vekaletleri nisanda belirlendi ve yaz boyunca sorunsuz işledi.",
                "Herkes yeni lisansın gerektirdiği eğitimi tamamladı.",
            ]],
            ["Riskler", [
                "Kasımdaki taşınma, ertelenemeyecek tek tarih.",
                "Bir tedarikçi yeni koşulları henüz imzalamadı, takibi sürüyor.",
                "Sözleşme erken yenilenmezse barındırma maliyeti ocakta artacak.",
            ]],
        ],
        "closing": "Bir sonraki toplantı temmuz sonunda.",
        "sheets": ["Genel bakış", "Maliyetler"],
        "item": "Kalem",
        "total": "Toplam",
        "periods": ["Oca", "Şub", "Mar", "Nis", "May", "Haz"],
        "rows": ["Yazılım", "Seyahat", "Donanım", "Pazarlama", "Destek", "Eğitim", "Lisanslar", "Barındırma", "Etkinlikler", "Ofis", "Bulut", "İşe alım", "Hukuk", "Sigorta", "İkram", "Kargo", "Reklam", "Danışmanlık", "Bakım", "Faturalar", "Ekipman", "Abonelikler", "Telefon", "İnternet", "Güvenlik", "Yedekleme", "Alan adları", "Sertifikalar", "Baskı", "Kırtasiye", "Posta", "Temizlik", "Onarım", "Mobilya", "Depolama", "Banka masrafları", "Üyelikler", "Konferanslar", "Çeviri", "Tasarım"],
        "slides": [
            ["Proje planı", ["Çeyrek hedefleri", "Bütçe ve maliyetler", "Sonraki adımlar"]],
            ["Takvim", ["Haziranda sürüm", "Temmuzda değerlendirme", "Ağustosta planlama"]],
            ["Ekip", ["İki yeni ortak", "Üç dilde destek", "Sonbaharda eğitim"]],
        ],
    },
    "cs": {
        "title": "Čtvrtletní zpráva",
        "lead": "Tým splnil všechny cíle druhého čtvrtletí a nová verze vyšla včas.",
        "sections": [
            ["Nejdůležitější", [
                "Náklady zůstaly pod rozpočtem a k projektu se připojili dva noví partneři.",
                "Nová verze oslovila za první týden více lidí než ta předchozí za měsíc.",
                "Podpora odpověděla na devět z deseti dotazů týž den.",
            ]],
            ["Náklady a rozpočet", [
                "Výdaje za software s novými licencemi vzrostly, cestovné opět kleslo.",
                "Hardware byl jednou vyměněn a podpora zůstala po celé čtvrtletí stabilní.",
                "Dva servery přešly k novému poskytovateli bez jediného dne výpadku.",
            ]],
            ["Příští čtvrtletí", [
                "Zářijová verze je poslední plánovaná letos.",
                "V podpoře jsou volná dvě místa, v designu jedno.",
                "Stěhování kanceláře je v listopadu a rozpočet na ně je schválený.",
            ]],
            ["Lidé", [
                "Na verzi pracovalo šest lidí, dva z nich jsou tu první rok.",
                "Zástupy na dobu dovolených se domluvily v dubnu a vydržely celé léto.",
                "Všichni absolvovali školení, které nová licence vyžaduje.",
            ]],
            ["Rizika", [
                "Listopadové stěhování je jediný termín, který nelze posunout.",
                "Jeden dodavatel dosud nepodepsal nové podmínky a urgujeme ho.",
                "Náklady na hosting v lednu vzrostou, pokud se smlouva neobnoví dříve.",
            ]],
        ],
        "closing": "Další schůzka je na konci července.",
        "sheets": ["Přehled", "Náklady"],
        "item": "Položka",
        "total": "Celkem",
        "periods": ["Led", "Úno", "Bře", "Dub", "Kvě", "Čvn"],
        "rows": ["Software", "Cestovné", "Hardware", "Marketing", "Podpora", "Školení", "Licence", "Hosting", "Akce", "Kancelář", "Cloud", "Nábor", "Právní služby", "Pojištění", "Občerstvení", "Doprava", "Reklama", "Poradenství", "Údržba", "Energie", "Vybavení", "Předplatné", "Telefonie", "Internet", "Bezpečnost", "Zálohování", "Domény", "Certifikáty", "Tisk", "Papírnictví", "Poštovné", "Úklid", "Opravy", "Nábytek", "Sklad", "Bankovní poplatky", "Členské příspěvky", "Konference", "Překlady", "Design"],
        "slides": [
            ["Plán projektu", ["Cíle čtvrtletí", "Rozpočet a náklady", "Další kroky"]],
            ["Harmonogram", ["Verze v červnu", "Hodnocení v červenci", "Plánování v srpnu"]],
            ["Tým", ["Dva noví partneři", "Podpora ve třech jazycích", "Školení na podzim"]],
        ],
    },
    "et": {
        "title": "Kvartaliaruanne",
        "lead": "Meeskond täitis teise kvartali kõik eesmärgid ja uus versioon ilmus õigeks ajaks.",
        "sections": [
            ["Peamine", [
                "Kulud püsisid eelarve piires ja projektiga liitus kaks uut partnerit.",
                "Uus versioon jõudis esimese nädalaga rohkemate inimesteni kui eelmine kuuga.",
                "Kasutajatugi vastas üheksale küsimusele kümnest samal päeval.",
            ]],
            ["Kulud ja eelarve", [
                "Tarkvarakulud kasvasid uute litsentsidega, lähetuskulud vähenesid taas.",
                "Riistvara vahetati korra välja ja tugi püsis kogu kvartali ühtlasena.",
                "Kaks serverit kolisid uue teenusepakkuja juurde ilma ainsagi katkestuseta.",
            ]],
            ["Järgmine kvartal", [
                "Septembri versioon on selle aasta viimane plaanitud versioon.",
                "Toes on täitmata kaks kohta ja disainis üks.",
                "Kontor kolib novembris ja selle eelarve on kokku lepitud.",
            ]],
            ["Inimesed", [
                "Versiooni kallal töötas kuus inimest, kaks neist on siin esimest aastat.",
                "Puhkuseasendused lepiti kokku aprillis ja need pidasid terve suve.",
                "Kõik on läbinud koolituse, mida uus litsents nõuab.",
            ]],
            ["Riskid", [
                "Novembri kolimine on ainus kuupäev, mida edasi lükata ei saa.",
                "Üks tarnija pole uusi tingimusi veel allkirjastanud ja talle tuletatakse meelde.",
                "Majutuse hind tõuseb jaanuaris, kui lepingut varem ei pikendata.",
            ]],
        ],
        "closing": "Järgmine koosolek on juuli lõpus.",
        "sheets": ["Ülevaade", "Kulud"],
        "item": "Kirje",
        "total": "Kokku",
        "periods": ["Jaan", "Veebr", "Märts", "Apr", "Mai", "Juuni"],
        "rows": ["Tarkvara", "Lähetused", "Riistvara", "Turundus", "Kasutajatugi", "Koolitus", "Litsentsid", "Majutus", "Üritused", "Kontor", "Pilv", "Värbamine", "Õigusabi", "Kindlustus", "Toitlustus", "Saatmine", "Reklaam", "Konsultatsioon", "Hooldus", "Kommunaalkulud", "Seadmed", "Tellimused", "Telefon", "Internet", "Turvalisus", "Varundus", "Domeenid", "Sertifikaadid", "Trükkimine", "Kontoritarbed", "Postikulud", "Koristus", "Remont", "Mööbel", "Ladu", "Pangateenused", "Liikmemaksud", "Konverentsid", "Tõlge", "Disain"],
        "slides": [
            ["Projektiplaan", ["Kvartali eesmärgid", "Eelarve ja kulud", "Järgmised sammud"]],
            ["Ajakava", ["Versioon juunis", "Ülevaatus juulis", "Planeerimine augustis"]],
            ["Meeskond", ["Kaks uut partnerit", "Tugi kolmes keeles", "Koolitus sügisel"]],
        ],
    },
    "hi": {
        "title": "तिमाही रिपोर्ट",
        "lead": "टीम ने दूसरी तिमाही के सभी लक्ष्य पूरे किए और नया संस्करण समय पर जारी हुआ।",
        "sections": [
            ["मुख्य बातें", [
                "लागत बजट के भीतर रही और परियोजना से दो नए साझेदार जुड़े।",
                "नया संस्करण पहले सप्ताह में उतने लोगों तक पहुँचा, जितनों तक पिछला एक महीने में पहुँचा था।",
                "सहायता टीम ने दस में से नौ सवालों का जवाब उसी दिन दिया।",
            ]],
            ["लागत और बजट", [
                "नए लाइसेंसों के साथ सॉफ़्टवेयर पर खर्च बढ़ा, जबकि यात्रा खर्च फिर घटा।",
                "हार्डवेयर एक बार बदला गया और सहायता पूरी तिमाही स्थिर रही।",
                "दो सर्वर एक दिन की भी रुकावट के बिना नए प्रदाता पर चले गए।",
            ]],
            ["अगली तिमाही", [
                "सितंबर का संस्करण इस साल का आख़िरी नियोजित संस्करण है।",
                "सहायता में दो और डिज़ाइन में एक पद खाली है।",
                "दफ़्तर नवंबर में बदलेगा और उसका बजट तय हो चुका है।",
            ]],
            ["लोग", [
                "इस संस्करण पर छह लोगों ने काम किया, जिनमें दो इस साल जुड़े।",
                "छुट्टियों के दौरान की व्यवस्था अप्रैल में तय हुई और पूरी गर्मी चली।",
                "सभी ने वह प्रशिक्षण पूरा कर लिया है जो नया लाइसेंस माँगता है।",
            ]],
            ["जोखिम", [
                "नवंबर का स्थानांतरण वह अकेली तारीख़ है जो टल नहीं सकती।",
                "एक आपूर्तिकर्ता ने नई शर्तों पर अब तक हस्ताक्षर नहीं किए हैं और उनसे बात चल रही है।",
                "अनुबंध जल्दी नवीनीकृत न हुआ तो जनवरी में होस्टिंग की लागत बढ़ेगी।",
            ]],
        ],
        "closing": "अगली बैठक जुलाई के अंत में है।",
        "sheets": ["सारांश", "लागत"],
        "item": "मद",
        "total": "कुल",
        "periods": ["जन", "फ़र", "मार्च", "अप्रैल", "मई", "जून"],
        "rows": ["सॉफ़्टवेयर", "यात्रा", "हार्डवेयर", "मार्केटिंग", "सहायता", "प्रशिक्षण", "लाइसेंस", "होस्टिंग", "आयोजन", "दफ़्तर", "क्लाउड", "भर्ती", "कानूनी सेवाएँ", "बीमा", "जलपान", "शिपिंग", "विज्ञापन", "परामर्श", "रखरखाव", "बिजली-पानी", "उपकरण", "सदस्यता शुल्क", "टेलीफ़ोन", "इंटरनेट", "सुरक्षा", "बैकअप", "डोमेन", "प्रमाणपत्र", "छपाई", "लेखन सामग्री", "डाक", "सफ़ाई", "मरम्मत", "फ़र्नीचर", "भंडारण", "बैंक शुल्क", "संघ सदस्यता", "सम्मेलन", "अनुवाद", "डिज़ाइन"],
        "slides": [
            ["परियोजना योजना", ["तिमाही के लक्ष्य", "बजट और लागत", "अगले कदम"]],
            ["समय-सारिणी", ["जून में संस्करण", "जुलाई में समीक्षा", "अगस्त में योजना"]],
            ["टीम", ["दो नए साझेदार", "तीन भाषाओं में सहायता", "पतझड़ में प्रशिक्षण"]],
        ],
    },
    "ja": {
        "title": "四半期報告",
        "lead": "チームは第2四半期の目標をすべて達成し、新しいバージョンは予定どおり公開されました。",
        "sections": [
            ["主な成果", [
                "費用は予算内に収まり、新しいパートナーが2社加わりました。",
                "新しいバージョンは最初の1週間で、前回が1か月かけて届いた人数を上回りました。",
                "サポートは10件のうち9件の問い合わせに当日中に回答しました。",
            ]],
            ["費用と予算", [
                "新しいライセンスによりソフトウェア費用は増え、出張費は再び減りました。",
                "ハードウェアは1度だけ入れ替え、サポートは四半期を通じて安定していました。",
                "サーバー2台を、1日も停止させずに新しい事業者へ移しました。",
            ]],
            ["次の四半期", [
                "9月のリリースが今年最後の予定です。",
                "サポートで2名、デザインで1名を募集しています。",
                "オフィスの移転は11月で、その予算はすでに決まっています。",
            ]],
            ["メンバー", [
                "このバージョンには6名が携わり、うち2名は今年からの参加です。",
                "休暇中の担当は4月に決め、夏の間ずっと機能しました。",
                "新しいライセンスが求める研修は全員が修了しています。",
            ]],
            ["リスク", [
                "11月の移転は、ほかの予定に押されて動かせない唯一の日程です。",
                "1社の取引先がまだ新しい条件に署名しておらず、確認を続けています。",
                "契約を早めに更新しなければ、ホスティング費用は1月に上がります。",
            ]],
        ],
        "closing": "次回の打ち合わせは7月末です。",
        "sheets": ["概要", "費用"],
        "item": "項目",
        "total": "合計",
        "periods": ["1月", "2月", "3月", "4月", "5月", "6月"],
        "rows": ["ソフトウェア", "出張", "ハードウェア", "マーケティング", "サポート", "研修", "ライセンス", "ホスティング", "イベント", "オフィス", "クラウド", "採用", "法務", "保険", "飲食", "配送", "広告", "コンサルティング", "保守", "光熱費", "備品", "定期購読", "電話", "インターネット", "セキュリティ", "バックアップ", "ドメイン", "証明書", "印刷", "文具", "郵送", "清掃", "修繕", "家具", "保管", "銀行手数料", "会費", "会議", "翻訳", "デザイン"],
        "slides": [
            ["プロジェクト計画", ["四半期の目標", "予算と費用", "次のステップ"]],
            ["スケジュール", ["6月にリリース", "7月に振り返り", "8月に計画"]],
            ["チーム", ["新しいパートナー2社", "3か国語でのサポート", "秋に研修"]],
        ],
    },
    "sv": {
        "title": "Kvartalsrapport",
        "lead": "Teamet nådde alla mål för det andra kvartalet, och den nya versionen kom ut i tid.",
        "sections": [
            ["Höjdpunkter", [
                "Kostnaderna höll sig under budget, och två nya partner anslöt sig till projektet.",
                "Den nya versionen nådde fler människor på sin första vecka än den förra gjorde på en månad.",
                "Supporten besvarade nio av tio frågor samma dag.",
            ]],
            ["Kostnader och budget", [
                "Utgifterna för programvara steg med de nya licenserna, medan resorna sjönk igen.",
                "Hårdvaran byttes ut en gång, och supporten var stabil hela kvartalet.",
                "Två servrar flyttade till den nya leverantören utan en enda dags avbrott.",
            ]],
            ["Nästa kvartal", [
                "Versionen i september är den sista som planeras i år.",
                "Två tjänster är lediga i supporten och en i designen.",
                "Kontoret flyttar i november, och budgeten för det är beslutad.",
            ]],
            ["Människorna", [
                "Sex personer arbetade med versionen, två av dem nya i år.",
                "Semestervikariaten ordnades i april och höll hela sommaren.",
                "Alla har gått den utbildning som den nya licensen kräver.",
            ]],
            ["Risker", [
                "Flytten i november är det enda datum som ingenting annat får skjuta framför sig.",
                "En leverantör har ännu inte skrivit under de nya villkoren, och vi ligger på.",
                "Kostnaden för drift stiger i januari om avtalet inte förnyas i förtid.",
            ]],
        ],
        "closing": "Nästa möte är i slutet av juli.",
        "sheets": ["Översikt", "Kostnader"],
        "item": "Post",
        "total": "Totalt",
        "periods": ["Jan", "Feb", "Mar", "Apr", "Maj", "Jun"],
        "rows": ["Programvara", "Resor", "Hårdvara", "Marknadsföring", "Support", "Utbildning", "Licenser", "Drift", "Evenemang", "Kontor", "Moln", "Rekrytering", "Juridik", "Försäkring", "Förtäring", "Frakt", "Annonsering", "Konsulter", "Underhåll", "El och vatten", "Utrustning", "Prenumerationer", "Telefoni", "Internet", "Säkerhet", "Säkerhetskopior", "Domäner", "Certifikat", "Tryck", "Kontorsmaterial", "Porto", "Städning", "Reparationer", "Möbler", "Lager", "Bankavgifter", "Medlemskap", "Konferenser", "Översättning", "Design"],
        "slides": [
            ["Projektplan", ["Mål för kvartalet", "Budget och kostnader", "Nästa steg"]],
            ["Tidsplan", ["Version i juni", "Genomgång i juli", "Planering i augusti"]],
            ["Team", ["Två nya partner", "Support på tre språk", "Utbildning i höst"]],
        ],
    },
    "zh": {
        "title": "季度报告",
        "lead": "团队完成了第二季度的全部目标，新版本按时发布。",
        "sections": [
            ["重点", [
                "成本控制在预算之内，另有两家新伙伴加入这个项目。",
                "新版本在第一周触达的人数，超过上一版整整一个月的数字。",
                "支持团队当天回复了十个问题中的九个。",
            ]],
            ["成本与预算", [
                "新许可证让软件开支上升，差旅费用再次下降。",
                "硬件更换了一次，支持在整个季度保持稳定。",
                "两台服务器迁到新的服务商，没有一天中断。",
            ]],
            ["下个季度", [
                "九月的版本是今年计划中的最后一个。",
                "支持岗位空出两个，设计岗位一个。",
                "办公室在十一月搬迁，预算已经定下。",
            ]],
            ["团队", [
                "六个人参与了这个版本，其中两位是今年新来的。",
                "休假期间的顶班在四月就安排好，整个夏天都没出问题。",
                "新许可证要求的培训，所有人都已完成。",
            ]],
            ["风险", [
                "十一月的搬迁是唯一无法推迟的日期。",
                "有一家供应商还没有签署新条款，我们正在跟进。",
                "如果合同不提前续签，托管费用会在一月上涨。",
            ]],
        ],
        "closing": "下次会议在七月底。",
        "sheets": ["概览", "成本"],
        "item": "项目",
        "total": "合计",
        "periods": ["一月", "二月", "三月", "四月", "五月", "六月"],
        "rows": ["软件", "差旅", "硬件", "市场推广", "支持", "培训", "许可证", "托管", "活动", "办公室", "云服务", "招聘", "法务", "保险", "餐饮", "运输", "广告", "咨询", "维护", "水电", "设备", "订阅", "电话", "网络", "安全", "备份", "域名", "证书", "印刷", "文具", "邮费", "保洁", "维修", "家具", "仓储", "银行手续费", "会员费", "会议", "翻译", "设计"],
        "slides": [
            ["项目计划", ["本季度目标", "预算与成本", "下一步"]],
            ["时间表", ["六月发布", "七月复盘", "八月规划"]],
            ["团队", ["两家新伙伴", "三种语言的支持", "秋季培训"]],
        ],
    },
}

# What the sheets add up: forty rows over six periods, so there is enough of it
# to look like a spreadsheet. The first twenty keep the first four figures they
# had, because the invoice and the .xlsx take slices off the front.
FIGURES = [
    [1200, 1450, 1310, 1600, 1380, 1520],
    [480, 620, 510, 470, 690, 540],
    [3600, 900, 1200, 750, 830, 1150],
    [820, 760, 930, 1010, 870, 940],
    [540, 560, 580, 600, 610, 630],
    [300, 450, 380, 520, 410, 470],
    [1100, 1150, 1180, 1240, 1260, 1290],
    [640, 640, 660, 680, 700, 700],
    [420, 980, 350, 610, 1240, 380],
    [260, 280, 270, 300, 290, 310],
    [890, 910, 940, 980, 1000, 1030],
    [1500, 400, 620, 350, 480, 390],
    [340, 360, 350, 370, 380, 390],
    [220, 220, 230, 230, 240, 240],
    [180, 620, 210, 240, 190, 660],
    [410, 430, 400, 450, 440, 460],
    [760, 820, 690, 900, 850, 780],
    [950, 480, 1100, 520, 560, 1180],
    [280, 290, 300, 310, 320, 330],
    [520, 540, 530, 560, 570, 590],
    [1340, 1290, 1410, 1360, 1440, 1480],
    [710, 730, 720, 750, 760, 780],
    [190, 200, 190, 210, 200, 220],
    [330, 330, 340, 340, 350, 350],
    [860, 890, 1240, 910, 930, 960],
    [240, 250, 260, 260, 270, 280],
    [120, 130, 120, 140, 130, 150],
    [460, 170, 180, 490, 180, 190],
    [580, 610, 550, 640, 600, 670],
    [150, 160, 170, 160, 180, 170],
    [210, 230, 220, 250, 240, 260],
    [390, 390, 400, 410, 410, 420],
    [270, 1080, 310, 340, 290, 360],
    [1620, 350, 380, 360, 400, 370],
    [620, 650, 630, 680, 660, 700],
    [110, 120, 110, 130, 120, 140],
    [440, 450, 460, 470, 480, 490],
    [980, 1020, 640, 1060, 1090, 720],
    [560, 500, 590, 530, 610, 570],
    [740, 770, 800, 790, 830, 860],
]

# What the browser lists them as. Realistic rather than descriptive: the first
# screenshot is meant to look like somebody's folder, and the extensions do the
# talking about what the app opens.
FILE_NAMES = {
    "en": {"text": "Quarterly report", "sheet": "Budget", "slides": "Project plan",
           "word": "Contract", "cells": "Sales figures", "deck": "Team offsite",
           "paper": "Invoice", "rows": "Contacts", "notes": "Notes"},
    "de": {"text": "Quartalsbericht", "sheet": "Budget", "slides": "Projektplan",
           "word": "Vertrag", "cells": "Umsatzzahlen", "deck": "Teamtreffen",
           "paper": "Rechnung", "rows": "Kontakte", "notes": "Notizen"},
    "es": {"text": "Informe trimestral", "sheet": "Presupuesto", "slides": "Plan del proyecto",
           "word": "Contrato", "cells": "Cifras de ventas", "deck": "Jornada de equipo",
           "paper": "Factura", "rows": "Contactos", "notes": "Notas"},
    "fr": {"text": "Rapport trimestriel", "sheet": "Budget", "slides": "Plan du projet",
           "word": "Contrat", "cells": "Chiffres des ventes", "deck": "Réunion d'équipe",
           "paper": "Facture", "rows": "Contacts", "notes": "Notes"},
    "it": {"text": "Relazione trimestrale", "sheet": "Budget", "slides": "Piano di progetto",
           "word": "Contratto", "cells": "Dati di vendita", "deck": "Ritiro del team",
           "paper": "Fattura", "rows": "Contatti", "notes": "Note"},
    "pl": {"text": "Raport kwartalny", "sheet": "Budżet", "slides": "Plan projektu",
           "word": "Umowa", "cells": "Wyniki sprzedaży", "deck": "Spotkanie zespołu",
           "paper": "Faktura", "rows": "Kontakty", "notes": "Notatki"},
    "pt-BR": {"text": "Relatório trimestral", "sheet": "Orçamento", "slides": "Plano do projeto",
              "word": "Contrato", "cells": "Números de vendas", "deck": "Reunião da equipe",
              "paper": "Fatura", "rows": "Contatos", "notes": "Notas"},
    "ru": {"text": "Квартальный отчёт", "sheet": "Бюджет", "slides": "План проекта",
           "word": "Договор", "cells": "Продажи", "deck": "Встреча команды",
           "paper": "Счёт", "rows": "Контакты", "notes": "Заметки"},
    "tr": {"text": "Üç aylık rapor", "sheet": "Bütçe", "slides": "Proje planı",
           "word": "Sözleşme", "cells": "Satış rakamları", "deck": "Ekip çalıştayı",
           "paper": "Fatura", "rows": "Kişiler", "notes": "Notlar"},
    "cs": {"text": "Čtvrtletní zpráva", "sheet": "Rozpočet", "slides": "Plán projektu",
           "word": "Smlouva", "cells": "Čísla prodejů", "deck": "Setkání týmu",
           "paper": "Faktura", "rows": "Kontakty", "notes": "Poznámky"},
    "et": {"text": "Kvartaliaruanne", "sheet": "Eelarve", "slides": "Projektiplaan",
           "word": "Leping", "cells": "Müüginumbrid", "deck": "Meeskonnapäev",
           "paper": "Arve", "rows": "Kontaktid", "notes": "Märkmed"},
    "hi": {"text": "तिमाही रिपोर्ट", "sheet": "बजट", "slides": "परियोजना योजना",
           "word": "अनुबंध", "cells": "बिक्री के आँकड़े", "deck": "टीम बैठक",
           "paper": "बिल", "rows": "संपर्क", "notes": "नोट्स"},
    "ja": {"text": "四半期報告", "sheet": "予算", "slides": "プロジェクト計画",
           "word": "契約書", "cells": "売上データ", "deck": "チーム合宿",
           "paper": "請求書", "rows": "連絡先", "notes": "メモ"},
    "sv": {"text": "Kvartalsrapport", "sheet": "Budget", "slides": "Projektplan",
           "word": "Avtal", "cells": "Försäljningssiffror", "deck": "Teamdag",
           "paper": "Faktura", "rows": "Kontakter", "notes": "Anteckningar"},
    "zh": {"text": "季度报告", "sheet": "预算", "slides": "项目计划",
           "word": "合同", "cells": "销售数据", "deck": "团队会议",
           "paper": "发票", "rows": "联系人", "notes": "笔记"},
}

# The rest of the folder, so it does not read as a set of nine samples. Each is
# a copy of the sample named beside it, which only decides its icon: the browser
# shows a name and an icon, and none of them is ever opened.
FILLERS = {
    "meeting": "text",
    "letter": "text",
    "travel": "text",
    "reading": "text",
    "household": "sheet",
    "hours": "sheet",
    "stocktake": "sheet",
    "kickoff": "slides",
    "course": "slides",
    "lease": "word",
    "resume": "word",
    "application": "word",
    "expenses": "cells",
    "inventory": "cells",
    "review": "deck",
    "ticket": "paper",
    "warranty": "paper",
    "manual": "paper",
}

# A language with none of its own falls back to English.
FILLER_NAMES = {
    "en": {
        "meeting": "Meeting notes", "letter": "Letter to the landlord",
        "travel": "Travel plan", "reading": "Reading list",
        "household": "Household budget", "hours": "Hours", "stocktake": "Stocktake",
        "kickoff": "Kickoff", "course": "Course slides",
        "lease": "Lease", "resume": "CV", "application": "Application",
        "expenses": "Expenses", "inventory": "Inventory",
        "review": "Quarterly review",
        "ticket": "Ticket", "warranty": "Warranty", "manual": "Manual",
    },
    "de": {
        "meeting": "Besprechungsnotizen", "letter": "Brief an den Vermieter",
        "travel": "Reiseplan", "reading": "Leseliste",
        "household": "Haushaltsbudget", "hours": "Arbeitszeiten", "stocktake": "Inventur",
        "kickoff": "Auftakt", "course": "Kursfolien",
        "lease": "Mietvertrag", "resume": "Lebenslauf", "application": "Bewerbung",
        "expenses": "Ausgaben", "inventory": "Bestand",
        "review": "Quartalsrückblick",
        "ticket": "Ticket", "warranty": "Garantie", "manual": "Anleitung",
    },
    "es": {
        "meeting": "Notas de reunión", "letter": "Carta al casero",
        "travel": "Plan de viaje", "reading": "Lista de lectura",
        "household": "Presupuesto doméstico", "hours": "Horas", "stocktake": "Recuento",
        "kickoff": "Arranque del proyecto", "course": "Diapositivas del curso",
        "lease": "Alquiler del piso", "resume": "Currículum", "application": "Solicitud",
        "expenses": "Gastos", "inventory": "Inventario",
        "review": "Revisión trimestral",
        "ticket": "Billete", "warranty": "Garantía", "manual": "Manual",
    },
    "fr": {
        "meeting": "Notes de réunion", "letter": "Lettre au propriétaire",
        "travel": "Itinéraire", "reading": "Liste de lecture",
        "household": "Budget familial", "hours": "Heures", "stocktake": "Inventaire",
        "kickoff": "Lancement", "course": "Diapositives du cours",
        "lease": "Bail", "resume": "CV", "application": "Candidature",
        "expenses": "Dépenses", "inventory": "Stock",
        "review": "Bilan trimestriel",
        "ticket": "Billet", "warranty": "Garantie", "manual": "Manuel",
    },
    "it": {
        "meeting": "Note della riunione", "letter": "Lettera al locatore",
        "travel": "Piano di viaggio", "reading": "Lista di lettura",
        "household": "Bilancio familiare", "hours": "Ore", "stocktake": "Inventario",
        "kickoff": "Avvio", "course": "Diapositive del corso",
        "lease": "Contratto d'affitto", "resume": "Curriculum", "application": "Candidatura",
        "expenses": "Spese", "inventory": "Magazzino",
        "review": "Revisione trimestrale",
        "ticket": "Biglietto", "warranty": "Garanzia", "manual": "Manuale",
    },
    "pl": {
        "meeting": "Notatki ze spotkania", "letter": "List do właściciela mieszkania",
        "travel": "Plan podróży", "reading": "Lista lektur",
        "household": "Budżet domowy", "hours": "Godziny", "stocktake": "Inwentaryzacja",
        "kickoff": "Spotkanie startowe", "course": "Slajdy kursu",
        "lease": "Umowa najmu", "resume": "CV", "application": "Podanie",
        "expenses": "Wydatki", "inventory": "Stan magazynu",
        "review": "Przegląd kwartalny",
        "ticket": "Bilet", "warranty": "Gwarancja", "manual": "Instrukcja",
    },
    "pt-BR": {
        "meeting": "Notas da reunião", "letter": "Carta ao locador",
        "travel": "Plano de viagem", "reading": "Lista de leitura",
        "household": "Orçamento doméstico", "hours": "Horas", "stocktake": "Balanço",
        "kickoff": "Kickoff", "course": "Slides do curso",
        "lease": "Contrato de aluguel", "resume": "Currículo", "application": "Inscrição",
        "expenses": "Despesas", "inventory": "Estoque",
        "review": "Revisão trimestral",
        "ticket": "Passagem", "warranty": "Garantia", "manual": "Manual",
    },
    "ru": {
        "meeting": "Заметки со встречи",
        "letter": "Письмо арендодателю",
        "travel": "План поездки",
        "reading": "Список чтения",
        "household": "Домашний бюджет",
        "hours": "Часы",
        "stocktake": "Инвентаризация",
        "kickoff": "Старт проекта",
        "course": "Слайды курса",
        "lease": "Договор аренды",
        "resume": "Резюме",
        "application": "Заявление",
        "expenses": "Расходы",
        "inventory": "Склад",
        "review": "Квартальный обзор",
        "ticket": "Билет",
        "warranty": "Гарантия",
        "manual": "Инструкция",
    },
    "tr": {
        "meeting": "Toplantı notları", "letter": "Ev sahibine mektup",
        "travel": "Seyahat planı", "reading": "Okuma listesi",
        "household": "Ev bütçesi", "hours": "Çalışma saatleri",
        "stocktake": "Sayım",
        "kickoff": "Proje başlangıcı", "course": "Kurs slaytları",
        "lease": "Kira sözleşmesi", "resume": "Özgeçmiş",
        "application": "Başvuru",
        "expenses": "Giderler", "inventory": "Envanter",
        "review": "Üç aylık değerlendirme",
        "ticket": "Bilet", "warranty": "Garanti", "manual": "Kılavuz",
    },
    "cs": {
        "meeting": "Zápis z porady", "letter": "Dopis pronajímateli",
        "travel": "Plán cesty", "reading": "Seznam ke čtení",
        "household": "Rodinný rozpočet", "hours": "Odpracované hodiny",
        "stocktake": "Inventura",
        "kickoff": "Zahájení projektu", "course": "Slidy ke kurzu",
        "lease": "Nájemní smlouva", "resume": "Životopis",
        "application": "Přihláška",
        "expenses": "Výdaje", "inventory": "Zásoby",
        "review": "Čtvrtletní hodnocení",
        "ticket": "Jízdenka", "warranty": "Záruka", "manual": "Návod",
    },
    "et": {
        "meeting": "Koosoleku märkmed", "letter": "Kiri üürileandjale",
        "travel": "Reisiplaan", "reading": "Lugemisnimekiri",
        "household": "Kodune eelarve", "hours": "Töötunnid",
        "stocktake": "Inventuur",
        "kickoff": "Projekti algus", "course": "Koolituse slaidid",
        "lease": "Üürileping", "resume": "Elulookirjeldus",
        "application": "Avaldus",
        "expenses": "Kulud", "inventory": "Laoseis",
        "review": "Kvartali ülevaade",
        "ticket": "Pilet", "warranty": "Garantii", "manual": "Kasutusjuhend",
    },
    "hi": {
        "meeting": "बैठक के नोट्स", "letter": "मकान मालिक को पत्र",
        "travel": "यात्रा योजना", "reading": "पढ़ने की सूची",
        "household": "घर का बजट", "hours": "काम के घंटे",
        "stocktake": "स्टॉक जाँच",
        "kickoff": "शुरुआती बैठक", "course": "कोर्स स्लाइड",
        "lease": "किरायानामा", "resume": "बायोडाटा",
        "application": "आवेदन",
        "expenses": "खर्च", "inventory": "सूची",
        "review": "तिमाही समीक्षा",
        "ticket": "टिकट", "warranty": "वारंटी", "manual": "मैनुअल",
    },
    "ja": {
        "meeting": "打ち合わせメモ", "letter": "大家さんへの手紙",
        "travel": "旅行の予定", "reading": "読みたい本",
        "household": "家計簿", "hours": "勤務時間",
        "stocktake": "棚卸し",
        "kickoff": "キックオフ", "course": "研修スライド",
        "lease": "賃貸契約書", "resume": "履歴書",
        "application": "申込書",
        "expenses": "経費", "inventory": "在庫",
        "review": "四半期レビュー",
        "ticket": "チケット", "warranty": "保証書", "manual": "取扱説明書",
    },
    "sv": {
        "meeting": "Mötesanteckningar", "letter": "Brev till hyresvärden",
        "travel": "Resplan", "reading": "Läslista",
        "household": "Hushållsbudget", "hours": "Arbetstider",
        "stocktake": "Inventering",
        "kickoff": "Uppstart", "course": "Kursbilder",
        "lease": "Hyresavtal", "resume": "CV",
        "application": "Ansökan",
        "expenses": "Utgifter", "inventory": "Lagerlista",
        "review": "Kvartalsgenomgång",
        "ticket": "Biljett", "warranty": "Garanti", "manual": "Bruksanvisning",
    },
    "zh": {
        "meeting": "会议记录", "letter": "给房东的信",
        "travel": "行程安排", "reading": "阅读清单",
        "household": "家庭预算", "hours": "工时",
        "stocktake": "盘点",
        "kickoff": "启动会", "course": "课程幻灯片",
        "lease": "租赁合同", "resume": "简历",
        "application": "申请表",
        "expenses": "开支", "inventory": "库存",
        "review": "季度回顾",
        "ticket": "车票", "warranty": "保修单", "manual": "说明书",
    },
}


# The languages that do not put spaces between words at all, where counting runs
# of letters would hand the search a whole clause. Written down instead, and
# checked below against the document so a term that stops appearing in it is an
# error rather than a screenshot of a search that found nothing.
UNSPACED = {
    "ja": "サポート",
    "zh": "支持",
}

# What separates one word from the next, everywhere else - written as what breaks
# a word rather than as what a word is made of. `\w` is the other way round and
# is wrong for half this list: a Devanagari vowel sign is not a letter, a digit
# or an underscore, so a Hindi word comes apart at every matra.
BREAK = re.compile(r"[\s.,;:!?()\[\]{}<>\"'«»„“”‘’—–\-/\\|…।]+")


# The word the search screenshot looks for.
#
# Counted out of the document rather than written down, so it is always a word
# that is really in there, and always one that is in there several times - a
# search that highlights a single hit does not look like a search. Short words
# are skipped because "the" and "and" say nothing about the document.
def query(words: dict, language: str = "") -> str:
    """The most repeated long word of the report, which is what to search for."""
    text = " ".join(
        [words["title"], words["lead"], words["closing"]]
        + [heading for heading, _ in words["sections"]]
        + [line for _, paragraphs in words["sections"] for line in paragraphs]
    )

    if language in UNSPACED:
        written = UNSPACED[language]
        if text.count(written) < 2:
            raise ValueError(
                f"{language}: the report says '{written}' {text.count(written)} times, so "
                "searching for it would photograph a search that found nothing. "
                "Pick another word in UNSPACED."
            )
        return written

    counted = {}
    for word in BREAK.split(text.lower()):
        if len(word) >= 5 and not word.isdigit():
            counted[word] = counted.get(word, 0) + 1

    if not counted:
        raise ValueError(f"{language or 'this language'}: nothing in the report to search for")

    # the most repeated, and the longest of those, so the choice is not a coin toss
    return max(counted, key=lambda word: (counted[word], len(word)))


# The folder is not nine copies of one report. What each file is called says
# what it should hold, so the contract reads like a contract and the invoice
# like an invoice - a folder where every document has the same title is the one
# thing a picture of a folder must not be.
OTHERS = {
    "en": {
        "contract": ["Service agreement",
                     "This agreement is made between the two parties named below.",
                     ["The supplier provides the software described in the appendix for one year.",
                      "Payment is due within thirty days of each invoice.",
                      "Either party may end this agreement with three months' notice.",
                      "Changes to this agreement are valid only in writing.",
                      "The supplier keeps the service available on working days.",
                      "Both parties treat what they learn of each other as confidential.",
                      "Austrian law applies, and the court of Vienna has jurisdiction.",
                      "The supplier keeps a backup of the customer's data for thirty days.",
                      "Support requests are answered within one working day.",
                      "The customer names one person who may approve changes.",
                      "Prices hold for the first year and are reviewed each autumn.",
                      "Neither party may pass this agreement to a third party without consent.",
                      "The appendix lists the software covered and the version it starts at.",
                      "This agreement replaces every earlier arrangement between the parties."]],
        "invoice": ["Invoice 2026-014", "Issued 12 June 2026", "Due within 30 days", "Billed to", "Subtotal", "VAT 20%", "Amount due", "Thank you for your business.", "Qty", "Unit price"],
        "contacts": [["Name", "Team", "Email", "Phone"],
                     ["Design", "Support", "Sales", "Engineering"]],
    },
    "de": {
        "contract": ["Dienstleistungsvertrag",
                     "Dieser Vertrag wird zwischen den beiden unten genannten Parteien geschlossen.",
                     ["Der Anbieter stellt die im Anhang beschriebene Software für ein Jahr bereit.",
                      "Die Zahlung ist innerhalb von dreißig Tagen nach Rechnungsstellung fällig.",
                      "Beide Parteien können den Vertrag mit einer Frist von drei Monaten kündigen.",
                      "Änderungen dieses Vertrags bedürfen der Schriftform.",
                      "Der Anbieter hält den Dienst an Werktagen verfügbar.",
                      "Beide Parteien behandeln vertraulich, was sie voneinander erfahren.",
                      "Es gilt österreichisches Recht; Gerichtsstand ist Wien.",
                      "Der Anbieter bewahrt eine Sicherung der Daten des Kunden dreißig Tage lang auf.",
                      "Supportanfragen werden innerhalb eines Werktags beantwortet.",
                      "Der Kunde benennt eine Person, die Änderungen freigeben darf.",
                      "Die Preise gelten im ersten Jahr und werden jeden Herbst überprüft.",
                      "Keine Partei darf diesen Vertrag ohne Zustimmung an Dritte weitergeben.",
                      "Der Anhang nennt die erfasste Software und die Version, ab der sie gilt.",
                      "Dieser Vertrag ersetzt alle früheren Vereinbarungen zwischen den Parteien."]],
        "invoice": ["Rechnung 2026-014", "Ausgestellt am 12. Juni 2026", "Zahlbar innerhalb von 30 Tagen", "Rechnung an", "Zwischensumme", "USt. 20%", "Zahlbetrag", "Vielen Dank für Ihren Auftrag.", "Menge", "Einzelpreis"],
        "contacts": [["Name", "Team", "E-Mail", "Telefon"],
                     ["Design", "Support", "Vertrieb", "Entwicklung"]],
    },
    "es": {
        "contract": ["Contrato de servicios",
                     "Este contrato se celebra entre las dos partes indicadas a continuación.",
                     ["El proveedor facilita el software descrito en el anexo durante un año.",
                      "El pago vence a los treinta días de cada factura.",
                      "Cualquiera de las partes puede rescindirlo con tres meses de preaviso.",
                      "Las modificaciones solo son válidas por escrito.",
                      "El proveedor mantiene el servicio disponible los días laborables.",
                      "Ambas partes tratan como confidencial lo que conozcan de la otra.",
                      "Se aplica la ley austriaca y el tribunal de Viena es competente.",
                      "El proveedor conserva una copia de los datos del cliente durante treinta días.",
                      "Las consultas de soporte se responden en un día laborable.",
                      "El cliente designa a una persona que puede aprobar los cambios.",
                      "Los precios se mantienen el primer año y se revisan cada otoño.",
                      "Ninguna parte puede ceder este contrato a un tercero sin consentimiento.",
                      "El anexo enumera el software incluido y la versión desde la que se aplica.",
                      "Este contrato sustituye cualquier acuerdo anterior entre las partes."]],
        "invoice": ["Factura 2026-014", "Emitida el 12 de junio de 2026", "Vence en 30 días", "Facturar a", "Subtotal", "IVA 20%", "Importe a pagar", "Gracias por su confianza.", "Cant.", "Precio unit."],
        "contacts": [["Nombre", "Equipo", "Correo", "Teléfono"],
                     ["Diseño", "Soporte", "Ventas", "Ingeniería"]],
    },
    "fr": {
        "contract": ["Contrat de service",
                     "Ce contrat est conclu entre les deux parties désignées ci-dessous.",
                     ["Le prestataire fournit le logiciel décrit en annexe pendant un an.",
                      "Le paiement est dû dans les trente jours suivant chaque facture.",
                      "Chaque partie peut résilier le contrat avec un préavis de trois mois.",
                      "Toute modification n'est valable que par écrit.",
                      "Le prestataire maintient le service disponible les jours ouvrés.",
                      "Chaque partie traite comme confidentiel ce qu'elle apprend de l'autre.",
                      "Le droit autrichien s'applique et le tribunal de Vienne est compétent.",
                      "Le prestataire conserve une sauvegarde des données du client pendant trente jours.",
                      "Les demandes de support reçoivent une réponse sous un jour ouvré.",
                      "Le client désigne une personne habilitée à approuver les modifications.",
                      "Les prix sont fermes la première année et revus chaque automne.",
                      "Aucune des parties ne peut céder ce contrat à un tiers sans accord.",
                      "L'annexe indique le logiciel couvert et la version à partir de laquelle la couverture s'applique.",
                      "Ce contrat remplace tout accord antérieur entre les parties."]],
        "invoice": ["Facture 2026-014", "Émise le 12 juin 2026", "À régler sous 30 jours", "Facturé à", "Sous-total", "TVA 20 %", "Montant dû", "Merci de votre confiance.", "Qté", "Prix unitaire"],
        "contacts": [["Nom", "Équipe", "E-mail", "Téléphone"],
                     ["Design", "Support", "Ventes", "Développement"]],
    },
    "it": {
        "contract": ["Contratto di servizio",
                     "Il presente contratto è stipulato tra le due parti indicate di seguito.",
                     ["Il fornitore mette a disposizione per un anno il software descritto in allegato.",
                      "Il pagamento è dovuto entro trenta giorni da ogni fattura.",
                      "Ciascuna parte può recedere con un preavviso di tre mesi.",
                      "Le modifiche sono valide solo in forma scritta.",
                      "Il fornitore mantiene il servizio disponibile nei giorni lavorativi.",
                      "Le parti trattano come riservato quanto apprendono l'una dell'altra.",
                      "Si applica il diritto austriaco e il foro competente è Vienna.",
                      "Il fornitore conserva una copia dei dati del cliente per trenta giorni.",
                      "Le richieste di supporto ricevono risposta entro un giorno lavorativo.",
                      "Il cliente indica una persona autorizzata ad approvare le modifiche.",
                      "I prezzi restano fermi il primo anno e sono rivisti ogni autunno.",
                      "Nessuna parte può cedere il contratto a terzi senza consenso.",
                      "L'allegato elenca il software coperto e la versione di partenza.",
                      "Il presente contratto sostituisce ogni accordo precedente tra le parti."]],
        "invoice": ["Fattura 2026-014", "Emessa il 12 giugno 2026", "Da saldare entro 30 giorni", "Intestato a", "Subtotale", "IVA 20%", "Importo dovuto", "Grazie per la collaborazione.", "Qtà", "Prezzo unit."],
        "contacts": [["Nome", "Reparto", "E-mail", "Telefono"],
                     ["Design", "Supporto", "Vendite", "Sviluppo"]],
    },
    "pl": {
        "contract": ["Umowa o świadczenie usług",
                     "Niniejsza umowa zostaje zawarta między dwiema stronami wymienionymi poniżej.",
                     ["Dostawca udostępnia oprogramowanie opisane w załączniku na okres roku.",
                      "Płatność jest wymagalna w terminie trzydziestu dni od daty każdej faktury.",
                      "Każda ze stron może rozwiązać umowę z trzymiesięcznym wypowiedzeniem.",
                      "Zmiany umowy wymagają formy pisemnej.",
                      "Dostawca utrzymuje dostępność usługi w dni robocze.",
                      "Obie strony traktują jako poufne to, czego dowiedzą się o sobie nawzajem.",
                      "Obowiązuje prawo austriackie, a sądem właściwym jest sąd w Wiedniu.",
                      "Dostawca przechowuje kopię danych klienta przez trzydzieści dni.",
                      "Zgłoszenia do wsparcia są rozpatrywane w ciągu jednego dnia roboczego.",
                      "Klient wskazuje jedną osobę uprawnioną do zatwierdzania zmian.",
                      "Ceny obowiązują przez pierwszy rok i są weryfikowane każdej jesieni.",
                      "Żadna ze stron nie może przenieść umowy na osobę trzecią bez zgody.",
                      "Załącznik wymienia oprogramowanie objęte umową oraz wersję początkową.",
                      "Niniejsza umowa zastępuje wszystkie wcześniejsze ustalenia stron."]],
        "invoice": ["Faktura 2026-014", "Wystawiono 12 czerwca 2026", "Płatne w ciągu 30 dni", "Nabywca", "Wartość netto", "VAT 20%", "Do zapłaty", "Dziękujemy za współpracę.", "Ilość", "Cena jedn."],
        "contacts": [["Imię i nazwisko", "Dział", "E-mail", "Telefon"],
                     ["Projektowanie", "Wsparcie", "Sprzedaż", "Rozwój"]],
    },
    "pt-BR": {
        "contract": ["Contrato de serviço",
                     "Este contrato é celebrado entre as duas partes indicadas abaixo.",
                     ["O fornecedor disponibiliza por um ano o software descrito no anexo.",
                      "O pagamento vence em trinta dias a contar de cada fatura.",
                      "Qualquer parte pode encerrar o contrato com aviso prévio de três meses.",
                      "Alterações só são válidas por escrito.",
                      "O fornecedor mantém o serviço disponível em dias úteis.",
                      "As partes tratam como confidencial o que souberem uma da outra.",
                      "Aplica-se a lei austríaca e o foro competente é o de Viena.",
                      "O fornecedor mantém uma cópia dos dados do cliente por trinta dias.",
                      "Os chamados de suporte são respondidos em um dia útil.",
                      "O cliente indica uma pessoa autorizada a aprovar alterações.",
                      "Os preços ficam fixos no primeiro ano e são revisados todo outono.",
                      "Nenhuma parte pode transferir este contrato a terceiros sem consentimento.",
                      "O anexo lista o software abrangido e a versão inicial coberta.",
                      "Este contrato substitui qualquer acordo anterior entre as partes."]],
        "invoice": ["Fatura 2026-014", "Emitida em 12 de junho de 2026", "Vence em 30 dias", "Faturado para", "Subtotal", "Impostos 20%", "Valor a pagar", "Obrigado pela preferência.", "Qtd", "Preço unit."],
        "contacts": [["Nome", "Equipe", "E-mail", "Telefone"],
                     ["Design", "Suporte", "Vendas", "Engenharia"]],
    },
    "ru": {
        "contract": ["Договор оказания услуг",
                     "Настоящий договор заключён между двумя сторонами, указанными ниже.",
                     ["Исполнитель предоставляет программное обеспечение, указанное в приложении, сроком на один год.",
                      "Оплата производится в течение тридцати дней с даты счёта.",
                      "Каждая из сторон может расторгнуть договор, уведомив за три месяца.",
                      "Изменения действительны только в письменном виде.",
                      "Исполнитель обеспечивает доступность сервиса в рабочие дни.",
                      "Стороны сохраняют в тайне сведения, полученные друг о друге.",
                      "Применяется австрийское право, споры рассматривает суд Вены.",
                      "Исполнитель хранит резервную копию данных заказчика тридцать дней.",
                      "Обращения в поддержку рассматриваются в течение одного рабочего дня.",
                      "Заказчик назначает одного сотрудника, который вправе утверждать изменения.",
                      "Цены фиксируются на первый год и пересматриваются каждую осень.",
                      "Ни одна из сторон не вправе передать договор третьему лицу без согласия.",
                      "В приложении указано, какое программное обеспечение входит в договор и с какой версии.",
                      "Настоящий договор заменяет все прежние договорённости сторон."]],
        "invoice": ["Счёт 2026-014", "Выставлен 12 июня 2026 г.", "Оплата в течение 30 дней", "Плательщик", "Промежуточный итог", "НДС 20%", "К оплате", "Благодарим за сотрудничество.", "Кол-во", "Цена за ед."],
        "contacts": [["ФИО", "Отдел", "Почта", "Телефон"],
                     ["Дизайн", "Поддержка", "Продажи", "Разработка"]],
    },
    "tr": {
        "contract": ["Hizmet sözleşmesi",
                     "Bu sözleşme aşağıda belirtilen iki taraf arasında yapılmıştır.",
                     ["Tedarikçi, ekte tanımlanan yazılımı bir yıl boyunca sağlar.",
                      "Ödeme, her faturadan sonra otuz gün içinde yapılır.",
                      "Taraflardan biri sözleşmeyi üç ay önceden bildirerek sonlandırabilir.",
                      "Değişiklikler yalnızca yazılı olarak geçerlidir.",
                      "Tedarikçi hizmeti iş günlerinde erişilebilir tutar.",
                      "Taraflar birbirleri hakkında öğrendiklerini gizli tutar.",
                      "Avusturya hukuku uygulanır ve yetkili mahkeme Viyana'dır.",
                      "Tedarikçi, müşterinin verilerinin yedeğini otuz gün saklar.",
                      "Destek talepleri bir iş günü içinde yanıtlanır.",
                      "Müşteri, değişiklikleri onaylayabilecek bir kişi belirler.",
                      "Fiyatlar ilk yıl sabittir ve her sonbahar gözden geçirilir.",
                      "Hiçbir taraf sözleşmeyi onay almadan üçüncü kişiye devredemez.",
                      "Ek, kapsanan yazılımı ve geçerli olduğu sürümü listeler.",
                      "Bu sözleşme, taraflar arasındaki önceki tüm düzenlemelerin yerine geçer."]],
        "invoice": ["Fatura 2026-014", "12 Haziran 2026 tarihli", "30 gün içinde ödenir", "Alıcı", "Ara toplam", "KDV %20", "Ödenecek tutar", "İş birliğiniz için teşekkürler.", "Adet", "Birim fiyat"],
        "contacts": [["Ad Soyad", "Ekip", "E-posta", "Telefon"],
                     ["Tasarım", "Destek", "Satış", "Geliştirme"]],
    },
    "cs": {
        "contract": ["Smlouva o poskytování služeb",
                     "Tato smlouva se uzavírá mezi oběma níže uvedenými stranami.",
                     ["Dodavatel poskytuje software popsaný v příloze po dobu jednoho roku.",
                      "Platba je splatná do třiceti dnů od vystavení každé faktury.",
                      "Kterákoli strana může smlouvu ukončit s tříměsíční výpovědní lhůtou.",
                      "Změny této smlouvy jsou platné pouze písemně.",
                      "Dodavatel udržuje službu dostupnou v pracovní dny.",
                      "Obě strany zachovávají mlčenlivost o tom, co se o sobě dozvědí.",
                      "Řídí se rakouským právem; místně příslušný je soud ve Vídni.",
                      "Dodavatel uchovává zálohu dat zákazníka po dobu třiceti dnů.",
                      "Požadavky na podporu jsou zodpovězeny do jednoho pracovního dne.",
                      "Zákazník určí jednu osobu, která smí schvalovat změny.",
                      "Ceny platí první rok a každý podzim se přehodnocují.",
                      "Žádná strana nesmí smlouvu bez souhlasu postoupit třetí straně.",
                      "Příloha uvádí zahrnutý software a verzi, od které platí.",
                      "Tato smlouva nahrazuje všechna dřívější ujednání mezi stranami."]],
        "invoice": ["Faktura 2026-014", "Vystaveno 12. června 2026", "Splatnost do 30 dnů", "Odběratel", "Mezisoučet", "DPH 20 %", "K úhradě", "Děkujeme za spolupráci.", "Množ.", "Cena za ks"],
        "contacts": [["Jméno a příjmení", "Tým", "E-mail", "Telefon"],
                     ["Design", "Podpora", "Prodej", "Vývoj"]],
    },
    "et": {
        "contract": ["Teenuse osutamise leping",
                     "Käesolev leping sõlmitakse allpool nimetatud kahe poole vahel.",
                     ["Tarnija annab lisas kirjeldatud tarkvara kasutada üheks aastaks.",
                      "Makse tähtaeg on kolmkümmend päeva iga arve kuupäevast.",
                      "Kumbki pool võib lepingu lõpetada kolmekuulise etteteatamisega.",
                      "Lepingu muudatused kehtivad üksnes kirjalikult.",
                      "Tarnija hoiab teenuse tööpäevadel kättesaadavana.",
                      "Mõlemad pooled hoiavad saladuses selle, mida teineteise kohta teada saavad.",
                      "Kohaldatakse Austria õigust ja kohtualluvus on Viinis.",
                      "Tarnija säilitab kliendi andmete varukoopiat kolmkümmend päeva.",
                      "Kasutajatoe päringutele vastatakse ühe tööpäeva jooksul.",
                      "Klient nimetab ühe inimese, kes tohib muudatusi kinnitada.",
                      "Hinnad kehtivad esimesel aastal ja need vaadatakse üle igal sügisel.",
                      "Kumbki pool ei tohi lepingut ilma nõusolekuta kolmandale isikule anda.",
                      "Lisa loetleb hõlmatud tarkvara ja versiooni, millest alates see kehtib.",
                      "Käesolev leping asendab kõik varasemad poolte vahelised kokkulepped."]],
        "invoice": ["Arve 2026-014", "Väljastatud 12. juuni 2026", "Tasuda 30 päeva jooksul", "Maksja", "Vahesumma", "Käibemaks 20%", "Tasumisele kuulub", "Täname koostöö eest.", "Kogus", "Ühiku hind"],
        "contacts": [["Nimi", "Tiim", "E-post", "Telefon"],
                     ["Disain", "Kasutajatugi", "Müük", "Arendus"]],
    },
    "hi": {
        "contract": ["सेवा अनुबंध",
                     "यह अनुबंध नीचे नामित दोनों पक्षों के बीच किया जाता है।",
                     ["आपूर्तिकर्ता परिशिष्ट में वर्णित सॉफ़्टवेयर एक वर्ष के लिए उपलब्ध कराता है।",
                      "प्रत्येक बिल की तारीख़ से तीस दिनों के भीतर भुगतान देय है।",
                      "कोई भी पक्ष तीन महीने का नोटिस देकर यह अनुबंध समाप्त कर सकता है।",
                      "इस अनुबंध में बदलाव केवल लिखित रूप में मान्य हैं।",
                      "आपूर्तिकर्ता कार्यदिवसों में सेवा उपलब्ध रखता है।",
                      "दोनों पक्ष एक-दूसरे के बारे में जो जानते हैं उसे गोपनीय रखते हैं।",
                      "ऑस्ट्रिया का कानून लागू होगा और वियना की अदालत को अधिकार क्षेत्र प्राप्त है।",
                      "आपूर्तिकर्ता ग्राहक के डेटा का बैकअप तीस दिनों तक रखता है।",
                      "सहायता अनुरोधों का उत्तर एक कार्यदिवस के भीतर दिया जाता है।",
                      "ग्राहक एक व्यक्ति नामित करता है जो बदलावों को मंज़ूरी दे सकता है।",
                      "कीमतें पहले वर्ष स्थिर रहती हैं और हर पतझड़ में उनकी समीक्षा होती है।",
                      "कोई भी पक्ष सहमति के बिना यह अनुबंध किसी तीसरे को नहीं सौंप सकता।",
                      "परिशिष्ट में शामिल सॉफ़्टवेयर और वह संस्करण दर्ज है जिससे यह लागू होता है।",
                      "यह अनुबंध पक्षों के बीच हुए सभी पूर्व समझौतों का स्थान लेता है।"]],
        "invoice": ["बिल 2026-014", "जारी 12 जून 2026", "30 दिनों में देय", "प्राप्तकर्ता", "उप-योग", "कर 20%", "देय राशि", "आपके व्यवसाय के लिए धन्यवाद।", "मात्रा", "इकाई मूल्य"],
        "contacts": [["नाम", "टीम", "ईमेल", "फ़ोन"],
                     ["डिज़ाइन", "सहायता", "बिक्री", "इंजीनियरिंग"]],
    },
    "ja": {
        "contract": ["業務委託契約書",
                     "本契約は、以下に記載する二者の間で締結されます。",
                     ["受託者は、付録に記載したソフトウェアを1年間提供します。",
                      "支払いは、各請求書の発行から30日以内に行うものとします。",
                      "いずれの当事者も、3か月前の通知により本契約を終了できます。",
                      "本契約の変更は、書面による場合にかぎり有効です。",
                      "受託者は、営業日において本サービスを利用可能な状態に保ちます。",
                      "両当事者は、相手方について知り得たことを秘密として扱います。",
                      "本契約にはオーストリア法が適用され、ウィーンの裁判所を管轄とします。",
                      "受託者は、委託者のデータのバックアップを30日間保管します。",
                      "サポートへの問い合わせには、1営業日以内に回答します。",
                      "委託者は、変更を承認できる担当者を1名定めます。",
                      "価格は初年度は据え置き、毎年秋に見直します。",
                      "いずれの当事者も、同意なく本契約を第三者に譲渡できません。",
                      "付録には、対象となるソフトウェアと適用開始のバージョンを記載します。",
                      "本契約は、両当事者間のこれまでの取り決めのすべてに代わるものです。"]],
        "invoice": ["請求書 2026-014", "発行日 2026年6月12日", "お支払期限 30日以内", "請求先", "小計", "消費税 20%", "ご請求額", "お取引ありがとうございます。", "数量", "単価"],
        "contacts": [["氏名", "チーム", "メール", "電話"],
                     ["デザイン", "サポート", "営業", "開発"]],
    },
    "sv": {
        "contract": ["Tjänsteavtal",
                     "Detta avtal ingås mellan de två parter som anges nedan.",
                     ["Leverantören tillhandahåller den programvara som beskrivs i bilagan i ett år.",
                      "Betalning ska ske inom trettio dagar från varje faktura.",
                      "Vardera parten kan säga upp avtalet med tre månaders varsel.",
                      "Ändringar av detta avtal gäller endast skriftligen.",
                      "Leverantören håller tjänsten tillgänglig på vardagar.",
                      "Båda parter behandlar det de får veta om varandra som konfidentiellt.",
                      "Österrikisk rätt tillämpas, och domstolen i Wien är behörig.",
                      "Leverantören sparar en säkerhetskopia av kundens data i trettio dagar.",
                      "Supportärenden besvaras inom en arbetsdag.",
                      "Kunden utser en person som får godkänna ändringar.",
                      "Priserna gäller det första året och ses över varje höst.",
                      "Ingen part får överlåta detta avtal till tredje part utan samtycke.",
                      "Bilagan anger vilken programvara som omfattas och från vilken version.",
                      "Detta avtal ersätter alla tidigare överenskommelser mellan parterna."]],
        "invoice": ["Faktura 2026-014", "Utfärdad 12 juni 2026", "Betalas inom 30 dagar", "Faktureras till", "Delsumma", "Moms 20 %", "Att betala", "Tack för ditt förtroende.", "Antal", "Á-pris"],
        "contacts": [["Namn", "Team", "E-post", "Telefon"],
                     ["Design", "Support", "Försäljning", "Utveckling"]],
    },
    "zh": {
        "contract": ["服务合同",
                     "本合同由以下列明的双方签订。",
                     ["供方按附件所述提供软件，期限为一年。",
                      "每张发票开具后三十日内付款。",
                      "任何一方均可提前三个月通知终止本合同。",
                      "对本合同的变更，仅以书面形式为有效。",
                      "供方在工作日保持服务可用。",
                      "双方对从对方处知悉的信息负有保密义务。",
                      "本合同适用奥地利法律，由维也纳法院管辖。",
                      "供方为客户数据保留三十天的备份。",
                      "支持请求在一个工作日内答复。",
                      "客户指定一名有权批准变更的联系人。",
                      "价格在第一年内保持不变，此后每年秋季复核。",
                      "未经同意，任何一方不得将本合同转让给第三方。",
                      "附件列明所涵盖的软件及其适用的起始版本。",
                      "本合同取代双方此前的全部约定。"]],
        "invoice": ["发票 2026-014", "开具日期 2026年6月12日", "30 天内付款", "付款方", "小计", "税额 20%", "应付金额", "感谢您的惠顾。", "数量", "单价"],
        "contacts": [["姓名", "团队", "邮箱", "电话"],
                     ["设计", "支持", "销售", "研发"]],
    },
}

# Names are names in every language, so these are not translated.
PEOPLE = ["A. Bauer", "M. Rossi", "J. Novak", "L. Dubois", "S. Meyer", "K. Larsen"]


# --- the other formats ------------------------------------------------------
#
# The first screenshot is a folder, and an .odt beside an .xlsx beside a .pdf
# says what the app opens without a line of copy claiming it. Read by odrcore
# rather than by Word, so they carry the least markup that is still a valid
# package.

OOXML_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="{type}" Target="{target}"/>
</Relationships>
"""

WORD_MAIN = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"


def docx_parts(words: dict, others: dict) -> dict:
    """The Word file is the contract, not another copy of the report."""
    title, lead, clauses = others["contract"]

    def run(text: str, *, size: int, bold: bool = False, colour: str = "") -> str:
        marks = ("<w:b/>" if bold else "") + (f'<w:color w:val="{colour}"/>' if colour else "")

        return (
            f"<w:r><w:rPr>{marks}<w:sz w:val=\"{size}\"/></w:rPr>"
            f'<w:t xml:space="preserve">{escape(text)}</w:t></w:r>'
        )

    def para(runs: str, after: int) -> str:
        return f'<w:p><w:pPr><w:spacing w:after="{after}"/></w:pPr>{runs}</w:p>'

    # A clause is two sentences in one paragraph, numbered in line with the
    # first. A number on a line of its own above a single sentence reads as a
    # list of scraps rather than as a contract.
    paragraphs = [
        para(run(title, size=72, bold=True), 640),
        para(run(lead, size=22), 420),
    ]
    for number, index in enumerate(range(0, len(clauses) - 1, 2), start=1):
        body = " ".join(clauses[index:index + 2])
        paragraphs.append(
            para(
                run(f"{number}.  ", size=22, bold=True, colour=ACCENT[1:]) + run(body, size=22),
                300,
            )
        )

    # An empty paragraph between them, rather than trusting w:spacing: the
    # renderer sets the clauses flush against each other whatever `w:after`
    # says, and a contract whose clauses touch reads as one block of text.
    body = '<w:p/>'.join(paragraphs)

    return {
        "[Content_Types].xml": '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
        '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
        '<Default Extension="xml" ContentType="application/xml"/>'
        '<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-'
        'officedocument.wordprocessingml.document.main+xml"/>'
        '<Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-'
        'officedocument.wordprocessingml.styles+xml"/></Types>',
        "_rels/.rels": OOXML_RELS.format(type=WORD_MAIN, target="word/document.xml"),
        # Not optional. odrcore opens /word/styles.xml whether or not the
        # document has a style in it, and a package without one is not read as a
        # Word file at all: it falls through to the web view, which draws the
        # text with no page around it and offers neither search nor editing.
        "word/_rels/document.xml.rels": OOXML_RELS.format(
            type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles",
            target="styles.xml",
        ),
        "word/styles.xml": '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">'
        "<w:docDefaults><w:rPrDefault><w:rPr>"
        '<w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/><w:sz w:val="22"/>'
        "</w:rPr></w:rPrDefault>"
        '<w:pPrDefault><w:pPr><w:spacing w:after="160"/></w:pPr></w:pPrDefault>'
        "</w:docDefaults>"
        '<w:style w:type="paragraph" w:default="1" w:styleId="Normal">'
        '<w:name w:val="Normal"/></w:style>'
        "</w:styles>",
        "word/document.xml": '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">'
        # A4 with 2cm margins, in twentieths of a point. Without it there is no
        # page for odrcore to lay the text on, and the document is drawn as a
        # bare column of text rather than as a sheet of paper.
        f"<w:body>{body}"
        '<w:sectPr><w:pgSz w:w="11906" w:h="16838"/>'
        '<w:pgMar w:top="1134" w:right="1134" w:bottom="1134" w:left="1134"/>'
        "</w:sectPr></w:body></w:document>",
    }


def xlsx_parts(words: dict) -> dict:
    """A workbook of its own figures, so it is not the .ods twice."""
    head, body, foot = table(words, columns=2, rows=8, scale=3)
    rows_of = [head] + body + [foot]

    def cell(column: int, row: int, value) -> str:
        reference = f"{chr(ord('A') + column)}{row}"
        if isinstance(value, int):
            return f'<c r="{reference}"><v>{value}</v></c>'

        return f'<c r="{reference}" t="inlineStr"><is><t>{escape(value)}</t></is></c>'

    rows = "".join(
        f'<row r="{index + 1}">'
        + "".join(cell(column, index + 1, value) for column, value in enumerate(line))
        + "</row>"
        for index, line in enumerate(rows_of)
    )

    return {
        "[Content_Types].xml": '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
        '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
        '<Default Extension="xml" ContentType="application/xml"/>'
        '<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-'
        'officedocument.spreadsheetml.sheet.main+xml"/>'
        '<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-'
        'officedocument.spreadsheetml.worksheet+xml"/></Types>',
        "_rels/.rels": OOXML_RELS.format(type=WORD_MAIN, target="xl/workbook.xml"),
        "xl/_rels/workbook.xml.rels": OOXML_RELS.format(
            type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet",
            target="worksheets/sheet1.xml",
        ),
        "xl/workbook.xml": '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"'
        ' xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">'
        f'<sheets><sheet name="{escape(words["sheets"][0])}" sheetId="1" r:id="rId1"/></sheets></workbook>',
        "xl/worksheets/sheet1.xml": '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">'
        f"<sheetData>{rows}</sheetData></worksheet>",
    }


def pptx_parts(words: dict) -> dict:
    """One slide, titled and bulleted, so a deck opens on something."""
    drawing = 'xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"'
    presentation = 'xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"'

    def shape(identifier: int, name: str, box: str, lines: list, size: int) -> str:
        paragraphs = "".join(
            f'<a:p><a:r><a:rPr lang="en" sz="{size}" b="{1 if size > 2000 else 0}"/>'
            f"<a:t>{escape(line)}</a:t></a:r></a:p>"
            for line in lines
        )

        return (
            f'<p:sp><p:nvSpPr><p:cNvPr id="{identifier}" name="{name}"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>'
            f"<p:spPr><a:xfrm>{box}</a:xfrm>"
            '<a:prstGeom prst="rect"><a:avLst/></a:prstGeom></p:spPr>'
            f"<p:txBody><a:bodyPr/><a:lstStyle/>{paragraphs}</p:txBody></p:sp>"
        )

    slide = (
        f'<?xml version="1.0" encoding="UTF-8" standalone="yes"?><p:sld {presentation} {drawing}>'
        "<p:cSld><p:spTree>"
        '<p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/>'
        + shape(
            2,
            "Title",
            '<a:off x="685800" y="838200"/><a:ext cx="7772400" cy="1143000"/>',
            [words["slides"][0][0]],
            4000,
        )
        + shape(
            3,
            "Body",
            '<a:off x="685800" y="2286000"/><a:ext cx="7772400" cy="2743200"/>',
            words["slides"][0][1],
            2000,
        )
        + "</p:spTree></p:cSld></p:sld>"
    )

    return {
        "[Content_Types].xml": '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
        '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
        '<Default Extension="xml" ContentType="application/xml"/>'
        '<Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-'
        'officedocument.presentationml.presentation.main+xml"/>'
        '<Override PartName="/ppt/slides/slide1.xml" ContentType="application/vnd.openxmlformats-'
        'officedocument.presentationml.slide+xml"/></Types>',
        "_rels/.rels": OOXML_RELS.format(type=WORD_MAIN, target="ppt/presentation.xml"),
        "ppt/_rels/presentation.xml.rels": OOXML_RELS.format(
            type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide",
            target="slides/slide1.xml",
        ),
        "ppt/presentation.xml": f'<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        f'<p:presentation {presentation} xmlns:r="http://schemas.openxmlformats.org/'
        f'officeDocument/2006/relationships">'
        '<p:sldIdLst><p:sldId id="256" r:id="rId1"/></p:sldIdLst>'
        '<p:sldSz cx="9144000" cy="5143500"/><p:notesSz cx="6858000" cy="9144000"/></p:presentation>',
        "ppt/slides/slide1.xml": slide,
    }


# Helvetica's own character widths, in thousandths of the point size, so the pdf
# can be set the way a real one is: each word placed where it belongs rather
# than a whole line handed over as one run. It is also what lets the lines wrap
# where the text actually reaches the margin.
HELVETICA = {
    "regular": (
        "278 278 355 556 556 889 667 191 333 333 389 584 278 333 278 278 "
        "556 556 556 556 556 556 556 556 556 556 278 278 584 584 584 556 "
        "1015 667 667 722 722 667 611 778 722 278 500 667 556 833 722 778 "
        "667 778 722 667 611 722 667 944 667 667 611 278 278 278 469 556 "
        "333 556 556 500 556 556 278 556 556 222 222 500 222 833 556 556 "
        "556 556 333 500 278 556 500 722 500 500 500 334 260 334 584"
    ),
    "bold": (
        "278 333 474 556 556 889 722 238 333 333 389 584 278 333 278 278 "
        "556 556 556 556 556 556 556 556 556 556 333 333 584 584 584 611 "
        "975 722 722 722 722 667 611 778 722 278 556 722 611 833 722 778 "
        "667 778 722 667 611 722 667 944 667 667 611 333 278 333 584 556 "
        "333 556 611 556 611 556 333 611 611 278 278 556 278 889 611 611 "
        "611 611 389 556 333 611 556 778 556 556 500 389 280 389 584"
    ),
}

WIDTHS = {
    weight: {chr(32 + index): int(value) for index, value in enumerate(table.split())}
    for weight, table in HELVETICA.items()
}


def advance(text: str, weight: str, size: float) -> float:
    """How wide that text is set in Helvetica at that size.

    An accented letter is as wide as the letter it is built on - true across
    Helvetica's Latin range - so the table only has to hold the plain ones.
    """
    table = WIDTHS[weight]
    total = 0
    for character in text:
        width = table.get(character)
        if width is None:
            plain = unicodedata.normalize("NFD", character)[0]
            width = table.get(plain, 556)
        total += width

    return total * size / 1000


WINANSI = set(bytes(range(32, 256)).decode("cp1252", errors="ignore"))


def spellable(words: dict, others: dict) -> bool:
    """Whether Helvetica's encoding can write everything the invoice puts on the page.

    Everything, not a line or two of it: the check used to read the title and the
    closing only, which is a sample rather than an answer - a language those two
    happen to be spellable in can still hold a character further down that the
    encoding has no byte for, and that character reaches the page as mojibake.

    Seven of the fifteen locales fail this and take the English invoice: cs, pl
    and tr for a handful of letters, and hi, ja, ru and zh for their whole
    script. Fixing that means embedding a subset of a real font and writing the
    text as CIDs, which is a job of its own and not one to do inside a screenshot
    script - so it is written down here rather than left to be discovered in the
    store.
    """
    spoken = [words["item"], words["total"], words["title"], words["closing"]]
    spoken += words["periods"] + words["rows"] + others["invoice"]

    return all(character in WINANSI for line in spoken for character in line)


# A4 upright in points, with the same margin the ODF pages take.
PAGE = (595.0, 842.0)
MARGIN = 57.0


def pdf_bytes(words: dict, others: dict) -> bytes:
    """A one page PDF, written out by hand rather than through a library.

    An invoice, which is a page of placed labels and figures rather than of
    running prose: every cell is set where it belongs, so nothing has to be
    wrapped and `advance` is only asked how wide a number is.

    Helvetica and WinAnsi, so what it says is Latin text only - the languages
    this cannot spell get the English wording, which is also what the search
    screenshot then looks for.
    """
    latin = spellable(words, others)
    said = words if latin else WORDS["en"]

    def literal(text: str) -> str:
        return text.replace("\\", r"\\").replace("(", r"\(").replace(")", r"\)")

    invoice = others["invoice"] if latin else OTHERS["en"]["invoice"]
    number, issued, due, billed, subtotal, vat, due_label, thanks, quantity, unit = invoice
    head, body, foot = table(said, columns=1, rows=20)

    money = foot[-1]
    tax = round(money * 0.2)
    right = PAGE[0] - MARGIN

    drawn = []

    def put(text, x, y, weight="regular", size=10, align="left"):
        """One line, placed. Numbers are hung off the right, which is what makes
        a column of figures a column rather than a ragged list."""
        name = "F2" if weight == "bold" else "F1"
        at = x - advance(text, weight, size) if align == "right" else x
        drawn.append(f"BT /{name} {size:g} Tf {at:.1f} {y:.1f} Td ({literal(text)}) Tj ET")

    # the head: who it is from and when, against who it is to
    y = PAGE[1] - MARGIN - 26
    put(number, MARGIN, y, "bold", 20)
    put(issued, right, y, "regular", 10, "right")
    put(due, right, y - 14, "regular", 10, "right")

    y -= 46
    put(billed, MARGIN, y, "bold", 11)
    for line in ("Muster GmbH", "Praterstrasse 12", "1020 Wien"):
        y -= 14
        put(line, MARGIN, y)

    # the table, in four columns across the width
    columns = (MARGIN, MARGIN + 300, MARGIN + 390, right)
    y -= 34
    put(head[0], columns[0], y, "bold", 10)
    put(quantity, columns[1], y, "bold", 10, "right")
    put(unit, columns[2], y, "bold", 10, "right")
    put(head[-1], columns[3], y, "bold", 10, "right")

    for index, line in enumerate(body):
        count = index % 4 + 1
        amount = line[-1]
        y -= 15
        put(str(line[0]), columns[0], y)
        put(str(count), columns[1], y, align="right")
        put(f"{amount / count:.2f}", columns[2], y, align="right")
        put(str(amount), columns[3], y, align="right")

    y -= 24
    for label, value, weight in (
        (subtotal, money, "regular"), (vat, tax, "regular"), (due_label, money + tax, "bold")
    ):
        put(label, columns[2], y, weight, 10 if weight == "regular" else 12, "right")
        put(str(value), columns[3], y, weight, 10 if weight == "regular" else 12, "right")
        y -= 17

    y -= 12
    put(thanks, MARGIN, y)

    stream = ("\n".join(drawn) + "\n").encode("cp1252")

    objects = [
        b"<</Type/Catalog/Pages 2 0 R>>",
        b"<</Type/Pages/Kids[3 0 R]/Count 1>>",
        b"<</Type/Page/Parent 2 0 R/MediaBox[0 0 595 842]"
        b"/Resources<</Font<</F1 4 0 R/F2 6 0 R>>>>/Contents 5 0 R>>",
        b"<</Type/Font/Subtype/Type1/BaseFont/Helvetica/Encoding/WinAnsiEncoding>>",
        b"<</Length " + str(len(stream)).encode() + b">>\nstream\n" + stream + b"endstream",
        b"<</Type/Font/Subtype/Type1/BaseFont/Helvetica-Bold/Encoding/WinAnsiEncoding>>",
    ]

    out = bytearray(b"%PDF-1.4\n")
    offsets = []
    for number, body in enumerate(objects, start=1):
        offsets.append(len(out))
        out += f"{number} 0 obj\n".encode() + body + b"\nendobj\n"

    table_at = len(out)
    out += f"xref\n0 {len(objects) + 1}\n".encode() + b"0000000000 65535 f \n"
    for offset in offsets:
        out += f"{offset:010d} 00000 n \n".encode()
    out += f"trailer\n<</Size {len(objects) + 1}/Root 1 0 R>>\nstartxref\n{table_at}\n%%EOF\n".encode()

    return bytes(out)


def csv_text(words: dict, others: dict) -> str:
    """The contact list its name promises."""
    headers, roles = others["contacts"]
    lines = [",".join(headers)]
    for index, person in enumerate(PEOPLE):
        handle = person.split(". ")[-1].lower()
        lines.append(
            ",".join([person, roles[index % len(roles)], f"{handle}@example.org", f"+43 1 234 56{index}0"])
        )

    return "\n".join(lines) + "\n"


def txt_text(words: dict) -> str:
    """The notes: the report in plain text, with the deck's points under it."""
    lines = [words["title"], "=" * len(words["title"]), "", words["lead"], ""]
    for heading, paragraphs in words["sections"]:
        lines += [heading, "-" * len(heading), ""]
        for text in paragraphs:
            lines += [text, ""]
    for title, bullets in words["slides"]:
        lines += [title, "-" * len(title), ""]
        lines += [f"* {point}" for point in bullets]
        lines.append("")
    lines.append(words["closing"])

    return "\n".join(lines) + "\n"


# What the app asks the bundle for. The first three are the documents the
# screenshots open; the rest sit in the folder the first screenshot is of.
DOCUMENTS = {
    "text": ("odt", "application/vnd.oasis.opendocument.text", "document", report),
    "sheet": ("ods", "application/vnd.oasis.opendocument.spreadsheet", "document", sheet),
    "slides": ("odp", "application/vnd.oasis.opendocument.presentation", "slide", deck),
}

PACKAGES = {
    "word": ("docx", docx_parts),
    "cells": ("xlsx", xlsx_parts),
    "deck": ("pptx", pptx_parts),
}

PLAIN = {
    "rows": ("csv", csv_text),
    "notes": ("txt", txt_text),
}


def package(path: Path, parts: dict) -> None:
    """A zip of the given parts, reproducibly."""
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as archive:
        for name, text in parts.items():
            info = zipfile.ZipInfo(name, date_time=EPOCH)
            info.external_attr = 0o644 << 16
            archive.writestr(info, text, compress_type=zipfile.ZIP_DEFLATED)


def write(path: Path, mimetype: str, kind: str, content_xml: str) -> None:
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as package:
        def entry(name: str, text: str, stored: bool = False) -> None:
            info = zipfile.ZipInfo(name, date_time=EPOCH)
            info.external_attr = 0o644 << 16
            package.writestr(
                info, text, compress_type=zipfile.ZIP_STORED if stored else zipfile.ZIP_DEFLATED
            )

        # first and uncompressed, or the package is only recognised by sniffing
        entry("mimetype", mimetype, stored=True)
        entry("META-INF/manifest.xml", MANIFEST.format(mimetype=mimetype))
        entry("styles.xml", styles(kind))
        entry("content.xml", content_xml)


def main(argv=None) -> None:
    parser = argparse.ArgumentParser(description="Write the documents the store screenshots open.")
    parser.add_argument(
        "--language", action="append", choices=sorted(WORDS),
        help="only this language, repeatable; default is all of them. What to reach for when a "
             "change is worded in English first and the rest are to follow.")
    args = parser.parse_args(argv)

    languages = args.language or list(WORDS)

    SAMPLES.mkdir(parents=True, exist_ok=True)

    written = 0
    for language in languages:
        words = WORDS[language]
        for name, (extension, mimetype, kind, build) in DOCUMENTS.items():
            path = SAMPLES / f"sample-{name}-{language}.{extension}"
            write(path, mimetype, kind, build(words))
            written += 1

        others = OTHERS[language]

        for name, (extension, build) in PACKAGES.items():
            parts = build(words, others) if name == "word" else build(words)
            package(SAMPLES / f"sample-{name}-{language}.{extension}", parts)
            written += 1

        for name, (extension, build) in PLAIN.items():
            text = build(words, others) if name == "rows" else build(words)
            (SAMPLES / f"sample-{name}-{language}.{extension}").write_text(text, encoding="utf-8")
            written += 1

        (SAMPLES / f"sample-paper-{language}.pdf").write_bytes(pdf_bytes(words, others))
        written += 1

    # The locale table travels with the documents rather than being written out a
    # second time in `ScreenshotTests`: which locale reads which language's
    # documents is one fact, and a second copy of it can only disagree.
    written_in = set(store.LOCALES.values())
    if written_in != set(WORDS):
        raise SystemExit(
            f"the languages here and the ones store_screenshots.py photographs have parted "
            f"company: {sorted(written_in ^ set(WORDS))}"
        )

    details = {
        "locales": store.LOCALES,
        "languages": {
            language: {
                "files": FILE_NAMES[language]
                | {
                    key: FILLER_NAMES.get(language, {}).get(key, FILLER_NAMES["en"][key])
                    for key in FILLERS
                },
                "search": query(words, language),
            }
            for language, words in WORDS.items()
        },
    }
    (SAMPLES / "screenshot-names.json").write_text(
        json.dumps(details, ensure_ascii=False, indent=1, sort_keys=True) + "\n", encoding="utf-8"
    )

    print(f"wrote {written} documents in {len(languages)} languages to {SAMPLES}")


if __name__ == "__main__":
    main()
