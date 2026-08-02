#!/usr/bin/env python3
#
# Lays sets of screen-tour shots out as a PDF: a contact sheet, then one page per
# screen with the same screen from each set beside itself.
#
# What each page says is prose, and prose does not belong in code - it lives in a
# story file (story.json next to this one by default), which is the thing to edit
# when the design changes.
#
# Usage: tools/screen-tour/collage.py --set main=DIR --set redesign=DIR
# See README.md next to this file.

import argparse
import json
import os
import sys

try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError:
    sys.exit("this needs Pillow: pip install pillow")

HERE = os.path.dirname(os.path.abspath(__file__))

# A4, in points. Everything below is written in points and multiplied by the
# scale the requested dpi asks for, so the layout does not change when the
# resolution does - see the note about pixelation in README.md.
PAGE_W, PAGE_H = 595, 842
MARGIN = 38

BG = (255, 255, 255)
INK = (24, 24, 27)
MUTED = (110, 110, 118)
RULE = (222, 222, 228)
ACCENT = (0, 105, 137)

FONT_CANDIDATES = {
    "regular": [
        "/System/Library/Fonts/Supplemental/Arial.ttf",
        "/Library/Fonts/Arial.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
        "/usr/share/fonts/TTF/DejaVuSans.ttf",
    ],
    "bold": [
        "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
        "/Library/Fonts/Arial Bold.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf",
        "/usr/share/fonts/TTF/DejaVuSans-Bold.ttf",
    ],
}
FALLBACK = "/System/Library/Fonts/Helvetica.ttc"


class Sheet:
    """A page being drawn, in points, at whatever scale the dpi asked for."""

    def __init__(self, dpi):
        self.scale = dpi / 72.0
        self.page = Image.new("RGB", (self.px(PAGE_W), self.px(PAGE_H)), BG)
        self.draw = ImageDraw.Draw(self.page)

    def px(self, points):
        return int(round(points * self.scale))

    def font(self, weight, size):
        for path in FONT_CANDIDATES[weight] + [FALLBACK]:
            if os.path.exists(path):
                return ImageFont.truetype(path, self.px(size))

        # load_default() ignores the size, so at 300 dpi every label would come out as
        # unreadable specks. Better to say so than to hand back a PDF nobody can read.
        raise SystemExit(
            f"no {weight} font found. Tried:\n  "
            + "\n  ".join(FONT_CANDIDATES[weight] + [FALLBACK])
            + "\nInstall one (on debian: apt install fonts-dejavu-core) or add its path "
            "to FONT_CANDIDATES."
        )

    def text(self, x, y, message, font, fill):
        self.draw.text((self.px(x), self.px(y)), message, font=font, fill=fill)

    def rule(self, x, y, width):
        self.draw.line(
            (self.px(x), self.px(y), self.px(x + width), self.px(y)),
            fill=RULE,
            width=max(1, self.px(1)),
        )

    def paste(self, image, x, y):
        self.page.paste(image, (self.px(x), self.px(y)))

    def wrap(self, message, font, width):
        """[message] broken into lines that fit [width] points."""
        limit = self.px(width)
        words, lines, current = message.split(), [], ""
        for word in words:
            trial = (current + " " + word).strip()
            if self.draw.textlength(trial, font=font) <= limit:
                current = trial
            else:
                if current:
                    lines.append(current)
                current = word
        if current:
            lines.append(current)

        return lines

    def paragraph(self, x, y, message, font, fill, width, leading):
        for line in self.wrap(message, font, width):
            self.text(x, y, line, font, fill)
            y += leading

        return y

    def framed(self, path, height):
        """A screenshot scaled to [height] points, with a hairline around it."""
        image = Image.open(path)
        target_h = self.px(height)
        target_w = int(image.width * target_h / image.height)
        scaled = image.resize((target_w, target_h), Image.LANCZOS).convert("RGB")

        edge = max(1, self.px(0.75))
        framed = Image.new("RGB", (target_w + edge * 2, target_h + edge * 2), (200, 200, 206))
        framed.paste(scaled, (edge, edge))

        return framed


def shot_path(sets, name, shot):
    path = os.path.join(sets[name], f"{shot}.png")
    if not os.path.exists(path):
        sys.exit(f"no {shot}.png in the {name} set ({sets[name]})")

    return path


def cover(story, sets, dpi):
    sheet = Sheet(dpi)
    title = sheet.font("bold", 30)
    heading = sheet.font("bold", 12)
    body = sheet.font("regular", 13)
    small = sheet.font("regular", 10)
    tiny = sheet.font("regular", 9)

    sheet.text(MARGIN, MARGIN + 8, story["title"], title, INK)
    sheet.text(MARGIN, MARGIN + 46, story["subtitle"], title, ACCENT)
    sheet.text(MARGIN, MARGIN + 92, story["note"], body, MUTED)
    sheet.rule(MARGIN, MARGIN + 122, PAGE_W - MARGIN * 2)

    # the contact sheet: every page that compares two sets, one column each
    pairs = [page for page in story["pages"] if len(page["columns"]) == 2]
    gap = 8
    tile_w = (PAGE_W - MARGIN * 2 - gap * (len(pairs) - 1)) / len(pairs)

    sample = Image.open(shot_path(sets, pairs[0]["columns"][0]["set"], pairs[0]["shot"]))
    tile_h = tile_w * sample.height / sample.width

    rows = [pairs[0]["columns"][0]["set"], pairs[0]["columns"][1]["set"]]
    y_top = MARGIN + 172
    tops = [y_top, y_top + tile_h + 34]

    for name, top in zip(rows, tops):
        sheet.text(MARGIN, top - 16, name.upper(), heading, ACCENT)

    for index, page in enumerate(pairs):
        x = MARGIN + index * (tile_w + gap)
        for name, top in zip(rows, tops):
            image = Image.open(shot_path(sets, name, page["shot"]))
            image = image.resize(
                (sheet.px(tile_w), sheet.px(tile_h)), Image.LANCZOS
            ).convert("RGB")
            sheet.paste(image, x, top)
            sheet.draw.rectangle(
                (
                    sheet.px(x),
                    sheet.px(top),
                    sheet.px(x + tile_w) - 1,
                    sheet.px(top + tile_h) - 1,
                ),
                outline=(205, 205, 212),
            )

        caption = page["title"].split("·", 1)[-1].strip()
        sheet.paragraph(x, tops[1] + tile_h + 6, caption, tiny, MUTED, tile_w, 11)

    y = tops[1] + tile_h + 52
    y = sheet.paragraph(MARGIN, y, story["footer"], small, MUTED, PAGE_W - MARGIN * 2, 14)

    summary = story.get("summary")
    if summary:
        y += 8
        sheet.rule(MARGIN, y, PAGE_W - MARGIN * 2)
        y += 10
        sheet.text(MARGIN, y, summary["heading"], heading, ACCENT)
        y += 20
        for line in summary["lines"]:
            indented = line.startswith(" ")
            sheet.text(
                MARGIN + (10 if indented else 0),
                y,
                line.strip() if indented else "•  " + line,
                small,
                INK,
            )
            y += 14

    return sheet.page


def pair_page(page, sets, dpi):
    sheet = Sheet(dpi)
    title = sheet.font("bold", 22)
    subtitle = sheet.font("regular", 13)
    label = sheet.font("bold", 14)
    caption = sheet.font("regular", 11)

    sheet.text(MARGIN, MARGIN, page["title"], title, INK)
    sheet.text(MARGIN, MARGIN + 31, page["subtitle"], subtitle, MUTED)
    sheet.rule(MARGIN, MARGIN + 59, PAGE_W - MARGIN * 2)

    gutter = 29
    column_w = (PAGE_W - MARGIN * 2 - gutter) / 2
    shot_h = 566

    images = [
        sheet.framed(shot_path(sets, column["set"], page["shot"]), shot_h)
        for column in page["columns"]
    ]
    shot_w = images[0].width / sheet.scale
    xs = [
        MARGIN + (column_w - shot_w) / 2,
        MARGIN + column_w + gutter + (column_w - shot_w) / 2,
    ]

    for x, column in zip(xs, page["columns"]):
        sheet.text(x, MARGIN + 76, column["set"], label, ACCENT)

    top = MARGIN + 100
    for x, image in zip(xs, images):
        sheet.paste(image, x, top)

    for x, column in zip(xs, page["columns"]):
        sheet.paragraph(x, top + shot_h + 14, column["caption"], caption, INK, column_w, 15)

    return sheet.page


def solo_page(page, sets, dpi):
    """One screen, with room beside it for more than a caption."""
    sheet = Sheet(dpi)
    title = sheet.font("bold", 22)
    subtitle = sheet.font("regular", 13)
    label = sheet.font("bold", 14)
    body = sheet.font("regular", 11)

    sheet.text(MARGIN, MARGIN, page["title"], title, INK)
    sheet.text(MARGIN, MARGIN + 31, page["subtitle"], subtitle, MUTED)
    sheet.rule(MARGIN, MARGIN + 59, PAGE_W - MARGIN * 2)

    column = page["columns"][0]
    image = sheet.framed(shot_path(sets, column["set"], page["shot"]), 624)
    sheet.text(MARGIN, MARGIN + 76, column["set"], label, ACCENT)
    sheet.paste(image, MARGIN, MARGIN + 100)

    x = MARGIN + image.width / sheet.scale + 29
    width = PAGE_W - MARGIN - x
    y = MARGIN + 100
    for paragraph in column["caption"].split("\n\n"):
        y = sheet.paragraph(x, y, paragraph, body, INK, width, 16) + 10

    return sheet.page


def main():
    parser = argparse.ArgumentParser(description="Lay screen-tour shots out as a PDF.")
    parser.add_argument(
        "--set",
        dest="sets",
        action="append",
        required=True,
        metavar="NAME=DIR",
        help="a named set of shots, repeatable, e.g. --set main=build/tour/main",
    )
    parser.add_argument(
        "--story",
        default=os.path.join(HERE, "story.json"),
        help="what each page says (default: story.json next to this script)",
    )
    parser.add_argument("--out", required=True, help="the pdf to write")
    parser.add_argument(
        "--dpi",
        type=int,
        default=300,
        help="how finely the pages are rendered (default: 300)",
    )
    parser.add_argument("--pngs", help="also write each page as a png into this directory")
    args = parser.parse_args()

    sets = {}
    for entry in args.sets:
        if "=" not in entry:
            sys.exit(f"--set wants NAME=DIR, got {entry}")
        name, path = entry.split("=", 1)
        sets[name] = path

    with open(args.story, encoding="utf-8") as handle:
        story = json.load(handle)

    pages = [cover(story, sets, args.dpi)]
    for page in story["pages"]:
        builder = pair_page if len(page["columns"]) == 2 else solo_page
        pages.append(builder(page, sets, args.dpi))

    os.makedirs(os.path.dirname(os.path.abspath(args.out)) or ".", exist_ok=True)
    pages[0].save(
        args.out, save_all=True, append_images=pages[1:], resolution=float(args.dpi)
    )

    if args.pngs:
        os.makedirs(args.pngs, exist_ok=True)
        for index, page in enumerate(pages):
            page.save(os.path.join(args.pngs, f"page-{index:02d}.png"))

    print(f"{args.out}  ({len(pages)} pages at {args.dpi} dpi)")


if __name__ == "__main__":
    sys.exit(main())
