#!/usr/bin/env python3
"""Verifica la copia vendorizada del contrato en este repo."""
from __future__ import annotations

import hashlib
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CONTRACT = ROOT / "contract"


def fail(message: str) -> None:
    print(f"ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)


def main() -> None:
    version_file = CONTRACT / "VERSION"
    if not version_file.exists():
        fail("falta contract/VERSION")
    version = version_file.read_text(encoding="utf-8").strip()
    if not version.startswith("1."):
        fail(f"version de contrato inesperada: {version}")

    sums = CONTRACT / "SHA256SUMS"
    if not sums.exists():
        fail("falta contract/SHA256SUMS")

    for line in sums.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        parts = line.split(maxsplit=1)
        if len(parts) != 2:
            fail(f"linea invalida en SHA256SUMS: {line!r}")
        digest, relative = parts
        target = CONTRACT / relative.strip()
        if not target.exists():
            fail(f"falta {relative.strip()}")
        actual = hashlib.sha256(target.read_bytes()).hexdigest()
        if actual != digest:
            fail(f"huella distinta en {relative.strip()}")

    print(f"OK: contrato {version} vendorizado y verificado")


if __name__ == "__main__":
    main()
