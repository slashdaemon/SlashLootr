#!/usr/bin/env python3
"""
Publish SlashLootr per-band JARs to Modrinth.

One JAR per MC version band, Fabric-only. Each band becomes one Modrinth
version. Simplified cousin of StreamCraft's per-platform-variant flow —
same .env loading, dry-run, project-ID resolution, idempotent re-runs.

Usage:
    # Dry-run all bands to inspect metadata
    python scripts/publish-modrinth.py --project slashlootr --version 0.1.0 --dry-run

    # Publish all bands as a release
    python scripts/publish-modrinth.py --project slashlootr --version 0.1.0

    # Publish a subset as a beta
    python scripts/publish-modrinth.py --project slashlootr --version 0.1.0 \\
        --bands 1.21.9 --type beta

Auth via MODRINTH_TOKEN env var or .env file in the repo root. PAT scope:
"Create version". Get one at https://modrinth.com/settings/pats.

Project slug or ID via --project. Modrinth resolves either, but the JSON
body for POST /version requires the base62 ID — the script does the lookup.

Idempotent: existing Modrinth versions matching the version_number are
skipped, so re-running after a partial failure won't double-upload.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from pathlib import Path

try:
    import requests
except ImportError:
    print("ERR: requests not installed. Install with: pip install requests")
    sys.exit(1)


MODRINTH_API = "https://api.modrinth.com/v2"

# Per-band Minecraft versions to advertise. Mirrors publish-curseforge.py.
BAND_GAME_VERSIONS = {
    "1.20.1":  ["1.20.1"],
    "1.20.5":  ["1.20.5", "1.20.6"],
    "1.21":    ["1.21"],
    "1.21.1":  ["1.21.1"],
    "1.21.2":  ["1.21.2", "1.21.3"],
    "1.21.4":  ["1.21.4", "1.21.5"],
    "1.21.6":  ["1.21.6", "1.21.7", "1.21.8"],
    "1.21.9":  ["1.21.9", "1.21.10"],
    "1.21.11": ["1.21.11"],
    "26.1.2":  ["26.1.2"],
}

# Hardcoded dependency: Fabric API (Modrinth project ID P7dR8mSH).
DEPENDENCIES = [
    {"project_id": "P7dR8mSH", "dependency_type": "required"},  # fabric-api
]


def parse_filename(jar_path: Path) -> tuple[str, str]:
    """Parse slashlootr-<ver>+mc<band>.jar → (mod_version, mc_band)."""
    name = jar_path.stem
    m = re.match(r"^slashlootr-([^+]+)\+mc([0-9.]+)$", name)
    if not m:
        raise ValueError(f"Cannot parse filename: {jar_path.name}")
    return m.group(1), m.group(2)


def discover_jars(release_dir: Path, mod_version: str) -> dict[str, Path]:
    by_band: dict[str, Path] = {}
    for jar in sorted(release_dir.glob(f"slashlootr-{mod_version}+mc*.jar")):
        if jar.name.endswith("-sources.jar") or jar.name.endswith("-dev.jar"):
            continue
        try:
            ver, band = parse_filename(jar)
        except ValueError as e:
            print(f"  skip: {e}")
            continue
        if ver != mod_version:
            continue
        by_band[band] = jar
    return by_band


def extract_changelog(changelog_path: Path, mod_version: str, full_file: bool) -> str:
    if not changelog_path.exists():
        return ""
    text = changelog_path.read_text(encoding="utf-8")
    if full_file:
        return text.strip()
    pattern = rf"## v?{re.escape(mod_version)}\b.*?(?=\n## v?\d|\Z)"
    m = re.search(pattern, text, re.DOTALL)
    return m.group(0).strip() if m else ""


def fetch_known_game_versions() -> set[str]:
    r = requests.get(f"{MODRINTH_API}/tag/game_version", timeout=30)
    r.raise_for_status()
    return {v["version"] for v in r.json()}


def resolve_project_id(slug_or_id: str, token: str) -> str:
    """POST /version requires the base62 ID, not the slug. Look it up."""
    r = requests.get(
        f"{MODRINTH_API}/project/{slug_or_id}",
        headers={"Authorization": token},
        timeout=30,
    )
    if r.status_code == 404:
        raise SystemExit(
            f"ERR: Modrinth project '{slug_or_id}' not found. Create the project at "
            f"https://modrinth.com/mods, then pass its slug or ID via --project."
        )
    r.raise_for_status()
    return r.json()["id"]


def find_existing_version(project: str, version_number: str, token: str) -> str | None:
    r = requests.get(
        f"{MODRINTH_API}/project/{project}/version",
        headers={"Authorization": token},
        timeout=30,
    )
    if r.status_code == 404:
        raise SystemExit(
            f"ERR: Modrinth project '{project}' not found. Create the project at "
            f"https://modrinth.com/mods, then pass its slug or ID via --project."
        )
    r.raise_for_status()
    for v in r.json():
        if v["version_number"] == version_number:
            return v["id"]
    return None


def upload_version(
    project: str,
    project_id: str,
    mod_version: str,
    band: str,
    jar: Path,
    game_versions: list[str],
    version_type: str,
    changelog: str,
    token: str,
    dry_run: bool,
) -> bool:
    """Returns True on successful upload, False on skip."""
    version_number = f"{mod_version}+mc{band}"
    name = f"v{mod_version} (MC {band})"

    print(f"\n=== {version_number} ===")
    print(f"  game_versions: {game_versions}")
    print(f"  file: {jar.name}")

    if not dry_run:
        existing = find_existing_version(project, version_number, token)
        if existing:
            print(f"  SKIP - already published (id={existing})")
            return False

    metadata = {
        "name": name,
        "version_number": version_number,
        "changelog": changelog,
        "dependencies": DEPENDENCIES,
        "game_versions": game_versions,
        "version_type": version_type,
        "loaders": ["fabric"],
        "featured": False,
        "project_id": project_id,
        "file_parts": [jar.name],
        "primary_file": jar.name,
    }

    if dry_run:
        print(f"  DRY-RUN metadata:\n{json.dumps(metadata, indent=2)}")
        return False

    with jar.open("rb") as fh:
        files = [
            ("data", (None, json.dumps(metadata), "application/json")),
            (jar.name, (jar.name, fh, "application/java-archive")),
        ]
        size_mb = jar.stat().st_size / 1_000_000
        print(f"  POST {MODRINTH_API}/version ({size_mb:.2f} MB) ...")
        r = requests.post(
            f"{MODRINTH_API}/version",
            headers={"Authorization": token},
            files=files,
            timeout=600,
        )

    if r.status_code >= 400:
        raise RuntimeError(f"Modrinth {r.status_code}: {r.text}")

    data = r.json()
    print(f"  OK id={data['id']}, url=https://modrinth.com/mod/{project}/version/{version_number}")
    return True


def load_dotenv(path: Path) -> None:
    if not path.exists():
        return
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, value = line.partition("=")
        key, value = key.strip(), value.strip()
        if key and value and key not in os.environ:
            os.environ[key] = value


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--project", default=None,
                   help="Modrinth project slug or ID. Falls back to $MODRINTH_PROJECT_SLUG.")
    p.add_argument("--version", required=True, help="Mod version (e.g. 0.1.0)")
    p.add_argument("--type", default="release", choices=["release", "beta", "alpha"])
    p.add_argument("--release-dir", default="build/release", help="Directory containing JARs")
    p.add_argument("--changelog-file", default="CHANGELOG.md", help="Path to changelog file")
    p.add_argument("--bands", help="Comma-separated MC bands to upload (default: all discovered)")
    p.add_argument("--dry-run", action="store_true", help="Print metadata without uploading")
    args = p.parse_args()

    project_root = Path(__file__).resolve().parent.parent
    load_dotenv(project_root / ".env")

    token = os.environ.get("MODRINTH_TOKEN", "")
    if not token and not args.dry_run:
        print("ERR MODRINTH_TOKEN not set (env var or .env file)")
        return 1

    if args.project is None:
        args.project = os.environ.get("MODRINTH_PROJECT_SLUG", "").strip()
        if not args.project:
            print("ERR --project not given and MODRINTH_PROJECT_SLUG not set in env/.env")
            return 1
        print(f"Using MODRINTH_PROJECT_SLUG={args.project} from .env")

    release_dir = (project_root / args.release_dir).resolve()
    if not release_dir.is_dir():
        print(f"ERR release dir not found: {release_dir}")
        return 1

    by_band = discover_jars(release_dir, args.version)
    if not by_band:
        print(f"ERR no JARs found matching version {args.version} in {release_dir}")
        return 1

    if args.bands:
        wanted = {b.strip() for b in args.bands.split(",")}
        by_band = {b: v for b, v in by_band.items() if b in wanted}
        if not by_band:
            print(f"ERR no bands matching --bands={args.bands}")
            return 1

    print(f"Discovered {len(by_band)} band(s) for v{args.version}: {sorted(by_band)}")

    full_file = (args.changelog_file != "CHANGELOG.md")
    changelog = extract_changelog(project_root / args.changelog_file, args.version, full_file)
    if not changelog:
        print(f"WARN no changelog section found for v{args.version} in {args.changelog_file}")
    else:
        first_line = changelog.splitlines()[0] if changelog else ""
        print(f"Changelog: {first_line} ({len(changelog)} chars)")

    project_id = args.project
    if not args.dry_run:
        project_id = resolve_project_id(args.project, token)
        print(f"Project: {args.project} -> id={project_id}")
        try:
            known = fetch_known_game_versions()
            seen = {b for b in by_band}
            for band in seen:
                requested = BAND_GAME_VERSIONS.get(band, [band])
                missing = [v for v in requested if v not in known]
                if missing:
                    print(f"WARN band {band}: Modrinth doesn't recognize {missing}; "
                          f"those entries will be silently dropped on upload")
        except Exception as e:
            print(f"WARN could not fetch known game versions: {e}")

    failures = 0
    successes = 0
    for band in sorted(by_band):
        jar = by_band[band]
        game_versions = BAND_GAME_VERSIONS.get(band, [band])
        try:
            if upload_version(
                project=args.project,
                project_id=project_id,
                mod_version=args.version,
                band=band,
                jar=jar,
                game_versions=game_versions,
                version_type=args.type,
                changelog=changelog,
                token=token,
                dry_run=args.dry_run,
            ):
                successes += 1
        except Exception as e:
            print(f"  FAILED: {e}")
            failures += 1

    skipped = len(by_band) - successes - failures
    print(f"\nDone - {successes} uploaded, {failures} failed, {skipped} skipped")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
