# ADR 001 — Identitas aplikasi: applicationId & nama

- **Status**: Accepted
- **Tanggal**: 2026-09-11

## Konteks
`applicationId` Android bersifat permanen setelah publikasi; mengubahnya berarti aplikasi baru.
Harus diputuskan sekali sebelum kode pertama ditulis.

## Keputusan
- `applicationId` = **`com.rollinkxx.warp`** (ditetapkan maintainer).
- Package Kotlin utama = `com.rollinkxx.warp` (sama dengan applicationId; struktur direktori
  `app/src/main/java/com/rollinkxx/warp/`).
- Nama tampilan aplikasi = **WARP Lite**.

Cek tabrakan nama (2026-09-11, via pencarian web karena sandbox tidak bisa mengakses Play Store
langsung): tidak ditemukan aplikasi/paket dengan id `com.rollinkxx.warp`. Nama "WARP Lite"
bersifat generik; aplikasi resmi Cloudflare memakai id `com.cloudflare.onedotonedotonedotone`
dengan nama "1.1.1.1 + WARP", sehingga tidak bertabrakan secara identitas paket.

## Konsekuensi
- Semua file build, manifest, dan sumber Kotlin wajib memakai identitas di atas.
- Perubahan identitas setelah rilis publik memerlukan ADR baru berstatus Superseded.
- Catatan merek: "WARP" adalah merek Cloudflare; bila dipublikasikan ke toko aplikasi,
  maintainer bertanggung jawab memastikan penamaan/deskripsi tidak menyesatkan.
