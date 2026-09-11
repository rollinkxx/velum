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
  - Catatan pemulihan (2026-09-11): seluruh pekerjaan awal proyek berasal dari sesi
    `arena/01a08e90-warp` dan dipulihkan ke branch ini lewat dua merge (`26104f6`, lalu
    `4e0c75a`). Branch itu sempat dikira terhapus dari remote — ternyata hanya tidak
    terlihat karena refspec fetch sandbox yang terbatas (lihat Catatan teknis di §5).
- Semua kerja HANYA di branch sesi. Dilarang `checkout`/`switch`/membuat branch lain,
  dilarang push ke branch lain.
- Sebelum mulai tugas apa pun: `git branch --show-current`, `git status` — tree harus
  bersih — **dan bandingkan `git log` HEAD dengan `git ls-remote origin`** (alasan di §5).
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
3. Commit (pesan §4) + push segera ke branch sesi. Tanpa PR. Dilarang menumpuk commit lokal
   (pengecualian: model paket di bawah).
4. Semua tugas selesai + konfirmasi maintainer → `gh pr create` dengan ringkasan, daftar
   verifikasi lokal, rujukan commit/TODO.
5. `gh pr checks --watch` sampai hijau. Merah → diagnosis dulu (lihat di bawah), 1 push
   perbaikan per tahap. Hijau → laporan + STOP. Rekap di body PR: commit, diagnosis run merah
   (bila ada), sisa pekerjaan (handoff).

**Model paket (amandemen 2026-09-11, atas perintah maintainer)**
- Bila maintainer memerintahkan beberapa tugas berkaitan sebagai satu paket: implementasikan
  semuanya → gerbang lokal menyeluruh → 1–N commit (tetap 1 per perubahan logis) dalam
  **1 push gabungan** di akhir paket → 1 run CI di tree ujung (hemat kuota). Workflow memakai
  `concurrency: cancel-in-progress` per-ref sehingga push beruntun aman.
- Batas keras: **dilarang mengakhiri giliran kerja dengan commit/perubahan yang belum
  terpush** — jendela sandbox ephemeral (insiden 2026-09-11) berlaku penuh.
- Uji lokal semua yang bisa diuji tetap wajib; bagian yang tidak bisa diuji lokal
  (toolchain absen) divalidasi oleh run CI ujung-paket — CI adalah validasi final,
  BUKAN alat coba-coba.

**Kedisiplinan push & CI**
- Push itu mahal (kuota CI). Dilarang trial-and-error lewat CI.
- CI merah: JANGAN langsung push lagi. Baca log penuh: `gh run view <id> --log-failed`.
  Jika gagal dengan EOF/blob storage, fallback ke step summary yang ditulis workflow
  (`$GITHUB_STEP_SUMMARY`), komentar PR, atau endpoint `gh api` (lihat §5). Tulis diagnosis,
  kumpulkan SEMUA fix → 1 commit → 1 push. Tidak boleh ada run merah tanpa penjelasan.

**Checklist pra-push permanen**
- [ ] `git status` bersih selain perubahan yang dimaksud; tidak ada file build/artefak.
- [ ] HEAD berada di ujung yang diharapkan (`git log --oneline -2` cocok dengan
      `git ls-remote origin <branch-sesi>`).
- [ ] Tepat 1 perubahan logis dalam commit ini; pesan commit sesuai §4.
- [ ] Gerbang §3 dijalankan dan lolos untuk semua yang bisa diuji lokal.
- [ ] Tidak ada kredensial/keystore/.env/token di diff (`git diff --cached | grep -inE
      "password|secret|token|BEGIN (RSA|EC|OPENSSH) PRIVATE|keystore"` → harus kosong).
- [ ] Keseimbangan kurung/delimiter untuk file yang disunting (termasuk fence markdown).
- [ ] CHANGELOG.md `[Unreleased]` dan TODO.md diperbarui bila relevan.

## §3 Gerbang Kualitas Pra-Commit

**Kondisi sandbox saat ini (fakta, diverifikasi 2026-09-11):** tidak ada `java`, `gradle`,
Android SDK (`ANDROID_HOME` kosong); modul python `yaml` juga tidak terpasang. Artinya
**build/lint/test Android TIDAK bisa dijalankan lokal**; **CI GitHub Actions adalah validasi
final** untuk kompilasi. Mitigasi wajib sebelum push:

1. **Review diff dua lapis**: (a) baca ulang tiap file yang diubah secara utuh; (b) baca
   `git diff --cached` baris per baris.
2. **Parse file konfigurasi yang disentuh**:
   - YAML workflow: `python3 -c "import yaml,sys;yaml.safe_load(open(sys.argv[1]))" <file>`
     (bila modul `yaml` tersedia; bila tidak — review manual + andalkan bahwa workflow
     yang sama sudah terbukti hijau di run sebelumnya)
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

**Keadaan repo (fakta per 2026-09-11, gabungan audit dua sesi):**
- Aplikasi Android ringan fungsi **WARP saja** (tunnel WireGuard ke Cloudflare), tanpa mode
  DNS, tanpa iklan/analitik/akun. UI Bahasa Indonesia.
- **Stack aktual** (dari `gradle/libs.versions.toml`, satu-satunya sumber versi): Gradle 8.9
  (wrapper ter-commit, termasuk `gradle-wrapper.jar`), AGP 8.7.3, Kotlin 2.0.21, JDK 17,
  compileSdk/targetSdk 35, minSdk 24. Dependensi runtime hanya `androidx.appcompat` dan
  `com.wireguard.android:tunnel` (GoBackend) — tanpa Compose/OkHttp/coroutine demi ukuran
  APK & RAM kecil. `gradle.properties`: configuration-cache & build-cache aktif,
  `nonTransitiveRClass`. Resource hanya Bahasa Indonesia (`resourceConfigurations += "in"`).
- **Identitas (ADR 001):** `applicationId` = `com.rollinkxx.warp` (debug: suffix `.debug`),
  package Kotlin `com.rollinkxx.warp`, nama aplikasi **WARP Lite**, versi awal `0.1.0`/code 1.
- **Struktur modul `app/`** (`app/src/main/java/com/rollinkxx/warp/`):
  - `MainActivity.kt` — UI satu layar (View XML), satu executor latar; panel info interaktif
    (durasi/endpoint/hasil uji+DC), izin notifikasi Android 13+, pintasan pengaturan VPN.
  - `WarpApi.kt` — registrasi/hapus registrasi ke `api.cloudflareclient.com/v0a2158`
    (flag `warp_enabled: true`), auto-heal akun lama via GET+daftar ulang (fail-safe),
    parse `cdn-cgi/trace` (warp/colo/ip). HttpURLConnection + org.json.
  - `WarpTunnel.kt` — singleton `Tunnel` untuk `GoBackend` (MTU 1280, DNS 1.1.1.1/1.0.0.1,
    AllowedIPs 0.0.0.0/0 + ::/0, keepalive 25).
  - `Prefs.kt` — SharedPreferences `warp` (+ memo `warpEnabled`, `wasUp`).
  - `BootReceiver.kt` — sambung ulang setelah boot bila terakhir UP & izin VPN berlaku.
  - `StatusNotifier.kt` — notifikasi persisten status (kanal `status`, IMPORTANCE_LOW).
  - `AndroidManifest.xml` — VpnService milik library (`GoBackend$VpnService`) di-merge
    (`tools:node="merge"`) untuk menambah `foregroundServiceType="specialUse"` + property
    subtype `vpn`; receiver boot exported; izin RECEIVE_BOOT_COMPLETED & POST_NOTIFICATIONS.
  - Tema gelap murni resource (drawable shape/ripple/selector; tanpa font eksternal);
    ikon adaptif vektor + PNG polos untuk API 24–25.
  - Rilis: `signingConfigs.release` membaca env (`KEYSTORE_FILE/PASSWORD/ALIAS/KEY_PASSWORD`);
    minify+R8 aktif; `proguard-rules.pro` keep `com.wireguard.**`.
- **CI (`.github/workflows/build.yml`):** trigger `push` semua branch (paths-ignore `**.md`,
  `docs/**`) + `workflow_dispatch`; `concurrency: cancel-in-progress` per-ref.
  Job `assembleDebug` (pemblokir): checkout → setup-java 17 temurin (**v5**) →
  android-actions/setup-android@v3 → gradle/actions/setup-gradle@v4 →
  `./gradlew --no-daemon --stacktrace assembleDebug` → step summary → artifact `app-debug`.
  Step advisory: "Ringkasan (fallback log)" (`if: always()`), tidak memblokir.
  Job opsional `release` (needs: build; `if: vars.ENABLE_RELEASE_SIGNING == 'true'`):
  decode keystore dari Secrets → `assembleRelease` → artifact `app-release`.
  Yang harus diset maintainer: Secrets `SIGNING_KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
  `KEY_ALIAS`, `KEY_PASSWORD` + variable `ENABLE_RELEASE_SIGNING=true`.
  Run hijau: 34562586434 (3m38s, `26104f6`) & 34565410965 (3m27s, `9f0adb9`).
  Durasi normal ≈ 3–4 menit (cache dingin). Artifact debug ≈ 9,1 MB (4 ABI native WireGuard,
  belum minify; release memakai minify+shrink).
- Remote: `https://github.com/rollinkxx/warp.git`, default branch `main`.
- Sandbox: tanpa JDK/Gradle/Android SDK; `gh` terautentikasi.
- Dokumen: `README.md` (pointer), `CONTRIBUTING.md` (pointer ke dokumen ini), `CHANGELOG.md`,
  `TODO.md`, `docs/adr/001-identitas-aplikasi.md` + indeks.
- Path referensi terlarang-ubah: belum ada (tidak ada snapshot test).

**Catatan teknis penting (jebakan) — diperbarui setiap kali ada temuan:**
- (2026-09-11, run 34562586434) Run pertama **hijau** tanpa perbaikan. Belum ada run merah
  yang tak terjelaskan.
- **(2026-09-11, insiden rangkap — koreksi entri lama "branch terhapus")** Branch sesi
  ternyata TIDAK pernah terhapus. Akar sebenarnya: sandbox agen di-clone dengan **refspec
  fetch terbatas** (`+refs/heads/main` saja), sehingga `origin/arena/*` tidak pernah tampak
  di `git branch -r`/`git fetch` biasa. Ground truth = **`git ls-remote origin`** (mendaftar
  semua refs), lalu ambil eksplisit:
  `git fetch origin '+refs/heads/<b>:refs/remotes/origin/<b>'`.
  Insiden kedua di hari yang sama: **sandbox ter-recreate di tengah sesi** (clone baru,
  HEAD kembali ke `76b33c9`, working tree tetap dari snapshot) — gejala khas: commit mendadak
  berisi puluhan `create mode`. Penyelamat: git **menolak push non-fast-forward**. Perbaikan:
  fetch refs eksplisit → `git reset --mixed origin/<branch-sesi>` → commit ulang di atas
  ujung yang benar. Pelajaran: verifikasi HEAD vs `ls-remote` SEBELUM commit; dan prinsip
  "belum push = belum kerja" terbukti menyelamatkan dua kali dalam sehari.
- (2026-09-11) Dari sandbox agen, `gh run download` dan `gh run view --log` gagal dengan
  **EOF ke blob storage Azure**. Gunakan `gh api repos/<owner>/<repo>/actions/runs/<id>/jobs`
  (status & waktu per step) dan `.../artifacts` (ukuran) sebagai sumber diagnosis, plus
  step summary workflow.
- (2026-09-11) Sandbox juga tidak bisa mengunduh raw.githubusercontent.com/Maven/Gradle
  langsung; ambil file referensi upstream via
  `gh api repos/.../contents/<path> -H "Accept: application/vnd.github.raw"`.
- (2026-09-11) Gradle wrapper diambil dari tag `v8.9.0` upstream; `distributionUrl` diarahkan
  manual ke `gradle-8.9-bin.zip` (file upstream di tag itu masih menunjuk rc-2).
- (2026-09-11) Workflow memakai `paths-ignore` untuk `**.md` & `docs/**` → push khusus dokumen
  TIDAK memicu CI. Konsekuensi: validasi perubahan docs sepenuhnya beban gerbang lokal §3,
  dan status TODO untuk item docs tidak membawa rujukan run CI.
- (2026-09-11, run 34565410965) Warning advisory: `actions/setup-java@v4` deprecated,
  disarankan migrasi ke `@v5`. Tidak memblokir build → dicatat sebagai TODO (No. 9),
  bukan perbaikan darurat.
- (2026-09-11) **Indikator WARP berlapis — rawan salah diagnosis.** Kolom "Using DNS over
  WARP" di `one.one.one.one/help` bernilai dari **flag akun** (`warp_enabled` pada
  registrasi `/reg`), BUKAN dari ketersambungan tunnel; sedangkan `warp=on` di
  `www.cloudflare.com/cdn-cgi/trace` membuktikan jalur ingress WARP. Akun tanpa flag:
  tunnel jalan + trace `warp=on` + DoWARP "No". Kolom DoH/DoT di halaman yang sama juga
  terbalik antara klien resmi (proxy DNS lokal → "No") dan tunnel transparan (→ "Yes").
  Paritas dicapai dengan `warp_enabled: true` saat registrasi (commit `5d427a3`); akun
  lama cukup satu kali **Daftar ulang**.
