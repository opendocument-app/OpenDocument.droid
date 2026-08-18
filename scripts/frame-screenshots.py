#!/usr/bin/env python3
#
# Puts the captured screenshots into the picture the store shows: the app on a
# phone, on a coloured ground, under a line of copy in that locale's language.
#
#   scripts/frame-screenshots.py                    frame the whole capture
#   scripts/frame-screenshots.py --locale en-US     one locale, for a look
#
# `fastlane android screenshots` takes the raw captures into fastlane/screenshots;
# this reads them and writes the framed set to fastlane/framed, which is what
# `scripts/store_screenshots.py` then checks and stages. The raw set is left
# alone, so a framing change costs a rerun of this and not of the emulators.
#
# Nothing here is drawn from an image file. Every part of the design is a
# rounded rectangle, a plain rectangle or a line of text, so it is all in
# `fastlane/frames/frames.json` and in the numbers below - which is also what
# lets one canvas size become another. The only asset is the font.
#
# Needs Pillow, which is the one thing in this repository's scripts that is not
# in the standard library:
#
#   python3 -m pip install Pillow

import argparse
import bisect
import functools
import json
import math
import shutil
import sys
from pathlib import Path

import store_screenshots as store

try:
    from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont
except ImportError:
    sys.exit("this needs Pillow: python3 -m pip install Pillow")

ROOT = Path(__file__).resolve().parent.parent
FRAMES = ROOT / "fastlane" / "frames"
CAPTURED = ROOT / "fastlane" / "screenshots"
FRAMED = ROOT / "fastlane" / "framed"

# The design, as fractions of the canvas rather than pixels, so that one canvas
# size becomes another and the same numbers describe both devices.
#
# How far down something sits is a fraction of the height; how big it is, and
# how far across, is a fraction of the width. So a taller canvas gives
# everything more room without stretching any of it.
#
# The canvas is a size of our own rather than the capture's: play refuses a
# screenshot whose long side is more than twice its short one, and a Pixel 9 Pro
# XL is 1344x2992 - 2.23:1 - before anything is drawn around it.
# `store_screenshots.CANVASES` is what the picture comes out as; the capture is a
# picture inside it, which leaves the device room to fit whole rather than be
# cropped past the buttons the app puts in the bottom right corner.
LAYOUT = {
    "phone": {
        "headline_top": 0.048,
        "headline_size": 0.058,        # before it is shrunk to fit
        "headline_width": 0.86,        # what it is shrunk to fit inside
        "headline_leading": 1.06,
        "screen_left": 0.308,
        "screen_top": 0.200,
        "screen_width": 0.600,         # as wide as it may be; `foot` is the other limit
        "foot": 0.036,                 # ground left under the device, of the height
        # A Pixel 9 Pro XL, from its published dimensions: a 1344px screen at
        # 486ppi is 70.2mm across a 76.6mm body, which leaves 3.2mm of aluminium
        # and black border on every side - half again what an iPhone carries -
        # and the display corner is a good deal tighter than Apple's.
        "bezel": 0.0268,               # screen edge to the outside of the body
        # Of that, less than half is the black mask and the rest is the polished
        # frame. Which way round this sits is most of whether the drawing reads
        # as a current phone.
        "rim": 0.42,                   # how much of the bezel is the black border
        "corner": 0.122,               # screen corner, of the screen's width
        "corner_easing": 2.2,          # near a circular arc - see squircle()
        "hole": 0.046,                 # the front camera, of the screen's width
        "hole_top": 0.028,
        # Nothing at all on the left edge: a Pixel keeps both its keys on the
        # right, and the tray is what the left carries - low on the edge, where
        # the device has it.
        "buttons": [],
        "tray": (0.620, 0.075),        # how far down the body, how long
        # The power key and the volume rocker, on the right edge: (how far down
        # the body, how long), both of the body's height.
        "buttons_right": [(0.175, 0.055), (0.250, 0.105)],
        "chip_top": 0.430,
        "chip_size": (0.230, 0.140),
        "chip_step": 0.157,
        "chip_text": 0.107,
        "dash_stroke": 0.0056,
        "dash_on": 0.0236,
        "dash_off": 0.0098,
        # The line crossing every screen: each picture takes it in at the height
        # the one before let it out at and hands it on, so no two screens carry
        # the same line and the gallery still reads as one. One height per seam,
        # which is one more than there are screens, and every one of them in the
        # band between the foot of the headline and the top of the device - the
        # only band that is neither written on nor covered up.
        "seams": [0.148, 0.176, 0.158, 0.180, 0.152, 0.172, 0.164],
        # What the line does between the two seams it has to join. "in" is the
        # height it arrived at and "out" the one it has to leave at; anything
        # else is a height of its own. One step each, at a different place, and
        # two of them dip a little way down first - only the left quarter is
        # free below the band, the device covers the rest, and a dip has to come
        # back up left of the body's edge (0.255 here) or it goes behind the
        # device and never comes out.
        "routes": [
            [(0.34, "in"), (0.34, "out")],
            [(0.17, "in"), (0.17, "out")],
            [(0.52, "in"), (0.52, "out")],
            [(0.10, "in"), (0.10, 0.245), (0.21, 0.245), (0.21, "out")],
            [(0.26, "in"), (0.26, "out"), (0.60, "out")],
            [(0.13, "in"), (0.13, 0.235), (0.23, 0.235), (0.23, "out")],
        ],
        "radii": [0.078, 0.066, 0.086, 0.062, 0.072, 0.070],
        # The lower line: in from off the canvas, around a corner and out again.
        # Points are (x, y) in canvas fractions and a point past 1 is off the
        # edge on purpose. A y of "chips" hangs the line off the top of the tabs
        # so it runs behind however many there are and comes out underneath -
        # anchored to a number, a screen with one tab starts it in mid air, so a
        # screen with no tabs takes one of the routes that does not need them.
        "decorations": [
            [("chips", "chips"), ("chips", 0.930), (0.55, 0.930)],
            [(-0.2, 0.700), (0.155, 0.700), (0.155, 0.930), (0.62, 0.930)],
            [("chips", "chips"), ("chips", 0.880), (-0.2, 0.880)],
            [(-0.2, 0.845), (0.185, 0.845), (0.185, 0.640), (0.58, 0.640)],
        ],
    },
    "tablet": {
        "headline_top": 0.060,
        "headline_size": 0.052,
        "headline_width": 0.80,
        "headline_leading": 1.06,
        "screen_left": 0.235,
        "screen_top": 0.240,
        "screen_width": 0.700,
        "foot": 0.036,
        "corner_easing": 2.2,
        # A Pixel Tablet: a 2560px screen at 276mm of body leaves about 14.5mm
        # of border on every side - four times the phone's, and the thing anyone
        # who has held one would name first. Its display corners are rounder
        # than a phone's are relative to the screen, and its keys are on the edge
        # that becomes the top in portrait, which this frame does not show. No
        # sim tray either: it is a wifi tablet.
        "bezel": 0.0470,
        # the other way round from the phone: a tablet's border really is mostly
        # black mask, with the frame a thin bright edge outside it
        "rim": 0.86,
        "corner": 0.030,
        "buttons": [],
        "chip_top": 0.430,
        "chip_size": (0.170, 0.0900),
        "chip_step": 0.1010,
        "chip_text": 0.080,
        "dash_stroke": 0.0040,
        "dash_on": 0.0147,
        "dash_off": 0.0061,
        "seams": [0.152, 0.186, 0.166, 0.192, 0.156, 0.180, 0.170],
        "routes": [
            [(0.30, "in"), (0.30, "out")],
            [(0.115, "in"), (0.115, "out")],
            [(0.46, "in"), (0.46, "out")],
            [(0.06, "in"), (0.06, 0.250), (0.15, 0.250), (0.15, "out")],
            [(0.20, "in"), (0.20, "out"), (0.54, "out")],
            [(0.08, "in"), (0.08, 0.240), (0.16, 0.240), (0.16, "out")],
        ],
        "radii": [0.060, 0.052, 0.068, 0.050, 0.056, 0.054],
        "decorations": [
            [("chips", "chips"), ("chips", 0.930), (0.52, 0.930)],
            [(-0.2, 0.620), (0.105, 0.620), (0.105, 0.930), (0.58, 0.930)],
            [("chips", "chips"), ("chips", 0.880), (-0.2, 0.880)],
            [(-0.2, 0.810), (0.125, 0.810), (0.125, 0.520), (0.54, 0.520)],
        ],
    },
}

# The device, which is drawn rather than photographed. The rim is read across the
# body's width: bright where the edge turns towards the light, dark on the flat.
BODY = "#08080a"
# How warm the frame is, as a multiplier per channel: a Pixel's aluminium is a
# warm grey. Small on purpose - past about a twentieth it stops being aluminium
# and starts being gold.
ALUMINIUM = (1.035, 1.0, 0.955)
# The metal the keys wear. They read as a step in the edge rather than as marks
# on it, which is what they are.
BUTTON = ("#d8d8d6", "#a9a9a7", "#c4c4c2")
# The sim tray, which is a seam rather than a key: the same metal, a shade darker
# so it reads as a line cut into the edge instead of one standing off it.
TRAY = ("#9a9a98", "#7c7c7a", "#909090")
GLASS = 96      # how brightly the screen's edge catches the light, of 255

# The shadow the device casts. Black rather than a colour of its own, which was
# mixed for the green ground and went muddy on the orange one, and offset down
# and right instead of sitting square behind the body, where the body covers it.
SHADOW = (0, 0, 0, 105)
SHADOW_OFFSET = (0.30, 0.65)    # of the bezel, across and down
SHADOW_BLUR = 0.85              # of the bezel


@functools.lru_cache(maxsize=None)
def design():
    text = json.loads((FRAMES / "frames.json").read_text())
    text.pop("_comment", None)
    return text


# The scripts Nunito cannot set, and where to find one that can.
#
# Nunito covers Latin and Cyrillic, which is eleven of the fifteen locales. It
# has no Devanagari and no CJK, and a font that has them is ten to sixteen
# megabytes per language - not something to put in a git history when every
# machine that runs this is an apt-get away from one. So these three are looked
# for on the system, and a machine without one is told what to install rather
# than handed a headline full of tofu.
#
# A candidate is (regular, bold, marker). The marker picks a face out of a .ttc
# by family name: the Noto collections hold every CJK language at once and which
# index is which is not fixed, so it is searched for rather than counted to.
NOTO_CJK = "/usr/share/fonts/opentype/noto/NotoSansCJK-%s.ttc"

# The last resort on a mac: Hiragino Sans and PingFang are downloadable rather than
# installed, so a machine that never asked for them has neither at a fixed path, while
# this one has been in /Library/Fonts since forever. One weight only, so the second
# headline line comes out light - a picture to look at, not one to ship.
ARIAL_UNICODE = "/Library/Fonts/Arial Unicode.ttf"

SCRIPTS = {
    "devanagari": (
        "fonts-noto-core on debian, or Devanagari Sangam MN on macos",
        [
            ("/usr/share/fonts/truetype/noto/NotoSansDevanagari-Regular.ttf",
             "/usr/share/fonts/truetype/noto/NotoSansDevanagari-Bold.ttf", None),
            ("/System/Library/Fonts/Supplemental/Devanagari Sangam MN.ttc",
             "/System/Library/Fonts/Supplemental/Devanagari Sangam MN.ttc", None),
            ("/System/Library/Fonts/Kohinoor.ttc", "/System/Library/Fonts/Kohinoor.ttc", None),
        ],
    ),
    "japanese": (
        "fonts-noto-cjk on debian, or Hiragino Sans on macos",
        [
            (NOTO_CJK % "Regular", NOTO_CJK % "Bold", "JP"),
            ("/System/Library/Fonts/Hiragino Sans W4.ttc",
             "/System/Library/Fonts/Hiragino Sans W7.ttc", None),
            (ARIAL_UNICODE, ARIAL_UNICODE, None),
        ],
    ),
    "chinese": (
        "fonts-noto-cjk on debian, or PingFang on macos",
        [
            (NOTO_CJK % "Regular", NOTO_CJK % "Bold", "SC"),
            ("/System/Library/Fonts/PingFang.ttc", "/System/Library/Fonts/PingFang.ttc", "SC"),
            ("/System/Library/Fonts/Hiragino Sans GB.ttc",
             "/System/Library/Fonts/Hiragino Sans GB.ttc", None),
            (ARIAL_UNICODE, ARIAL_UNICODE, None),
        ],
    ),
}

# The locales written in one of them. Everything else is Nunito.
WRITTEN_IN = {"hi-IN": "devanagari", "ja-JP": "japanese", "zh-CN": "chinese"}


def face_in(path, size, marker):
    """One face of a font file, picked out of a collection by family name.

    A .ttc holds several faces and the order is the font's business, not ours -
    so the index is searched for. Without a marker the first face is the file's
    own answer to what it is.
    """
    if marker is None:
        return ImageFont.truetype(path, size)

    for index in range(12):
        try:
            found = ImageFont.truetype(path, size, index=index)
        except (OSError, ValueError):
            break
        if marker in "".join(part or "" for part in found.getname()):
            return found

    raise OSError(f"{path} holds no {marker} face")


@functools.lru_cache(maxsize=None)
def font(size, weight, locale=None):
    """The headline font at one weight, in the script the language is written in.

    Nunito ships as a single variable file these days, so both weights come out
    of it by name; a system font is two files, or one that has only the weight it
    has, which is why a missing variation is not an error.
    """
    script = WRITTEN_IN.get(locale)
    if script is None:
        found = ImageFont.truetype(str(FRAMES / design()["font"]), size)
    else:
        wanted, candidates = SCRIPTS[script]
        found = None
        for regular, bold, marker in candidates:
            path = bold if weight == "Bold" else regular
            if not Path(path).exists():
                continue
            try:
                found = face_in(path, size, marker)
                break
            except OSError:
                continue

        if found is None:
            raise SystemExit(
                f"nothing on this machine can set {script}, which {locale}'s headline "
                f"is written in. Install {wanted}."
            )

    try:
        found.set_variation_by_name(weight)
    except (OSError, ValueError):
        # not a variable font: it is already the weight its file says it is
        pass

    return found


def gradient(size, top, bottom):
    """The ground: the same colour top to bottom, a little darker at the foot."""
    width, height = size
    strip = Image.new("RGB", (1, height))
    start = Image.new("RGB", (1, 1), top).getpixel((0, 0))
    end = Image.new("RGB", (1, 1), bottom).getpixel((0, 0))
    for y in range(height):
        share = y / max(1, height - 1)
        strip.putpixel((0, y), tuple(round(start[i] + (end[i] - start[i]) * share) for i in range(3)))

    return strip.resize((width, height)).convert("RGBA")


def rounded_path(points, radius, per_corner=24):
    """A polyline with its corners rounded off, as points to walk along."""
    walk = [points[0]]
    for before, corner, after in zip(points, points[1:], points[2:]):
        into = math.hypot(corner[0] - before[0], corner[1] - before[1])
        out = math.hypot(after[0] - corner[0], after[1] - corner[1])
        r = min(radius, into / 2, out / 2)
        start = (corner[0] + (before[0] - corner[0]) * r / into,
                 corner[1] + (before[1] - corner[1]) * r / into)
        end = (corner[0] + (after[0] - corner[0]) * r / out,
               corner[1] + (after[1] - corner[1]) * r / out)
        walk.append(start)
        for i in range(1, per_corner):
            t = i / per_corner
            # one quadratic bend, with the corner itself as the control point
            walk.append((
                (1 - t) ** 2 * start[0] + 2 * (1 - t) * t * corner[0] + t ** 2 * end[0],
                (1 - t) ** 2 * start[1] + 2 * (1 - t) * t * corner[1] + t ** 2 * end[1],
            ))
        walk.append(end)
    walk.append(points[-1])

    return walk


def dashed(canvas, points, stroke, on, off, colour=(255, 255, 255, 255), phase=0.0):
    """Lays dashes along a path, so a dash carries on around a corner.

    Counted out from the start of the path rather than accumulated as it walks,
    because a step that rounds to nothing next to a distance already travelled
    is a step that never arrives.
    """
    reached = [0.0]
    for before, after in zip(points, points[1:]):
        reached.append(reached[-1] + math.hypot(after[0] - before[0], after[1] - before[1]))
    total = reached[-1]
    if not total:
        return

    def at(distance):
        """The point that far along the path."""
        index = max(1, min(len(reached) - 1, bisect.bisect_left(reached, distance)))
        span = reached[index] - reached[index - 1]
        share = 0.0 if not span else (distance - reached[index - 1]) / span
        before, after = points[index - 1], points[index]

        return (before[0] + (after[0] - before[0]) * share,
                before[1] + (after[1] - before[1]) * share)

    draw = ImageDraw.Draw(canvas)
    width = max(1, round(stroke))

    period = on + off
    phase = phase % period
    for number in range(int((total + phase) // period) + 2):
        start = number * period - phase
        end = min(start + on, total)
        if start >= total:
            break
        start = max(start, 0.0)
        if start >= end:
            continue

        # the path's own corners inside this dash, so a dash that lands on a
        # bend is drawn bent rather than as a chord across it
        run = [at(start)]
        run += [point for point, so_far in zip(points, reached) if start < so_far < end]
        run.append(at(end))
        draw.line(run, fill=colour, width=width, joint="curve")


def squircle(box, radius, exponent=2.2, per_corner=40):
    """A rounded rectangle whose corners are superellipse quadrants.

    The exponent is what shape of phone this is, and the difference is not
    subtle at this size: a continuous curve, easing into the straight edge, takes
    around 5 and is an iPhone corner. A Pixel's is near enough a circular arc,
    which is 2 - 2.2 here, since a touch of easing is what the glass does where
    it meets the frame and an exact circle reads as a render.
    """
    x0, y0, x1, y1 = box
    r = min(radius, (x1 - x0) / 2, (y1 - y0) / 2)
    points = []

    # each corner as (centre, x sign, y sign), going clockwise from bottom right.
    # Two of the four are walked backwards, so that every quadrant leaves off
    # where the next one starts and the outline closes.
    for (cx, cy), sx, sy in (((x1 - r, y1 - r), 1, 1), ((x0 + r, y1 - r), -1, 1),
                             ((x0 + r, y0 + r), -1, -1), ((x1 - r, y0 + r), 1, -1)):
        for step in range(per_corner + 1):
            share = step / per_corner if sx * sy > 0 else 1 - step / per_corner
            angle = math.pi / 2 * share
            points.append((
                cx + sx * r * math.cos(angle) ** (2 / exponent),
                cy + sy * r * math.sin(angle) ** (2 / exponent),
            ))

    return points


def outset(points, distance):
    """The same outline, moved out by a fixed distance along its own normals.

    A squircle grown by raising its radius is not parallel to the one it grew
    from - the gap opens up around the corner and closes down the sides - so a
    bezel drawn that way is visibly fatter at the corners.
    """
    walked = list(zip(points, points[1:] + points[:1]))
    facing = 1.0 if sum(x0 * y1 - x1 * y0 for (x0, y0), (x1, y1) in walked) > 0 else -1.0
    moved = []

    for index, (x, y) in enumerate(points):
        (ax, ay), (bx, by) = points[index - 1], points[(index + 1) % len(points)]
        run, rise = bx - ax, by - ay
        length = math.hypot(run, rise) or 1.0
        moved.append((x + facing * rise / length * distance, y - facing * run / length * distance))

    return moved


def stencil(size, points, supersample=3):
    """An antialiased mask of one shape. Pillow's polygon has hard edges, so it
    is drawn large and shrunk, which is cheaper than it sounds on a mask."""
    big = Image.new("L", (size[0] * supersample, size[1] * supersample), 0)
    ImageDraw.Draw(big).polygon([(x * supersample, y * supersample) for x, y in points], fill=255)

    return big.resize(size, Image.LANCZOS)


def chamfer(share):
    """The metal's colour that far across the band, outside edge to black.

    A Pixel Pro's frame is polished aluminium: a narrow specular right at the
    outer edge, a hard drop behind it, a weaker sheen where the flat turns down
    to the glass, and dark where it meets the black surround. Brushed metal - one
    broad highlight two thirds of the way in - is somebody else's phone, and a
    flat fill is a grey stripe.
    """
    stops = ((0.00, 150), (0.10, 240), (0.22, 208), (0.42, 138), (0.66, 192), (0.85, 164), (1.00, 96))
    place = bisect.bisect_right([at for at, _ in stops], share)
    if place == 0:
        level = stops[0][1]
    elif place == len(stops):
        level = stops[-1][1]
    else:
        (before, low), (after, high) = stops[place - 1], stops[place]
        level = low + (high - low) * (share - before) / (after - before)

    return tuple(min(255, round(level * warm)) for warm in ALUMINIUM)


def brushed(size, colours):
    """The rim: a metal that catches the light differently across its width."""
    width, height = size
    strip = Image.new("RGB", (len(colours), 1))
    for index, colour in enumerate(colours):
        strip.putpixel((index, 0), Image.new("RGB", (1, 1), colour).getpixel((0, 0)))

    return strip.resize((width, height), Image.BICUBIC)


def crossing(layout, order, size):
    """The line this screen hands on: in at one height, out at the next."""
    width, height = size
    seams = layout["seams"]
    enters = seams[order % len(seams)] * height
    leaves = seams[(order + 1) % len(seams)] * height
    route = layout["routes"][order % len(layout["routes"])]

    def down(y):
        return enters if y == "in" else leaves if y == "out" else y * height

    return (
        [(-0.2 * width, enters)]
        + [(x * width, down(y)) for x, y in route]
        + [(1.2 * width, leaves)]
    )


def walked(points):
    """How far a path runs, so the next one can pick the dashes up."""
    return sum(
        math.hypot(after[0] - before[0], after[1] - before[1])
        for before, after in zip(points, points[1:])
    )


def device_body(canvas, shot, layout):
    """The device: the capture behind glass, in a metal body.

    Drawn rather than pasted from a mockup, so it is the shape of whatever was
    captured - a downloaded frame is the wrong shape for the next device.

    Built in its own image and composited once, so the parts can be masked
    against each other without the ground showing through the seams.
    """
    width, height = canvas.size
    bezel = layout["bezel"] * width          # screen edge to the outside of the body
    rim = bezel * layout["rim"]              # how much of that is metal

    left, top = layout["screen_left"] * width, layout["screen_top"] * height

    # Two limits rather than one fraction: `screen_width` is as wide as it may
    # be, and `foot` is how much ground has to be left under it. Sized by the
    # fraction alone, a device a little taller than the one the number was picked
    # for runs its bottom rim off the canvas and a shorter one leaves a stripe of
    # ground - neither of which is a decision anybody made.
    standing = (height - layout["foot"] * height) - top - bezel
    screen_width = min(layout["screen_width"] * width, standing * shot.width / shot.height)
    screen_height = screen_width * shot.height / shot.width

    screen = (left, top, left + screen_width, top + screen_height)
    corner = layout["corner"] * screen_width

    body = (screen[0] - bezel, screen[1] - bezel, screen[2] + bezel, screen[3] + bezel)

    # its own canvas, with room either side for the keys that stand proud
    margin = round(bezel * 3)
    origin = (round(body[0]) - margin, round(body[1]) - margin)
    size = (round(body[2]) - origin[0] + margin, round(body[3]) - origin[1] + margin)
    here = lambda box: tuple(v - origin[i % 2] for i, v in enumerate(box))

    device = Image.new("RGBA", size, (0, 0, 0, 0))

    # the buttons first, so the body's own edge covers where they meet it
    stand = screen_width * 0.0061          # how far a key stands proud, about 2.7pt
    tall_as = body[3] - body[1]

    def along(edge, keys, proud):
        marks = Image.new("L", size, 0)
        draw = ImageDraw.Draw(marks)
        for at_height, tall in keys:
            y = here(body)[1] + tall_as * at_height
            draw.rounded_rectangle((edge - proud, y, edge + proud, y + tall_as * tall),
                                   radius=proud * 0.55, fill=255)

        return marks

    keys = Image.new("L", size, 0)
    for edge, side in ((here(body)[0], layout["buttons"]),
                       (here(body)[2], layout.get("buttons_right", []))):
        keys = ImageChops.lighter(keys, along(edge, side, stand))
    device.paste(brushed(size, BUTTON), (0, 0), keys)

    # The sim tray, which sits flush rather than proud - it is a seam in the edge,
    # so it is drawn narrower and darker than a key and does not stand off the body
    if layout.get("tray"):
        device.paste(
            brushed(size, TRAY),
            (0, 0),
            along(here(body)[0], [layout["tray"]], stand * 0.45),
        )

    # Every edge is the screen's own outline moved out, so the black border and
    # the metal around it are the same width the whole way round - which is what
    # they are on the device, and not what a bigger squircle would give.
    face = squircle(here(screen), corner, layout["corner_easing"])
    outline = outset(face, bezel)

    # The metal, lit across the band's own width rather than the body's: the
    # band is filled as rings, each the colour ``chamfer`` gives for how far in
    # it sits, so the highlight follows the edge the whole way round.
    band = bezel - rim
    lit = Image.new("RGB", size, chamfer(1.0))
    rings = ImageDraw.Draw(lit)
    steps = max(8, round(band))
    for step in range(steps + 1):
        share = step / steps
        rings.polygon(outset(face, bezel - band * share), fill=chamfer(share))

    device.paste(lit, (0, 0), stencil(size, outline))

    # the black surround the glass sits in, and then the glass
    device.paste(Image.new("RGB", size, BODY), (0, 0), stencil(size, outset(face, rim)))

    fitted = shot.resize((round(screen_width), round(screen_height)), Image.LANCZOS).convert("RGBA")
    inside = here(screen)
    device.paste(fitted, (round(inside[0]), round(inside[1])),
                 stencil(size, face).crop(
                     (round(inside[0]), round(inside[1]),
                      round(inside[0]) + fitted.width, round(inside[1]) + fitted.height)))

    # The hairline where the glass meets the surround, which a real device
    # catches the light along. Without it a screen that is dark at the top runs
    # into the black bezel and the two read as one fat border.
    hair = max(1.0, screen_width * 0.0012)
    halo = ImageChops.subtract(
        stencil(size, face), stencil(size, outset(face, -hair))
    ).point(lambda level: level * GLASS // 255)
    device.paste(Image.new("RGB", size, "white"), (0, 0), halo)

    # The hole the front camera sits in, in the gap the status bar leaves for
    # it. A circle rather than a pill, which is a Pixel and not an iPhone.
    if layout.get("hole"):
        across = layout["hole"] * screen_width
        middle = (inside[0] + inside[2]) / 2
        hole_top = inside[1] + layout["hole_top"] * screen_width
        ImageDraw.Draw(device).ellipse(
            (middle - across / 2, hole_top, middle + across / 2, hole_top + across), fill=BODY
        )

    shadow = Image.new("RGBA", size, (0, 0, 0, 0))
    shadow.paste(Image.new("RGB", size, SHADOW[:3]), (0, 0),
                 stencil(size, outline).point(lambda v: v * SHADOW[3] // 255))
    canvas.alpha_composite(
        shadow.filter(ImageFilter.GaussianBlur(bezel * SHADOW_BLUR)),
        (origin[0] + round(bezel * SHADOW_OFFSET[0]), origin[1] + round(bezel * SHADOW_OFFSET[1])))
    canvas.alpha_composite(device, origin)


def headline(canvas, lines, layout, locale):
    """Two lines, light over bold, centred and shrunk until they fit.

    Fitted rather than set at a fixed size because the same sentence is a third
    longer in German than in English, and a line that runs off the picture is
    worse than one set a little smaller.
    """
    width, height = canvas.size
    size = round(layout["headline_size"] * width)
    allowed = layout["headline_width"] * width
    weights = ("Regular", "Bold")
    draw = ImageDraw.Draw(canvas)

    faces = [font(size, weight, locale) for weight in weights]
    while size > 8:
        if max(draw.textlength(line, font=face) for line, face in zip(lines, faces)) <= allowed:
            break
        size -= 2
        faces = [font(size, weight, locale) for weight in weights]

    leading = size * layout["headline_leading"]
    y = layout["headline_top"] * height
    for line, face in zip(lines, faces):
        draw.text((width / 2, y), line, font=face, fill="white", anchor="ma")
        y += leading


def chips(canvas, names, palette, layout):
    """The odt/ods/odp tabs, running off the left edge as the design has them."""
    width, height = canvas.size
    least, chip_height = (share * width for share in layout["chip_size"])
    face = font(round(layout["chip_text"] * width), "Bold")
    draw = ImageDraw.Draw(canvas)

    # As wide as the longest word in the whole design needs, and no narrower
    # than the design's own tab: measured across every format rather than the
    # two or three on this screen, so the tabs are one length through the
    # gallery rather than stepping in and out as the reader swipes.
    padding = layout["chip_text"] * width * 0.42
    chip_width = max([least] + [draw.textlength(name, font=face) + 2 * padding for name in palette])

    for index, name in enumerate(names):
        top = layout["chip_top"] * height + layout["chip_step"] * width * index
        draw.rectangle((-2, top, chip_width, top + chip_height), fill=palette[name])
        draw.text((chip_width / 2, top + chip_height / 2), name, font=face, fill="white", anchor="mm")

    return chip_width


def frame(shot, device, screen, locale, spec, order=0):
    """One picture: ground, decorations, device, tabs, headline."""
    if device not in LAYOUT:
        raise ValueError(f"no layout for {device} - one of {', '.join(LAYOUT)}")

    layout = LAYOUT[device]
    size = store.CANVASES[device]
    width, height = size
    canvas = gradient(size, *spec["backgrounds"][screen["background"]])

    on, off = layout["dash_on"] * width, layout["dash_off"] * width
    stroke = layout["dash_stroke"] * width
    radius = layout["radii"][order % len(layout["radii"])] * width

    # The crossing line, and the dash pattern picked up where the screens before
    # it left off, so the dashes carry on across the gallery rather than
    # restarting at every picture.
    before = sum(walked(crossing(layout, index, size)) for index in range(order))
    dashed(
        canvas, rounded_path(crossing(layout, order, size), radius), stroke, on, off, phase=before
    )

    lower = layout["decorations"][order % len(layout["decorations"])]

    # a line hanging off tabs that are not there reads as a line starting in mid
    # air, so a screen without them takes one that comes in from the edge
    if not screen["chips"] and any("chips" in point for point in lower):
        lower = next(
            points for points in layout["decorations"] if not any("chips" in p for p in points)
        )

    # "chips" is the middle of the tabs, so the line runs behind however many
    # there are and comes out underneath
    placed = [
        (layout["chip_size"][0] / 2 if x == "chips" else x,
         layout["chip_top"] if y == "chips" else y)
        for x, y in lower
    ]
    dashed(
        canvas,
        rounded_path([(x * width, y * height) for x, y in placed], radius),
        stroke, on, off,
    )

    device_body(canvas, shot, layout)
    chips(canvas, screen["chips"], spec["chips"], layout)
    headline(canvas, copy(screen, locale), layout, locale)

    return canvas.convert("RGB")


def copy(screen, locale):
    """This screen's two lines in that language, or the English if it has none."""
    lines = screen["headline"].get(locale) or screen["headline"][store.FALLBACK]

    return lines


def main(argv=None):
    parser = argparse.ArgumentParser(description="Frame the captured play store screenshots.")
    parser.add_argument("--captured", metavar="DIR", default=CAPTURED,
                        help=f"where the capture run wrote (default {CAPTURED.relative_to(ROOT)})")
    parser.add_argument("--framed", metavar="DIR", default=FRAMED,
                        help=f"where to write the framed set (default {FRAMED.relative_to(ROOT)})")
    parser.add_argument("--locale", action="append",
                        help="only this locale, repeatable; default is everything captured")
    args = parser.parse_args(argv)

    spec = design()
    screens = {screen["name"]: screen for screen in spec["screens"]}
    captured, framed = Path(args.captured), Path(args.framed)

    wanted = args.locale or store.languages()
    written = 0

    for locale in wanted:
        folder = captured / locale
        if not folder.is_dir():
            print(f"{locale}: no {folder}", file=sys.stderr)
            continue

        # emptied rather than written over, so a screen that was renamed does
        # not leave yesterday's picture behind for the release to find
        out = framed / locale
        shutil.rmtree(out, ignore_errors=True)
        out.mkdir(parents=True, exist_ok=True)

        for path in sorted(folder.glob("*.png")):
            # the capture run writes the device into the name, being the only
            # thing that knows which emulator it was driving
            device, name = store.named(path.stem)
            if device is None or name not in screens:
                print(f"{locale}: skipping {path.name}, which no screen is named after",
                      file=sys.stderr)
                continue

            with Image.open(path) as shot:
                picture = frame(
                    shot.convert("RGB"), device, screens[name], locale, spec,
                    order=list(screens).index(name),
                )

            picture.save(out / path.name)
            written += 1

    print(f"framed {written} screenshots into {framed}")

    return 0 if written else 1


if __name__ == "__main__":
    sys.exit(main())
