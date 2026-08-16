#!/usr/bin/env python3
"""Fail when obvious secrets, private exports, or build artifacts enter the repo."""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
FORBIDDEN_PARTS = {".idea", "target", "uploads", "httpRequests"}
FORBIDDEN_SUFFIXES = {
    ".zip", ".jar", ".class", ".cookies", ".sql", ".db", ".sqlite",
    ".sqlite3", ".csv", ".xlsx", ".docx", ".mp3", ".pem", ".key",
    ".p12", ".pfx",
}
TEXT_SUFFIXES = {".java", ".js", ".html", ".css", ".md", ".xml", ".yml", ".yaml", ".properties", ".py", ".txt"}
SECRET_PATTERNS = (
    ("private key", re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----")),
    ("GitHub token", re.compile(r"gh[pousr]_[A-Za-z0-9_]{20,}")),
    ("OpenAI-style key", re.compile(r"sk-[A-Za-z0-9_-]{20,}")),
)
EMAIL_PATTERN = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")


def candidate_files() -> list[Path]:
    result = subprocess.run(
        ["git", "ls-files", "-co", "--exclude-standard"],
        cwd=ROOT,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=False,
    )
    if result.returncode == 0:
        return [ROOT / line for line in result.stdout.splitlines() if line]
    return [path for path in ROOT.rglob("*") if path.is_file() and ".git" not in path.parts]


def main() -> int:
    errors: list[str] = []

    for path in candidate_files():
        relative = path.relative_to(ROOT)
        relative_text = relative.as_posix()

        if FORBIDDEN_PARTS.intersection(relative.parts):
            errors.append(f"forbidden directory: {relative_text}")
        if path.suffix.lower() in FORBIDDEN_SUFFIXES:
            errors.append(f"forbidden file type: {relative_text}")
        if path.suffix.lower() not in TEXT_SUFFIXES:
            continue

        content = path.read_text(encoding="utf-8", errors="ignore")
        for label, pattern in SECRET_PATTERNS:
            if pattern.search(content):
                errors.append(f"possible {label}: {relative_text}")

        if relative_text == "src/main/resources/application.properties":
            required_placeholders = (
                "${SPRING_DATASOURCE_URL}",
                "${SPRING_DATASOURCE_USERNAME}",
                "${SPRING_DATASOURCE_PASSWORD}",
                "${JWT_SECRET}",
            )
            for placeholder in required_placeholders:
                if placeholder not in content:
                    errors.append(f"missing safe placeholder {placeholder}: {relative_text}")

        for email in EMAIL_PATTERN.findall(content):
            if not email.lower().endswith("@example.com"):
                errors.append(f"email-like value requires review: {relative_text}")

    if errors:
        print("Repository safety check failed:")
        for error in sorted(set(errors)):
            print(f"- {error}")
        return 1

    print("Repository safety check passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
