# AGENTS.md — Panduan Wajib Sesi Agen (repo `rollinkxx/warp`)

Dokumen ini mengikat setiap agen coding yang bekerja di repo ini. Isinya diturunkan dari
keadaan repo yang nyata dan dari kesepakatan dengan maintainer. Bagian yang bertanda
**[direncanakan]** belum ada di repo dan baru berlaku setelah dibuat.
Bila fakta di §5 berubah, perbarui dokumen ini dalam **1 commit khusus** berjudul
`docs: sinkronisasi AGENTS.md` — jangan menumpuk perubahan aturan bersama perubahan kode.

## §1 Model Sesi & Branch

- Sandbox agen bersifat **ephemeral**. Satu-satunya state yang awet adalah yang sudah
  **ter-push ke GitHub**. Prinsip: **"belum push = belum kerja"**.
- Setiap sesi Arena terikat pada satu branch berpola `arena/<id>-<suffix>`
  (sesi ini: `arena/01a08ecf-warp`, bercabang dari `main` @ `76b33c9`).
  - Catatan pemulihan (2026-09-11): sesi sebelumnya `arena/01a08e90-warp` berisi seluruh
    pekerjaan awal proyek; branch-nya terhapus di remote. Commit HEAD-nya (`26104f6`,
    CI hijau) dipulihkan lewat pengambilan SHA dangling dari workflow run, lalu di-merge
    ke branch sesi ini.
- Semua kerja HANYA di branch sesi. Dilarang `checkout`/`switch`/membuat branch lain,
  dilarang push ke branch lain.
- Sebelum mulai tugas apa pun: `git branch --show-current` dan `git status` — tree harus bersih.
- Branch default repo: `main`. Agen **tidak pernah** merge ke `main` (merge mengakhiri sesi).
- Aturan khusus maintainer repo ini: **agen tidak mengeksekusi perubahan apa pun sebelum
  diperintahkan secara eksplisit.** Sajikan rencana dulu, tunggu perintah, baru kerjakan.

## §2 Aturan Emas: Push ≠ PR ≠ Merge

| Aksi | Kapan | Siapa |
|---|---|---|
| Commit + push ke branch sesi | Setiap 1 perubahan logis selesai & lolos gerbang §3 | Agen |
| Buka PR (`gh pr create`) | Hanya setelah SEMUA tugas sesi selesai **dan** maintainer konfirmasi eksplisit | Agen |
| Merge PR | Dari UI GitHub, setelah CI hijau | Maintainer (bukan agen) |

**Urutan 5 langkah per sesi**
1. Pahami tugas; cek branch & tree bersih.
2. Implementasi perubahan terkecil yang logis; jalankan gerbang §3.
3. Commit (pesan §4) + push segera ke branch sesi. Tanpa PR. Dilarang menumpuk commit lokal.
4. Semua tugas selesai + konfirmasi maintainer → `gh pr create` dengan ringkasan, daftar
   verifikasi lokal, rujukan commit/TODO.
5. `gh pr checks --watch` sampai hijau. Merah → diagnosis dulu (lihat di bawah), 1 push
   perbaikan per tahap. Hijau → laporan + STOP. Rekap di body PR: commit, diagnosis run merah
   (bila ada), sisa pekerjaan (handoff).

**Kedisiplinan push & CI**
- Push itu mahal (kuota CI). Dilarang trial-and-error lewat CI.
- CI merah: JANGAN langsung push lagi. Baca log penuh: `gh run view <id> --log-failed`.
  Jika gagal dengan EOF/blob storage, fallback ke step summary yang ditulis workflow
  (`$GITHUB_STEP_SUMMARY`) atau komentar PR. Tulis diagnosis, kumpulkan SEMUA fix →
  1 commit → 1 push. Tidak boleh ada run merah tanpa penjelasan.

**Checklist pra-push permanen**
- [ ] `git status` bersih selain perubahan yang dimaksud; tidak ada file build/artefak.
- [ ] Tepat 1 perubahan logis dalam commit ini; pesan commit sesuai §4.
- [ ] Gerbang §3 dijalankan dan lolos untuk semua yang bisa diuji lokal.
- [ ] Tidak ada kredensial/keystore/.env/token di diff (`git diff --cached | grep -inE
      "password|secret|token|BEGIN (RSA|EC|OPENSSH) PRIVATE|keystore"` → harus kosong).
- [ ] Keseimbangan kurung/delimiter untuk file yang disunting (termasuk fence markdown).
- [ ] CHANGELOG.md `[Unreleased]` dan TODO.md diperbarui bila relevan.

## §3 Gerbang Kualitas Pra-Commit

**Kondisi sandbox saat ini (fakta, diverifikasi 2026-09-11):** tidak ada `java`, `gradle`,
Android SDK (`ANDROID_HOME` kosong). Artinya **build/lint/test Android TIDAK bisa dijalankan
lokal**; **CI GitHub Actions adalah validasi final** untuk kompilasi. Mitigasi wajib sebelum push:

1. **Review diff dua lapis**: (a) baca ulang tiap file yang diubah secara utuh; (b) baca
   `git diff --cached` baris per baris.
2. **Parse file konfigurasi yang disentuh**:
   - YAML workflow: `python3 -c "import yaml,sys;yaml.safe_load(open(sys.argv[1]))" <file>`
   - XML (manifest/layout/strings): `python3 -c "import xml.dom.minidom,sys;xml.dom.minidom.parse(sys.argv[1])" <file>`
   - TOML (catalog): `python3 -c "import tomllib,sys;tomllib.load(open(sys.argv[1],'rb'))" <file>`
3. **Keseimbangan kurung** untuk `.kt`/`.kts`: hitung `{`/`}` dan `(`/`)` per file
   (skrip python3 sederhana) — harus seimbang.
4. **Konsistensi package-vs-lokasi**: deklarasi `package` di setiap `.kt` harus sama dengan
   path direktori di bawah `src/main/java/`.
5. **Katalog vs build-file**: setiap dependensi/plugin di `*.gradle.kts` harus merujuk
   `libs.*` dari `gradle/libs.versions.toml`; tidak ada string versi hardcode.
6. **Security grep** (sama seperti checklist §2) atas seluruh diff.
7. **Perintah persis dari CI** (`.github/workflows/build.yml`, step pemblokir "Build debug APK"):
   `./gradlew --no-daemon --stacktrace assembleDebug` — jalankan lokal bila toolchain
   tersedia; saat ini hanya berjalan di runner CI. (Opsional lanjutan: `./gradlew --no-daemon lintDebug`.)

Bila di kemudian hari sandbox memiliki JDK + Android SDK, langkah 7 menjadi WAJIB lokal
sebelum push.

## §4 Konvensi Repo

- **Bahasa**: seluruh commit/PR/dokumen memakai **Bahasa Indonesia ringkas**.
  Subjek commit: `<tipe>: <ringkasan>` dengan tipe `feat|fix|docs|ci|build|refactor|chore`.
  Body menjelaskan **APA** dan **MENGAPA**. Footer commit mengikuti ketentuan platform Arena
  yang berlaku pada sesi (jika platform menambahkan trailer otomatis, jangan dihapus).
- **CHANGELOG.md** (kanonis, format Keep a Changelog): entri aktif di `[Unreleased]` dengan
  sub-bagian `Added/Changed/Fixed/Removed`. README hanya pointer, tidak memuat changelog.
- **TODO.md**: tabel `No. | Item | Prioritas | Status`. Status `Selesai, menunggu validasi CI`
  → `Selesai tervalidasi (PR #N)` setelah CI hijau. Riwayat tidak dihapus; item baru =
  baris baru.
- **ADR**: keputusan arsitektur ditulis di `docs/adr/NNN-judul.md` (Status/Tanggal/Konteks/
  Keputusan/Konsekuensi) + indeks `docs/adr/README.md`. ADR lama tidak ditulis ulang;
  gunakan status `Superseded by NNN`.
- **Versi** (`versionName`/`versionCode`): bump HANYA atas permintaan eksplisit maintainer,
  tidak otomatis per PR.
- **Sumber kebenaran dependensi**: `gradle/libs.versions.toml`. Dilarang hardcode versi di
  `build.gradle.kts` mana pun. Versi Gradle wrapper hanya di
  `gradle/wrapper/gradle-wrapper.properties`.
- **Dilarang commit** kredensial, keystore (`*.jks`, `*.keystore`), `.env`, `local.properties`.
  Signing rilis (bila ada) hanya lewat GitHub Secrets.
- **Identitas permanen**: `applicationId` Android diputuskan SEKALI sebelum publish dan dicatat
  di ADR (termasuk hasil cek tabrakan nama di Play Store). Perubahan setelah publish = aplikasi
  baru.
- **Artefak referensi terlarang-ubah**: saat ini tidak ada (belum ada snapshot/golden test).
  Jika nanti ditambahkan (mis. Roborazzi), daftar path dan prosedur re-record wajib ditulis di §5.

## §5 Fakta Proyek

**Keadaan repo (fakta per 2026-09-11):**
- Aplikasi Android ringan fungsi **WARP saja** (tunnel WireGuard ke Cloudflare), tanpa mode
  DNS, tanpa iklan/analitik/akun. UI Bahasa Indonesia.
- Stack: Kotlin, Android Gradle Plugin, minSdk 24, UI XML/AppCompat (tanpa Jetpack Compose
  demi RAM & ukuran APK kecil). Dependensi inti: `com.wireguard.android:tunnel` (GoBackend).
  Versi terpusat di `gradle/libs.versions.toml` (lihat CHANGELOG `[Unreleased]`).
- Modul tunggal `app/`, `applicationId`/package = **`com.rollinkxx.warp`** (ADR 001);
  nama tampilan **WARP Lite**. Sumber: `MainActivity.kt` (UI), `WarpApi.kt` (registrasi WARP
  via `api.cloudflareclient.com`), `WarpTunnel.kt` (tunnel GoBackend), `Prefs.kt` (akun).
- CI: `.github/workflows/build.yml` — trigger `push` (mengabaikan `**.md` & `docs/**`,
  lihat §5.5) + `workflow_dispatch`; job tunggal `assembleDebug`: checkout → JDK 17 temurin →
  `android-actions/setup-android` → `gradle/actions/setup-gradle` →
  `./gradlew --no-daemon --stacktrace assembleDebug` → ringkasan step → artifact `app-debug`.
  Step pemblokir: build. Step advisory: belum ada.
- Remote: `https://github.com/rollinkxx/warp.git`, default branch `main`.
- Sandbox: tanpa JDK/Gradle/Android SDK; `gh` terautentikasi.
- Dokumen: `README.md` (pointer), `CONTRIBUTING.md` (pointer ke dokumen ini), `CHANGELOG.md`,
  `TODO.md`, `docs/adr/001-identitas-aplikasi.md` + indeks.
- Path referensi terlarang-ubah: belum ada.

**Catatan teknis penting (jebakan) — diperbarui setiap kali ada temuan:**
- (2026-09-11) Branch sesi `arena/01a08e90-warp` terhapus di remote setelah sesi berakhir,
  lalu kontennya **tidak tampak** di `git fetch` biasa walaupun commit-nya masih ada.
  Pemulihan berhasil lewat `headSha` workflow run (`gh run view <id> --json headSha`) +
  `git fetch origin <sha>` — pelajaran: selalu catat SHA penting; "belum push = belum kerja"
  berlaku ganda.
- Pra-antisipasi: `gradle-wrapper.jar` biner harus ikut ter-commit (sudah); `local.properties`
  tidak boleh di-commit (di-ignore); SDK path disediakan runner.
