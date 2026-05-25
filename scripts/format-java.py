#!/usr/bin/env python3
"""Convert leading 4-space indents to tabs in Java / Gradle Kotlin sources."""
from __future__ import annotations

import sys
from pathlib import Path

WIDTH = 4
SKIP_PARTS = {"build", ".gradle", "many_faces_proto", "bin", "obj"}


def convert_line(line: str) -> str:
	strip_end = line.rstrip("\n\r")
	suffix = line[len(strip_end) :]
	if not strip_end.strip():
		return strip_end + suffix
	leading = 0
	while leading < len(strip_end) and strip_end[leading] == " ":
		leading += 1
	if leading == 0 or strip_end.startswith("\t"):
		return line
	if leading % WIDTH != 0:
		return line
	return ("\t" * (leading // WIDTH)) + strip_end[leading:] + suffix


def convert_file(path: Path) -> bool:
	original = path.read_text(encoding="utf-8")
	updated = "".join(convert_line(line) for line in original.splitlines(keepends=True))
	if updated != original:
		path.write_text(updated, encoding="utf-8")
		return True
	return False


def main() -> None:
	root = Path(__file__).resolve().parents[1]
	changed = 0
	for pattern in ("**/*.java", "**/*.kts", "**/*.gradle"):
		for path in root.glob(pattern):
			if any(part in SKIP_PARTS for part in path.parts):
				continue
			if convert_file(path):
				changed += 1
				print(f"ok {path.relative_to(root)}")
	print(f"format-java: {changed} file(s) updated")


if __name__ == "__main__":
	main()
