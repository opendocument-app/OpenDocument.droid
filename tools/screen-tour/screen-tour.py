#!/usr/bin/env python3
#
# Walks a build through the handful of screens that carry its design and
# photographs each one, so two branches can be put side by side.
#
# Everything here goes through the app the way a user does - the system picker,
# the app's own buttons - because the point is to photograph what a user sees.
# Nothing is seeded straight into the app's storage: a recent document written
# by hand would carry a uri the app holds no grant for, and would be dropped on
# the next launch anyway.
#
# Usage: tools/screen-tour/screen-tour.py [--out DIR] [--install APK] ...
# See README.md next to this file.

import argparse
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))

# the documents the tour opens, in the order it opens them - so the last one is
# the one on screen for the document, menu and search shots, and the recents
# list reads bottom up. they come from the instrumented tests' assets, which are
# small, in the repo, and cover three different formats.
DOCUMENTS = ["spreadsheet-test.ods", "style-various-1.docx", "test.odt"]

# where the tour drops them. shared storage rather than the app's own directory:
# these are opened through the system picker, which is what grants the app the
# uri - see the note in README.md about how this differs from render-sweep.
DEVICE_DIR = "/sdcard/Download"

SEARCH_TERM = "document"


class Device:
    """adb plus enough of uiautomator to find something and tap it."""

    def __init__(self, serial, verbose):
        self.adb = [os.path.join(sdk_root(), "platform-tools", "adb")]
        if serial:
            self.adb += ["-s", serial]
        self.verbose = verbose
        self.size = None

    def run(self, *args, **kwargs):
        return subprocess.run(
            self.adb + list(args), capture_output=True, text=True, **kwargs
        ).stdout

    def shell(self, *args):
        return self.run("shell", *args)

    def say(self, message):
        if self.verbose:
            print(f"  {message}", flush=True)

    def screenshot(self, path):
        with open(path, "wb") as handle:
            handle.write(
                subprocess.run(
                    self.adb + ["exec-out", "screencap", "-p"], capture_output=True
                ).stdout
            )

        if os.path.getsize(path) < 1024:
            raise Tourfail(f"screencap wrote nothing to {path}")

    def nodes(self):
        """Every node of the current window, newest dump."""
        for _ in range(6):
            self.shell("uiautomator", "dump", "/sdcard/screen-tour.xml")
            xml = self.shell("cat", "/sdcard/screen-tour.xml")
            if "<hierarchy" in xml:
                flat = []

                def walk(node):
                    flat.append(node)
                    for child in node:
                        walk(child)

                walk(ET.fromstring(xml[xml.index("<hierarchy") :]))

                return flat

            time.sleep(1)

        raise Tourfail("uiautomator would not give us a window")

    def find(self, pattern, attribute="text"):
        """The first node whose [attribute] matches [pattern], or None."""
        for node in self.nodes():
            if re.search(pattern, node.get(attribute) or ""):
                return node

        return None

    def find_any(self, candidates):
        """The first of several (pattern, attribute) pairs that is on screen."""
        for pattern, attribute in candidates:
            node = self.find(pattern, attribute)
            if node is not None:
                return node, pattern

        return None, None

    def wait_for(self, candidates, timeout=25, what=""):
        deadline = time.time() + timeout
        while time.time() < deadline:
            node, pattern = self.find_any(candidates)
            if node is not None:
                return node, pattern

            time.sleep(1)

        raise Tourfail(f"waited {timeout}s for {what or candidates} and it never came up")

    def tap_node(self, node):
        left, top, right, bottom = map(int, re.findall(r"-?\d+", node.get("bounds")))
        self.shell("input", "tap", str((left + right) // 2), str((top + bottom) // 2))

    def tap(self, candidates, timeout=25, what=""):
        node, pattern = self.wait_for(candidates, timeout, what)
        self.say(f"tap {what or pattern}")
        self.tap_node(node)

        return pattern

    def back(self):
        self.shell("input", "keyevent", "KEYCODE_BACK")

    def swipe_up(self):
        """A screenful, near enough - for looking further down a list."""
        width, height = self.screen_size()

        self.shell(
            "input", "swipe",
            str(width // 2), str(int(height * 0.7)),
            str(width // 2), str(int(height * 0.3)),
            "300",
        )

    def screen_size(self):
        if self.size is None:
            match = re.search(r"(\d+)x(\d+)", self.shell("wm", "size"))
            self.size = (int(match.group(1)), int(match.group(2))) if match else (1080, 1920)

        return self.size

    def settle(self, seconds=2):
        time.sleep(seconds)


class Tourfail(Exception):
    pass


def sdk_root():
    return os.environ.get("ANDROID_HOME") or os.path.expanduser("~/Library/Android/sdk")


# The one place the two designs differ enough to need saying twice. Each entry is
# tried in order and the first that is on screen wins, so a single tour walks a
# branch whose actions live in a toolbar and one whose actions float over the page.
OPEN_BUTTON = [
    (r"landing_open$", "resource-id"),
    (r"landing_open_fab", "resource-id"),
    (r"^Open document$", "content-desc"),
]
FILE_MANAGER_CHOICE = [(r"^Files$", "text")]
ROOTS_DRAWER = [(r"^Show roots$", "content-desc")]
DOWNLOADS_ROOT = [(r"^Downloads$", "text")]
RECENTS_CHOICE = [(r"[Rr]ecently opened", "text")]
MORE_BUTTON = [(r"^More actions$", "content-desc"), (r"^More options$", "content-desc")]
CLOSE_MORE = [(r"^Close actions$", "content-desc")]
SEARCH_BUTTON = [(r"[Ss]earch in document", "content-desc")]
DOCUMENT_ON_SCREEN = [(r"document_container", "resource-id")]
LANDING_LIST = [(r"landing_list", "resource-id"), (r"landing_container", "resource-id")]


def tour(device, args, shots):
    """Walks the app, writing one png per screen into [shots]."""

    def shoot(name):
        device.settle(1)
        path = os.path.join(shots, f"{name}.png")
        device.screenshot(path)
        print(f"  {name}.png", flush=True)

    launch(device, args, fresh=not args.keep_state)
    shoot("01-first-launch")

    # what the app does when asked to open something. on one branch that is the
    # system picker, on the other the app's own list of file managers first -
    # both are worth a photograph, so this is shot before choosing anything
    device.tap(OPEN_BUTTON, what="the open button")
    device.settle(3)
    shoot("02-open")

    for index, document in enumerate(args.documents):
        if index > 0:
            device.tap(OPEN_BUTTON, what="the open button")
            device.settle(3)

        # only the older design asks which file manager to use
        chooser, _ = device.find_any(FILE_MANAGER_CHOICE)
        if chooser is not None:
            device.say("choosing Files in the file manager list")
            device.tap_node(chooser)
            device.settle(3)

        open_document(device, document)
        device.wait_for(DOCUMENT_ON_SCREEN, timeout=args.load_timeout, what="the document")
        device.settle(args.load_pause)

        if index < len(args.documents) - 1:
            device.back()
            device.settle(3)

    shoot("03-document")

    device.tap(MORE_BUTTON, what="the overflow")
    device.settle(2)
    shoot("04-menu")

    # the floating design closes its own way; a popup menu closes on back
    closer, _ = device.find_any(CLOSE_MORE)
    if closer is not None:
        device.tap_node(closer)
    else:
        device.back()
    device.settle(2)

    device.tap(SEARCH_BUTTON, what="search")
    device.settle(2)
    device.shell("input", "text", args.search)
    device.settle(1)
    device.shell("input", "keyevent", "KEYCODE_ENTER")
    device.settle(2)
    shoot("05-search")

    device.back()  # leaves the find bar
    device.settle(1)
    device.back()  # leaves the document
    device.settle(3)

    # where the app keeps what was opened: a screen of its own on one branch, a
    # dialog two taps deep on the other
    device.wait_for(LANDING_LIST, what="the landing screen")
    if device.find(r"landing_list", "resource-id") is None:
        device.tap(OPEN_BUTTON, what="the open button")
        device.settle(2)
        device.tap(RECENTS_CHOICE, what="the recent documents entry")
        device.settle(2)
    else:
        # a row keeps the pressed look of the tap that opened it, which
        # photographs as a selected row that nothing selected
        device.shell("input", "tap", "10", "10")
        device.settle(2)

    shoot("06-recents")


def open_document(device, name):
    """
    Finds [name] in the picker and taps it.

    The picker opens wherever it was left, or in Recent files on a device that has no answer to
    that - and neither of them need hold the documents this pushed. So it is browsed to rather
    than assumed: the roots drawer, then Downloads, then down the list if it is long.
    """
    pattern = re.escape(name)

    for attempt in range(4):
        if device.find(pattern) is not None:
            device.tap([(pattern, "text")], what=name)

            return

        if attempt == 0:
            device.say(f"{name} is not on screen, opening Downloads")
            device.tap(ROOTS_DRAWER, what="the roots drawer")
            device.settle(2)
            device.tap(DOWNLOADS_ROOT, what="the Downloads root")
        else:
            device.swipe_up()

        device.settle(3)

    raise Tourfail(f"{name} is not in the picker")


def launch(device, args, fresh):
    if fresh:
        device.say(f"clearing {args.package}")
        device.shell("pm", "clear", args.package)

    device.shell(
        "monkey", "-p", args.package, "-c", "android.intent.category.LAUNCHER", "1"
    )
    device.settle(4)

    dismiss_consent(device)


# The lite flavour asks for advertising consent before anything else. The dialog is
# a web view that arrives whenever the ad sdk has finished starting up - a second or
# ten after the landing screen is already drawn - so this cannot be a fixed wait: it
# watches for either the dialog or a quiet screen, and photographing the app with a
# consent sheet over it is exactly what a fixed wait gets you.
#
# Its buttons carry a description and no text, being web content, and a mistimed tap
# lands on "Manage options" and opens the vendor list - so "Accept all", which is the
# way out of that list, is one of the things looked for.
CONSENT_BUTTONS = [
    (r"^Accept all$", "text"),
    (r"^Accept all$", "content-desc"),
    (r"^Consent$", "text"),
    (r"^Consent$", "content-desc"),
]


def dismiss_consent(device, timeout=25):
    deadline = time.time() + timeout
    quiet = 0

    while time.time() < deadline:
        consent, pattern = device.find_any(CONSENT_BUTTONS)
        if consent is not None:
            # the sheet is still sliding in when it first appears, and a tap sent at
            # a button that has not stopped moving lands on the one below it
            device.settle(2)
            consent, pattern = device.find_any(CONSENT_BUTTONS)
            if consent is None:
                continue

            device.say(f"accepting the consent dialog ({pattern})")
            device.tap_node(consent)
            device.settle(5)

            quiet = 0
            continue

        # the app's own screen, with nothing over it, twice in a row - the dialog
        # can still be on its way while the landing screen is up behind it
        if device.find_any(OPEN_BUTTON)[0] is not None:
            quiet += 1
            if quiet >= 2:
                return

        device.settle(3)


def push_documents(device, args):
    for document in args.documents:
        source = os.path.join(args.assets, document)
        if not os.path.exists(source):
            raise Tourfail(f"no such document: {source}")

        device.run("push", source, f"{DEVICE_DIR}/{document}")

    # the picker lists what MediaStore knows about, not what is on the card
    device.shell(
        "am", "broadcast", "-a", "android.intent.action.MEDIA_SCANNER_SCAN_FILE",
        "-d", f"file://{DEVICE_DIR}",
    )


def main():
    parser = argparse.ArgumentParser(
        description="Photograph the screens that carry the app's design.",
    )
    parser.add_argument("--serial", help="adb serial, when more than one device is attached")
    parser.add_argument(
        "--package",
        default="at.tomtasche.reader.pro",
        help="application id to drive (default: at.tomtasche.reader.pro)",
    )
    parser.add_argument(
        "--out",
        default=os.path.join(REPO_ROOT, "build", "screen-tour", "shots"),
        help="where the pngs go (default: build/screen-tour/shots)",
    )
    parser.add_argument("--install", help="apk to install before starting")
    parser.add_argument(
        "--assets",
        default=os.path.join(REPO_ROOT, "app", "src", "androidTest", "assets"),
        help="where the documents to open come from",
    )
    parser.add_argument(
        "--document",
        dest="documents",
        action="append",
        help="a document to open, repeatable; the last one stays on screen",
    )
    parser.add_argument("--search", default=SEARCH_TERM, help="what to search the document for")
    parser.add_argument(
        "--keep-state",
        action="store_true",
        help="do not clear the app first - the first shot will not be a fresh install",
    )
    parser.add_argument("--load-timeout", type=int, default=40, help="seconds to wait for a load")
    parser.add_argument(
        "--load-pause",
        type=int,
        default=4,
        help="extra seconds after a document appears, for the WebView to finish drawing",
    )
    parser.add_argument("--quiet", action="store_true")
    args = parser.parse_args()

    args.documents = args.documents or DOCUMENTS

    device = Device(args.serial, not args.quiet)
    os.makedirs(args.out, exist_ok=True)

    if device.shell("getprop", "sys.boot_completed").strip() != "1":
        print("no booted device on adb", file=sys.stderr)
        return 2

    if args.install:
        print(f"installing {args.install}", flush=True)
        device.run("install", "-r", "-g", args.install)

    push_documents(device, args)

    print(f"touring {args.package} into {args.out}", flush=True)
    try:
        tour(device, args, args.out)
    except Tourfail as failure:
        print(f"\nthe tour stopped: {failure}", file=sys.stderr)
        print("the shots taken before it are still in place", file=sys.stderr)
        return 1

    print("done", flush=True)

    return 0


if __name__ == "__main__":
    sys.exit(main())
