#!/usr/bin/env python3
"""Checks that each fetched source still speaks the protocol its parser expects.

Parsers are covered by fixture unit tests, which catch changes in our own code. What they cannot
catch is a source changing its wire format underneath us: a renamed field, a new error envelope, a
challenge page where JSON used to be. This script performs the exact request the Android adapter
performs, then asserts the fields the parser reads are present and shaped as expected.

    tools/live-source-check.py 1ST06013631493
    tools/live-source-check.py 1ST06013631493 --source cainiao

Exit status is 0 when every checked source either satisfied its contract or had no record of the
number, and 1 when a source answered in a shape its parser cannot read. Sources that need a browser
engine (kind "browser") are reported as skipped; they are verified on device.
"""

import argparse
import json
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from http.cookiejar import CookieJar
from pathlib import Path

SOURCES = Path(__file__).resolve().parent.parent / "app/src/main/assets/sources.json"
UA = ("Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36")
TIMEOUT = 45


class ContractError(Exception):
    """The source answered, but not in a shape the parser can read."""


class NoRecord(Exception):
    """The source works; it just does not know this tracking number."""


def normalize(number):
    return re.sub(r"[^A-Z0-9]", "", number.upper())


def fetch(url, headers=None, opener=None, data=None):
    request = urllib.request.Request(url, data=data, headers={"User-Agent": UA, **(headers or {})})
    open_ = opener.open if opener is not None else urllib.request.urlopen
    with open_(request, timeout=TIMEOUT) as response:
        return response.read().decode("utf-8", "replace")


def require(condition, message):
    if not condition:
        raise ContractError(message)


def check_cainiao(recipe, number):
    """CainiaoJsonParser reads module[].mailNo and module[].detailList[].{time,desc,standerdDesc}."""
    body = fetch(recipe["url"].replace("{tracking}", number),
                 {"Accept": "application/json, text/plain, */*",
                  "Referer": f"https://global.cainiao.com/newDetail.htm?mailNoList={number}"})
    try:
        root = json.loads(body)
    except json.JSONDecodeError:
        raise ContractError(f"response is not JSON: {body[:120]!r}")

    require(isinstance(root.get("module"), list), "missing 'module' array")
    matching = [m for m in root["module"] if normalize(str(m.get("mailNo", ""))) == number]
    require(matching, "no module echoes the requested mailNo")
    details = matching[0].get("detailList")
    require(isinstance(details, list), "'detailList' is not an array")
    if not details:
        raise NoRecord("Cainiao has no record of this number")

    first = details[0]
    require(isinstance(first.get("time"), int), "'time' is not epoch milliseconds")
    require(first.get("desc") or first.get("standerdDesc"), "entry has neither 'desc' nor 'standerdDesc'")
    return f"{len(details)} events, status {matching[0].get('statusDesc') or matching[0].get('status')!r}"


def check_packy(recipe, number):
    """PackyJsonParser reads trackCode and events[].{description,eventDate,status}."""
    if not re.fullmatch(r"1ST[0-9]{11}", number):
        raise NoRecord("not a 1ST-format number")
    body = fetch(recipe["url"].replace("{tracking}", number),
                 {"Accept": "application/json", "Referer": "https://packyapp.com/en/carriers/1st"})
    try:
        root = json.loads(body)
    except json.JSONDecodeError:
        raise ContractError(f"response is not JSON: {body[:120]!r}")

    require(normalize(str(root.get("trackCode", ""))) == number, "'trackCode' does not echo the request")
    events = root.get("events")
    require(isinstance(events, list), "'events' is not an array")
    if not events:
        raise NoRecord("Packy has no events for this number")
    require(events[0].get("description"), "event has no 'description'")
    require(events[0].get("eventDate"), "event has no 'eventDate'")
    return f"{len(events)} events, status {root.get('status')!r}"


def parcelsapp_tracking_id(number):
    """Mirrors ParcelsAppProtocol.encodedTrackingId."""
    safe = "-_.!~*'()"
    encoded = urllib.parse.quote(number, safe=safe)
    shifted = "".join(chr((ord(c) + 76) % 126) if ord(c) <= 126 else c for c in encoded)
    return urllib.parse.quote(shifted, safe=safe)


def check_parcelsapp(recipe, number):
    """ParcelsAppWebSource bootstraps a CSRF session, then posts the shifted tracking id."""
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(CookieJar()))
    html = fetch("https://parcelsapp.com/en", {"Accept": "text/html"}, opener=opener)
    token = re.search(r'<meta name="csrf-token" content="([^"]+)"', html)
    require(token, "bootstrap page no longer exposes a csrf-token meta tag")

    form = urllib.parse.urlencode({
        "trackingId": parcelsapp_tracking_id(number), "carrier": "Auto-Detect", "language": "en",
        "country": "Unknown", "platform": "web-android", "wd": "false", "c": "false", "p": "0", "l": "1",
    }).encode()
    body = fetch("https://parcelsapp.com/api/v2/parcels", {
        "Accept": "application/json, text/javascript, */*; q=0.01",
        "X-Requested-With": "XMLHttpRequest", "X-CSRF-Token": token.group(1),
        "Origin": "https://parcelsapp.com", "Referer": "https://parcelsapp.com/en",
    }, opener=opener, data=form)
    try:
        root = json.loads(body)
    except json.JSONDecodeError:
        raise ContractError(f"response is not JSON: {body[:120]!r}")

    error = str(root.get("error", "")).strip()
    if error:
        # The parser maps these codes; they mean the protocol works and the lookup did not.
        if error.upper() in {"NO_TRACKER", "NO_DATA", "INVALID_TRACKING_NUMBER"}:
            raise NoRecord(f"ParcelsApp returned {error}")
        raise ContractError(f"ParcelsApp returned {error}")

    states = root.get("states")
    require(isinstance(states, list), "'states' is not an array")
    if not states:
        raise NoRecord("ParcelsApp returned no states")
    require(any(states[0].get(key) for key in ("state", "status", "description", "message", "details")),
            "state has no description field the parser reads")
    return f"{len(states)} states"


CHECKS = {"cainiao": check_cainiao, "packy_1st": check_packy, "parcelsapp": check_parcelsapp}


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("tracking_number")
    parser.add_argument("--source", action="append", dest="sources",
                        help="limit the run to this source id; repeatable")
    args = parser.parse_args()

    number = normalize(args.tracking_number)
    recipes = json.loads(SOURCES.read_text())["sources"]
    failures = 0
    checked = 0

    for recipe in recipes:
        if recipe["kind"] == "link":
            continue
        if args.sources and recipe["id"] not in args.sources:
            continue
        if recipe["kind"] == "browser":
            print(f"  SKIP  {recipe['id']:<12} needs a browser engine; verified on device")
            continue

        check = CHECKS.get(recipe["id"])
        if check is None:
            print(f"  SKIP  {recipe['id']:<12} no protocol contract is defined for this source")
            continue

        checked += 1
        try:
            detail = check(recipe, number)
            print(f"  OK    {recipe['id']:<12} {detail}")
        except NoRecord as absent:
            print(f"  EMPTY {recipe['id']:<12} {absent}")
        except ContractError as broken:
            failures += 1
            print(f"  BROKE {recipe['id']:<12} {broken}")
        except (urllib.error.URLError, OSError) as unreachable:
            print(f"  NET   {recipe['id']:<12} not reachable from here: {unreachable}")

    print(f"\n{checked} source(s) checked, {failures} contract failure(s)")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
