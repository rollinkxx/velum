# AGENTS.md — Panduan Wajib Sesi Agen (repo `rollinkxx/warp`)

Dokumen ini mengikat setiap agen coding yang bekerja di repo ini. Isinya diturunkan dari
keadaan repo yang nyata pada saat ditulis (2026-09-11) dan dari kesepakatan dengan maintainer.
Bagian yang bertanda **[direncanakan]** belum ada di repo dan baru berlaku setelah dibuat.

---

## §1 Model Sesi & Branch

- Sandbox agen bersifat **ephemeral**. Satu-satunya state yang awet adalah yang sudah
  **ter-push ke GitHub**. Prinsip: **"belum push = belum kerja"**.
- Setiap sesi Arena terikat pada satu branch berpola `arena/<id>-<suffix>`
  (sesi ini: `arena/01a08e90-warp`, bercabang dari `main` @ `76b33c9`).
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
- [ ] Gerbang §3 dijalankan dan lolos untuk semua yang bisa diuji lokal.
- [ ] Tidak ada kredensial/keystore/.env/token di diff (`git diff --cached | grep -inE "password|secret|token|BEGIN (RSA|EC|OPENSSH) PRIVATE|keystore"` → harus kosong).
- [ ] CHANGELOG.md `[Unreleased]` dan TODO.md diperbarui bila relevan.
- [ ] Pesan commit sesuai §4.

## §3 Gerbang Kualitas Pra-Commit

**Kondisi sandbox saat ini (fakta):** tidak ada `java`, `gradle`, Android SDK
(`ANDROID_HOME` kosong). Artinya **build/lint/test Android TIDAK bisa dijalankan lokal**;
**CI GitHub Actions adalah validasi final** untuk kompilasi. Mitigasi wajib sebelum push:

1. **Review diff dua lapis**: (a) baca ulang tiap file yang diubah secara utuh; (b) baca
   `git diff --cached` baris per baris.
2. **Parse file konfigurasi yang disentuh**:
   - YAML workflow: `python3 -c "import yaml,sys;yaml.safe_load(open(sys.argv[1]))" <file>`
   - XML (manifest/layout/strings): `python3 -c "import xml.dom.minidom,sys;xml.dom.minidom.parse(sys.argv[1])" <file>`
   - TOML (catalog): `python3 -c "import tomllib,sys;tomllib.load(open(sys.argv[1],'rb'))" <file>`
3. **Keseimbangan kurung** untuk `.kt`/`.kts`: hitung `{`/`}` dan `(`/`)` per file
   (`python3 - <<'EOF' ... EOF` sederhana) — harus seimbang.
4. **Konsistensi package-vs-lokasi**: deklarasi `package` di setiap `.kt` harus sama dengan
   path direktori di bawah `src/main/java/`.
5. **Katalog vs build-file**: setiap dependensi/plugin di `*.gradle.kts` harus merujuk
   `libs.*` dari `gradle/libs.versions.toml`; tidak ada string versi hardcode.
6. **Security grep** (sama seperti checklist §2) atas seluruh diff.
7. Perintah persis dari CI **[direncanakan]** — salin ke sini saat workflow dibuat, dan
   jalankan lokal bila toolchain tersedia:
   - `./gradlew --no-daemon assembleDebug`
   - (opsional lanjutan) `./gradlew --no-daemon lintDebug`

Bila di kemudian hari sandbox memiliki JDK + Android SDK, langkah 7 menjadi WAJIB lokal
sebelum push.

## §4 Konvensi Repo

- **Bahasa**: sejarah repo hanya 1 commit berbahasa Inggris (`Initial commit`); karena repo
  praktis baru, seluruh commit/PR/dokumen selanjutnya memakai **Bahasa Indonesia ringkas**.
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
- **Sumber kebenaran dependensi**: `gradle/libs.versions.toml` **[direncanakan]**. Dilarang
  hardcode versi di `build.gradle.kts` mana pun. Versi Gradle wrapper hanya di
  `gradle/wrapper/gradle-wrapper.properties`.
- **Dilarang commit** kredensial, keystore (`*.jks`, `*.keystore`), `.env`, `local.properties`.
  Signing rilis (bila ada) hanya lewat GitHub Secrets.
- **Identitas permanen**: `applicationId` Android diputuskan SEKALI sebelum publish dan dicatat
  di ADR (termasuk hasil cek tabrakan nama di Play Store). Perubahan setelah publish = aplikasi
  baru.
- **Artefak referensi terlarang-ubah**: saat ini tidak ada (belum ada snapshot/golden test).
  Jika nanti ditambahkan (mis. Roborazzi), daftar path dan prosedur re-record wajib ditulis di §5.

## §5 Fakta Proyek

**Keadaan repo saat dokumen ini dibuat (fakta):**
- Isi: `README.md` (1 baris: `# warp`) + dokumen ini. Belum ada kode, CI, docs, catalog.
- Remote: `https://github.com/rollinkxx/warp.git`, default branch `main`.
- Sandbox: tanpa JDK/Gradle/Android SDK; `gh` terautentikasi sebagai `arena-ai-coding-agent[bot]`.

**Rencana proyek yang disepakati dengan maintainer [direncanakan]:**
- Tujuan: aplikasi Android ringan fungsi **WARP saja** (tunnel WireGuard ke Cloudflare),
  tanpa mode DNS, tanpa iklan/analitik/akun. UI Bahasa Indonesia.
- Stack: Kotlin, Android Gradle Plugin, minSdk 24, UI XML/AppCompat (tanpa Jetpack Compose
  demi RAM & ukuran APK kecil). Dependensi inti: `com.wireguard.android:tunnel` (GoBackend).
- Struktur modul: modul tunggal `app/`, package `id.warp.lite` (**keputusan final
  applicationId harus dicatat di ADR 001 sebelum publish**):
  - `MainActivity.kt` — UI (tombol sambung/putus, status, uji koneksi)
  - `WarpApi.kt` — registrasi & pengambilan konfigurasi WARP (`api.cloudflareclient.com`)
  - `WarpTunnelService.kt` — foreground service tunnel WireGuard
  - `Prefs.kt` — penyimpanan akun (SharedPreferences)
- CI: `.github/workflows/build.yml` — trigger `push` + `workflow_dispatch`; job tunggal
  `build`: checkout → JDK 17 → Android SDK → `./gradlew --no-daemon assembleDebug` →
  upload artifact `app-debug.apk`. Step pemblokir: build. Step advisory: (belum ada).
  Durasi normal: belum terukur (isi setelah run pertama hijau).
- Path referensi terlarang-ubah: belum ada.

**Catatan teknis penting (jebakan CI) — diperbarui setiap kali ada temuan:**
- Belum ada run CI. Tambahkan entri di sini setiap kali run merah didiagnosis
  (format: tanggal — gejala — akar masalah — perbaikan — commit).
- Pra-antisipasi: `gradle-wrapper.jar` biner harus ikut ter-commit atau workflow memakai
  `gradle/actions/setup-gradle` untuk menyediakan wrapper; `local.properties` tidak boleh
  di-commit (di-ignore), SDK path disediakan oleh runner.
