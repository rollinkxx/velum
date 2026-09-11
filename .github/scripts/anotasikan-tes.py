#!/usr/bin/env python3
"""Mengubah kegagalan pengujian unit (laporan JUnit XML) menjadi anotasi GitHub.

Sama seperti `anotasikan-log.py`: log CI tidak bisa dibaca dari sandbox agen,
sedangkan anotasi bisa. Tanpa ini, satu pengujian yang gagal membuat job
`unitTest` merah tanpa informasi yang bisa diakses.
"""
import glob
import sys
import xml.etree.ElementTree as ET


def bersih(teks: str) -> str:
    return teks.replace("%", "%25").replace("\r", " ").replace("\n", " ").replace("::", ": ")[:400]


def utama() -> int:
    jumlah = 0
    for pola in (
        "app/build/test-results/testDebugUnitTest/*.xml",
        "app/build/test-results/test*/**/*.xml",
    ):
        for jalur in sorted(glob.glob(pola, recursive=True)):
            try:
                akar = ET.parse(jalur).getroot()
            except (OSError, ET.ParseError):
                continue
            for kasus in akar.iter("testcase"):
                for tag in ("failure", "error"):
                    for simpul in kasus.findall(tag):
                        nama = f"{kasus.get('classname', '?')}.{kasus.get('name', '?')}"
                        pesan = simpul.get("message") or (simpul.text or "").strip()
                        print(f"::error title={nama}::{bersih(pesan)}")
                        jumlah += 1
    print(f"anotasi kegagalan tes terkirim: {jumlah}")
    return 0


if __name__ == "__main__":
    sys.exit(utama())
