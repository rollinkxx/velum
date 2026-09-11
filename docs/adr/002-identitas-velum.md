# ADR 002 — Identitas baru: Velum (menggantikan WARP Lite)

- **Status**: Accepted
- **Tanggal**: 2026-09-11
- **Supersedes**: [001](001-identitas-aplikasi.md)

## Konteks

ADR 001 menetapkan nama "WARP Lite" dan `applicationId` `com.rollinkxx.warp` sebelum
aspek merek dagang diteliti. Penelusuran lanjutan menemukan:

- WARP® adalah merek terdaftar Cloudflare, Inc. untuk kategori yang persis sama dengan
  aplikasi ini (*downloadable software for enabling VPN operation*).
- Panduan merek resmi Cloudflare melarang menggabungkan merek mereka ke dalam nama
  aplikasi/produk pihak ketiga; yang diizinkan hanya penyebutan referensial berbentuk
  kata (*for / compatible with*) disertai atribusi dan tanpa menyiratkan afiliasi.
- Nama "Velum" (Latin: layar/tirai) lolos pemeriksaan tabrakan: satu-satunya aplikasi
  Android bernama sama adalah perkakas B2B internal pabrik lampu Prancis (kategori
  Business, praktis tak dikenal, beda pasar); pemakai merek lain adalah produsen lampu
  (beda kelas barang) dan dashboard Kubernetes SUSE yang sudah end-of-life. Tidak ada
  produk VPN/keamanan bernama Velum.

Karena aplikasi belum pernah rilis publik, penggantian identitas masih murah.
Setelah rilis, `applicationId` permanen.

## Keputusan

- Nama tampil aplikasi = **Velum**.
- `applicationId` / namespace / package Kotlin = **`com.rollinkxx.velum`**
  (debug: suffix `.debug`, struktur direktori mengikuti).
- Tagline: "Tunnel aman yang ringan".
- Class internal `WarpApi`/`WarpTunnel` → `VelumApi`/`VelumTunnel`; nama sesi VPN,
  file preferensi, tema, dan log TAG mengikuti identitas baru.
- String status yang terlihat pengguna memakai "Velum" ("Velum aktif…",
  "Belum lewat Velum…").
- Penyebutan WARP/Cloudflare yang tersisa hanya yang referensial dan fungsional
  (endpoint, `CF-Client-Version`, komentar protokol, riwayat dokumen lama).
- Teks listing publik wajib memakai frasa referensial + disclaimer non-afiliasi,
  mis. "…kompatibel dengan jaringan Cloudflare WARP®. Aplikasi tidak resmi; tidak
  berafiliasi, didukung, atau disponsori oleh Cloudflare, Inc."

## Konsekuensi

- Seluruh file build, manifest, dan sumber Kotlin wajib memakai identitas di atas.
- Rename repo GitHub `rollinkxx/warp` → `rollinkxx/velum` bersifat opsional
  (dilakukan maintainer via Settings; redirect otomatis) dan tidak menghalangi kode.
- Verifikasi manual pra-rilis tetap wajib: pencarian "Velum" di Play Store,
  cek PDKI DJKI kelas 9/42, opsional cek USPTO/EUIPO bila target global.
- ADR 001 berstatus `Superseded by 002` dan tidak diubah selain baris statusnya.
