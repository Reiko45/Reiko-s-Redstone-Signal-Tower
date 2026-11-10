#!/usr/bin/env python3
"""Uploads the mod's jar to CurseForge (GitHub Actions runs this; see docs/PUBLISHING.md).

It uses CurseForge's upload API: it looks up the ids of the game versions to tag the
file with (Minecraft 1.21.1, NeoForge, Java 21, Client, Server), takes this version's
section of CHANGELOG.md as the changelog, lists the optional mods it works with, and
uploads the jar as a release.

Usage:
    CURSEFORGE_TOKEN=... curseforge_upload.py --jar build/libs/x.jar --version 0.68.1 --project-id 123456
    curseforge_upload.py --jar x.jar --version 0.68.1 --project-id 123456 --dry-run

Only the Python standard library is used. CURSEFORGE_API overrides the server (for tests).
"""

import argparse
import json
import os
import re
import sys
import urllib.error
import urllib.request
import uuid

API = os.environ.get("CURSEFORGE_API", "https://minecraft.curseforge.com").rstrip("/")
MOD_NAME = "Reiko's Wireless Redstone"
MINECRAFT_VERSION = "1.21.1"

# Game versions to tag the file with: (what to call it, test on the version type's slug,
# the version's name, whether the upload should stop if CurseForge doesn't have it).
GAME_VERSIONS = [
    ("Minecraft " + MINECRAFT_VERSION, lambda slug: slug.startswith("minecraft-1-21"), MINECRAFT_VERSION, True),
    ("NeoForge", lambda slug: slug == "modloader", "NeoForge", True),
    ("Java 21", lambda slug: slug == "java", "Java 21", False),
    ("Client", lambda slug: slug == "environment", "Client", False),
    ("Server", lambda slug: slug == "environment", "Server", False),
]

# Optional mods the mod works with, by their CurseForge slugs. (CC: Tweaked and
# Reiko's Weather aren't listed: neither has a 1.21.1 NeoForge file on CurseForge.)
OPTIONAL_MODS = ["jade", "wthit-forge", "jei", "emi", "journeymap", "xaeros-minimap"]


class ApiError(Exception):
    def __init__(self, status, body):
        super().__init__(f"HTTP {status}: {body}")
        self.status = status
        self.body = body


def call(method, path, token, body=None, content_type=None):
    request = urllib.request.Request(API + path, data=body, method=method)
    request.add_header("X-Api-Token", token)
    request.add_header("Accept", "application/json")
    request.add_header("User-Agent", "reikos-wireless-redstone-publisher (GitHub Actions)")
    if content_type:
        request.add_header("Content-Type", content_type)
    try:
        with urllib.request.urlopen(request, timeout=300) as response:
            return json.loads(response.read().decode("utf-8") or "null")
    except urllib.error.HTTPError as error:
        raise ApiError(error.code, error.read().decode("utf-8", "replace")[:2000]) from None


def game_version_ids(token):
    types = call("GET", "/api/game/version-types", token)
    versions = call("GET", "/api/game/versions", token)
    type_slugs = {t["id"]: t.get("slug", "") for t in types}
    ids = []
    for label, type_test, name, required in GAME_VERSIONS:
        found = [v["id"] for v in versions
                 if v.get("name") == name and type_test(type_slugs.get(v.get("gameVersionTypeID"), ""))]
        if found:
            ids.append(found[0])
            print(f"  {label}: game version id {found[0]}")
        elif required:
            raise SystemExit(f"CurseForge has no game version for {label}; nothing was uploaded.")
        else:
            print(f"  {label}: not found on CurseForge, left out")
    return ids


def changelog_for(version, path="CHANGELOG.md"):
    with open(path, encoding="utf-8") as file:
        text = file.read()
    match = re.search(r"^## " + re.escape(version) + r"[ \t]*\n(.*?)(?=^## |\Z)", text, re.M | re.S)
    if not match or not match.group(1).strip():
        raise SystemExit(f"{path} has no '## {version}' section; add one, then push again. Nothing was uploaded.")
    return match.group(1).strip()


def multipart(fields, file_field, file_name, file_bytes, file_type):
    boundary = "----reikos-wireless-redstone-" + uuid.uuid4().hex
    parts = []
    for name, value in fields.items():
        # A plain form field, the way `curl -F metadata=...` sends it.
        parts.append(f"--{boundary}\r\nContent-Disposition: form-data; name=\"{name}\"\r\n\r\n".encode("utf-8"))
        parts.append(value.encode("utf-8"))
        parts.append(b"\r\n")
    parts.append((f"--{boundary}\r\nContent-Disposition: form-data; name=\"{file_field}\"; "
                  f"filename=\"{file_name}\"\r\nContent-Type: {file_type}\r\n\r\n").encode("utf-8"))
    parts.append(file_bytes)
    parts.append(f"\r\n--{boundary}--\r\n".encode("utf-8"))
    return b"".join(parts), "multipart/form-data; boundary=" + boundary


def upload(token, project_id, jar, metadata):
    with open(jar, "rb") as file:
        data = file.read()
    body, content_type = multipart({"metadata": json.dumps(metadata)}, "file", os.path.basename(jar),
                                   data, "application/java-archive")
    return call("POST", f"/api/projects/{project_id}/upload-file", token, body, content_type)


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--jar", required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--project-id", required=True)
    parser.add_argument("--release-type", default=os.environ.get("CURSEFORGE_RELEASE_TYPE", "release"),
                        choices=["release", "beta", "alpha"])
    parser.add_argument("--dry-run", action="store_true", help="print what would be uploaded, upload nothing")
    args = parser.parse_args()

    if not re.fullmatch(r"\d+", args.project_id.strip()):
        raise SystemExit(f"The CurseForge project id should be a number, not {args.project_id!r}.")
    if not os.path.isfile(args.jar):
        raise SystemExit(f"No jar at {args.jar}.")

    metadata = {
        "changelog": changelog_for(args.version),
        "changelogType": "markdown",
        "displayName": f"{MOD_NAME} {args.version}",
        "releaseType": args.release_type,
        "relations": {"projects": [{"slug": slug, "type": "optionalDependency"} for slug in OPTIONAL_MODS]},
    }

    if args.dry_run:
        metadata["gameVersions"] = [label for label, *_ in GAME_VERSIONS]
        print(json.dumps(metadata, indent=2))
        print(f"Would upload {args.jar} ({os.path.getsize(args.jar)} bytes) to project {args.project_id}.")
        return

    token = os.environ.get("CURSEFORGE_TOKEN", "").strip()
    if not token:
        raise SystemExit("CURSEFORGE_TOKEN isn't set; nothing was uploaded.")

    print("Looking up game versions:")
    metadata["gameVersions"] = game_version_ids(token)

    print(f"Uploading {os.path.basename(args.jar)} to CurseForge project {args.project_id} as a {args.release_type}...")
    try:
        result = upload(token, args.project_id.strip(), args.jar, metadata)
    except ApiError as error:
        if error.status == 400 and metadata["relations"]["projects"]:
            # A related mod's slug CurseForge doesn't know shouldn't stop the release.
            print(f"::warning::CurseForge refused the upload ({error.body}); trying again without the optional mods listed.")
            metadata["relations"] = {"projects": []}
            result = upload(token, args.project_id.strip(), args.jar, metadata)
        else:
            raise
    file_id = (result or {}).get("id")
    print(f"Uploaded: CurseForge file id {file_id}. It shows as 'Under review' until CurseForge approves it.")


if __name__ == "__main__":
    try:
        main()
    except urllib.error.URLError as error:
        raise SystemExit(f"Couldn't reach CurseForge ({error.reason}); nothing was uploaded. Run the workflow again later.")
    except ApiError as error:
        if error.status in (401, 403):
            raise SystemExit(f"CurseForge refused the token ({error}). Make a new upload token and update the CURSEFORGE_TOKEN secret.")
        raise SystemExit(f"CurseForge refused the upload: {error}")
