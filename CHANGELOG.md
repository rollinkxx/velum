# Changelog

Semua perubahan penting proyek ini dicatat di sini.
Format mengikuti [Keep a Changelog](https://keepachangelog.com/id-ID/1.1.0/) dan proyek ini memakai [Semantic Versioning](https://semver.org/lang/id/).

## [Unreleased]

### Added
- Dokumen fondasi proyek: CHANGELOG, TODO, ADR 001 (identitas aplikasi), `.gitignore` Android.
- Skeleton proyek Gradle (AGP 8.7.3, Kotlin 2.0.21, Gradle 8.9 wrapper, katalog `libs.versions.toml`, modul `app` minSdk 24).
- `Prefs.kt` (penyimpanan registrasi) dan `WarpApi.kt` (registrasi/hapus registrasi WARP, uji `cdn-cgi/trace`) tanpa dependensi HTTP tambahan.
- `WarpTunnel.kt`: tunnel WireGuard via `GoBackend` (MTU 1280, DNS 1.1.1.1, keepalive 25) dan `AndroidManifest.xml` (VpnService library, foregroundServiceType specialUse).
- UI satu layar Bahasa Indonesia (`MainActivity`, layout XML, tema AppCompat, ikon adaptif): Sambungkan/Putuskan, status, Uji koneksi, Daftar ulang.
