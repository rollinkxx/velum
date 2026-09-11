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
| 8 | (Opsional) job `assembleRelease` bertanda tangan via Secrets | Rendah | Selesai tervalidasi (PR #1; job skip by design menunggu Secrets maintainer) |
| 9 | Migrasi `actions/setup-java@v4` → `@v5` (advisory deprecation di run 34565410965) | Rendah | Selesai tervalidasi (PR #1) |
| 10 | Paritas WARP: registrasi dengan flag `warp_enabled: true` | Tinggi | Selesai tervalidasi (CI run 34568045154) |
| 11 | Poles tampilan: tema gelap elegan + kartu status + tombol custom (murni resource) | Sedang | Selesai tervalidasi (CI run 34568491718) |
| 12 | Status interaktif: durasi/endpoint/hasil uji+DC, auto-uji, titik berdenyut | Sedang | Selesai tervalidasi (CI run 34569006760) |
| 13 | (Opsional) auto-heal akun lama tanpa flag WARP via PATCH `/reg/{id}` (HttpURLConnection tidak mendukung PATCH — butuh klien HTTP lain atau hack; ada alternatif 1 ketukan Daftar ulang) | Rendah | Selesai dengan mekanisme lain: GET + daftar ulang otomatis, fail-safe — tervalidasi (PR #1) |
| 14 | (Opsional) pintasan ke pengaturan Always-on VPN + blokir koneksi tanpa VPN (killswitch bawaan sistem) | Rendah | Selesai, tervalidasi (PR #1) |
| 15 | (Opsional) sambung otomatis saat boot (RECEIVE_BOOT_COMPLETED) | Rendah | Selesai, tervalidasi (PR #1) |
| 16 | (Opsional) notifikasi status koneksi (izin POST_NOTIFICATIONS Android 13+) | Rendah | Selesai, tervalidasi (PR #1) |
| 17 | Rename identitas WARP Lite → Velum (ADR 002) | Tinggi | Selesai tervalidasi (CI run 34580968135) |
| 18 | Sambung ulang otomatis saat jaringan berganti (NetworkCallback + backoff) | Tinggi | Selesai tervalidasi (CI run 34581202095) |
| 19 | Fix `Prefs.clear()`: pertahankan memo `wasUp` | Sedang | Selesai tervalidasi (CI run 34580968135) |
| 20 | Enkripsi SharedPreferences (EncryptedSharedPreferences + migrasi) | Tinggi | Selesai tervalidasi (CI run 34581202095) |
| 21 | Statistik trafik + deteksi tunnel basi | Sedang | Selesai tervalidasi (CI run 34581202095) |
| 22 | Uji `assembleRelease` bertanda tangan end-to-end (butuh Secrets maintainer) | Sedang | Menunggu Secrets maintainer |
| 23 | Anotasi advisory Node.js 20 deprecated di runner (actions dipaksa ke Node 24; non-pemblokir, terlihat di run 34581202095) | Rendah | Dicatat; menunggu upstream actions |
