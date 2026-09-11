# TODO

| No. | Item | Prioritas | Status |
|---|---|---|---|
| 1 | Fondasi dokumen: CHANGELOG, TODO, ADR 001 applicationId, `.gitignore` | Tinggi | Selesai tervalidasi (CI run 34565410965) |
| 2 | Skeleton Gradle: settings/build/gradle.properties, `libs.versions.toml`, wrapper, `app/build.gradle.kts` | Tinggi | Selesai tervalidasi (CI run 34565410965) |
| 3 | CI `.github/workflows/build.yml` (assembleDebug + artifact + step summary) | Tinggi | Selesai tervalidasi (CI run 34565410965) |
| 4 | Kode inti: `Prefs.kt`, `WarpApi.kt` (registrasi WARP) | Tinggi | Selesai tervalidasi (CI run 34565410965) |
| 5 | `WarpTunnel.kt` (GoBackend) + `AndroidManifest.xml` | Tinggi | Selesai tervalidasi (CI run 34565410965) |
| 6 | UI Bahasa Indonesia: `MainActivity.kt`, layout, strings | Tinggi | Selesai tervalidasi (CI run 34565410965) |
| 7 | Perbarui AGENTS.md §5 dengan hasil CI nyata; status TODO | Sedang | Selesai (commit 6d476b4 + pembaruan status ini; validasi gerbang lokal — push `.md` tidak memicu CI) |
| 8 | (Opsional) job `assembleRelease` bertanda tangan via Secrets | Rendah | Belum, menunggu permintaan |
| 9 | Migrasi `actions/setup-java@v4` → `@v5` (advisory deprecation di run 34565410965) | Rendah | Selesai, menunggu validasi CI |
| 10 | Paritas WARP: registrasi dengan flag `warp_enabled: true` | Tinggi | Selesai tervalidasi (CI run 34568045154) |
| 11 | Poles tampilan: tema gelap elegan + kartu status + tombol custom (murni resource) | Sedang | Selesai tervalidasi (CI run 34568491718) |
| 12 | Status interaktif: durasi/endpoint/hasil uji+DC, auto-uji, titik berdenyut | Sedang | Selesai tervalidasi (CI run 34569006760) |
| 13 | (Opsional) auto-heal akun lama tanpa flag WARP via PATCH `/reg/{id}` (HttpURLConnection tidak mendukung PATCH — butuh klien HTTP lain atau hack; ada alternatif 1 ketukan Daftar ulang) | Rendah | Belum, menunggu keputusan maintainer |
| 14 | (Opsional) pintasan ke pengaturan Always-on VPN + blokir koneksi tanpa VPN (killswitch bawaan sistem) | Rendah | Belum, menunggu keputusan maintainer |
| 15 | (Opsional) sambung otomatis saat boot (RECEIVE_BOOT_COMPLETED) | Rendah | Belum, menunggu keputusan maintainer |
| 16 | (Opsional) notifikasi status koneksi (izin POST_NOTIFICATIONS Android 13+) | Rendah | Belum, menunggu keputusan maintainer |
