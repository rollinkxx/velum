#!/usr/bin/env python3
"""Mengubah baris error Gradle/Kotlin pada berkas log menjadi anotasi GitHub.

Alasan: log CI tidak bisa dibaca dari sandbox agen (`gh run view --log` EOF),
tetapi anotasi bisa dibaca lewat
`gh api repos/<owner>/<repo>/check-runs/<id>/annotations`.
Karena itu setiap kegagalan yang hanya muncul di log wajib diteruskan menjadi
anotasi, supaya run yang merah bisa didiagnosis tanpa membaca log.
"""
import re
import sys

POLA_KOTLIN = re.compile(
    r"^e:\s+(?P<berkas>\S+?):\s*\((?P<baris>\d+),\s*(?P<kolom>\d+)\):\s*(?P<pesan>.+)$"
)
POLA_JAVA = re.compile(r"^(?P<berkas>\S+\.java):(?P<baris>\d+):\s*error:\s*(?P<pesan>.+)$")


def bersih(teks: str) -> str:
    """Satu baris, tanpa karakter yang merusak format perintah GitHub."""
    return teks.replace("%", "%25").replace("\r", " ").replace("\n", " ").replace("::", ": ")[:400]


def utama() -> int:
    if len(sys.argv) < 2:
        return 0
    try:
        with open(sys.argv[1], encoding="utf-8", errors="replace") as berkas:
            baris = berkas.read().splitlines()
    except OSError:
        return 0

    jumlah = 0
    for teks in baris:
        cocok = POLA_KOTLIN.match(teks) or POLA_JAVA.match(teks)
        if not cocok:
            continue
        berkasnya = cocok.group("berkas").replace("file://", "")
        lokasi = f"file={berkasnya},line={cocok.group('baris')}"
        if cocok.groupdict().get("kolom"):
            lokasi += f",col={cocok.group('kolom')}"
        print(f"::error {lokasi}::{bersih(cocok.group('pesan'))}")
        jumlah += 1
    print(f"anotasi error terkirim: {jumlah}")
    return 0


if __name__ == "__main__":
    sys.exit(utama())
