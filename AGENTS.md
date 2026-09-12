# AGENTS.md — Panduan Wajib Sesi Agen (repo `rollinkxx/velum`)

Dokumen ini mengikat setiap agen coding yang bekerja di repo ini. Isinya diturunkan dari
keadaan repo yang nyata dan dari kesepakatan dengan maintainer. Bagian yang bertanda
**[direncanakan]** belum ada di repo dan baru berlaku setelah dibuat.
Bila fakta di §5 berubah, perbarui dokumen ini dalam **1 commit khusus** berjudul
`docs: sinkronisasi AGENTS.md` — jangan menumpuk perubahan aturan bersama perubahan kode.

## §1 Model Sesi & Branch

**Aturan portabilitas (berlaku atas seluruh §1).** §1 hanya memuat aturan yang benar untuk
**setiap** sesi. Nama branch sesi, SHA pangkal, nomor run CI, dan kronologi insiden
**dilarang ditulis di sini** — tempatnya §5 (fakta bertanggal). Identitas sesi tidak dibaca
dari dokumen, melainkan **ditemukan saat runtime** lewat ritual di bawah. Bila dokumen dan
hasil perintah berbeda, **hasil perintah yang benar**.

- Sandbox agen bersifat **ephemeral**. Satu-satunya state yang awet adalah yang sudah
  **ter-push ke GitHub**. Prinsip: **"belum push = belum kerja"**.
- Setiap sesi Arena terikat pada **satu branch sesi** berpola `arena/<id>-<suffix>`,
  bercabang dari `main`. Nilainya berbeda tiap sesi dan tidak pernah dihafal dokumen ini.
- **Ritual pra-tugas (wajib, urut, sebelum menyentuh berkas apa pun):**
  1. `B="$(git branch --show-current)"` — inilah branch sesi, satu-satunya tujuan push.
     Bila `B` tidak cocok pola `arena/*`: **berhenti** dan lapor ke maintainer.
  2. `git status --short` — bersih, selain perubahan yang memang sedang dikerjakan.
  3. `git ls-remote origin "refs/heads/$B"` — ground truth ujung remote. Jangan percaya
     `git branch -r`: refspec fetch sandbox terbatas (§5). Keluaran **kosong itu normal**
     bila branch sesi belum pernah di-push; push pertama yang akan membuatnya.
  4. Bila ref-nya ada: `git fetch origin "+refs/heads/$B:refs/remotes/origin/$B"` lalu
     `git log --oneline -3 HEAD "origin/$B"`. HEAD wajib berada **di ujung remote atau
     tepat di atasnya** (fast-forward). Bila HEAD tertinggal/menyimpang — gejala khas:
     commit mendadak berisi puluhan `create mode` — jalankan prosedur pemulihan §5
     (fetch eksplisit → `git reset --mixed origin/$B` → commit ulang) sebelum commit apa pun.
  5. Commit pangkal, bila perlu dirujuk di laporan: `git merge-base HEAD origin/main`.
     Riwayat lengkap tidak bisa dibaca dari `git log` (clone sandbox dangkal, §5) —
     pakai `gh api "repos/<owner>/<repo>/commits?sha=<ref>"`.
- Semua kerja HANYA di branch sesi. Dilarang `checkout`/`switch`/membuat branch lain,
  dilarang push ke branch lain.
- Branch default repo: `main`. Agen **tidak pernah** merge ke `main` (merge mengakhiri sesi).
- Branch sesi lama milik sesi terdahulu (dan branch `dependabot/*`) **tidak boleh disentuh**:
  bukan milik sesi berjalan. Pekerjaan sesi lama yang sudah ter-merge ke `main` sudah ikut
  terbawa lewat commit pangkal — tidak perlu di-cherry-pick.
- Aturan khusus maintainer repo ini: **agen tidak mengeksekusi perubahan apa pun sebelum
  diperintahkan secara eksplisit.** Sajikan rencana dulu, tunggu perintah, baru kerjakan.
  Aturan ini berlaku PENUH walau §6 menuntut kecepatan: sebelum ada perintah, agen hanya
  boleh membaca/menganalisis dan menyajikan rencana — **tidak menyentuh berkas apa pun,
  termasuk berkas dokumen**. Yang diatur §6 hanyalah *cara* bekerja setelah perintah turun:
  rencana disajikan lengkap sekali jadi dengan asumsi & default, tanpa pertanyaan yang bisa
  disimpulkan, dan tanpa trial-and-error di CI. Yang selalu wajib izin tertulis walau sudah
  ada perintah lain: merge ke `main`, push paksa, hapus registrasi/data, ganti
  `applicationId`/identitas, bump `versionName`/`versionCode` (§4).

## §2 Aturan Emas: Push ≠ PR ≠ Merge

| Aksi | Kapan | Siapa |
|---|---|---|
| Commit + push ke branch sesi | Setiap 1 perubahan logis selesai & lolos gerbang §3 | Agen |
| Buka PR (`gh pr create`) | Hanya setelah SEMUA tugas selesai **dan** maintainer konfirmasi eksplisit | Agen |
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
- Pola overlap (riwayat 2026-09-11): setelah push batch N, boleh mengerjakan
  batch N+1 sambil memantau CI batch N; push berikutnya hanya setelah run sebelumnya hijau;
  pantau via `gh run watch <id> --exit-status --interval 15` (ambil `<id>` dari
  `gh run list --branch <branch> -L 1 --json databaseId`). Hasil sesi itu: 6 push, 3 run hijau,
  0 merah.
- Uji lokal semua yang bisa diuji tetap wajib; bagian yang tidak bisa diuji lokal
  (toolchain absen) divalidasi oleh run CI ujung-paket — CI adalah validasi final,
  BUKAN alat coba-coba.

**Kedisiplinan push & CI**
- Push itu mahal (kuota CI). Dilarang trial-and-error lewat CI.
- **Kecepatan §6 tidak boleh dibayar dengan trial-and-error di CI.** Bila penyebab
  kegagalan belum jelas: berhenti, diagnosis dulu (anotasi check-run, §5), lalu
  kumpulkan SEMUA kemungkinan perbaikan dalam satu push — bukan satu push per tebakan.
  Pelajaran nyata: memperbaiki satu peringatan lint butuh 3 run
  (34596670455 → 34597044297 → 34597362335 → 34597848316) karena penyebabnya ditebak,
  bukan dibaca.
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
- **Aset ikon wajib serempak**: `drawable/ic_launcher_foreground.xml`,
  `drawable/ic_launcher_monochrome.xml`, `values/ic_launcher_colors.xml`, dan **10 berkas**
  `mipmap-*/ic_launcher{,_round}.png` (5 densitas) menggambarkan satu desain yang sama. Bila
  desain ikon diubah, ubah **semuanya** dalam satu commit — jangan pernah menyunting PNG
  legacy tanpa menyamakan vektornya (perangkat API 24–25 memakai PNG, API 26+ memakai
  vektor; ketimpangan hanya terlihat di perangkat). PNG legacy dibuat lewat kanvas yang sama
  (gradien `icon_bg_*`, monogram pada viewport 108, kanvas 48–192 px); `android:roundIcon`
  wajib menunjuk varian bulat.
- **Glif ubin/notifikasi**: `drawable/ic_launcher_tile.xml` adalah glif **satu warna yang
  dipotong rapat** (viewport 46x54) — dipakai ubin pengaturan cepat & ikon kecil notifikasi.
  Jangan mengarahkan keduanya ke `ic_launcher_foreground.xml`: monogramnya hanya menempati
  sepertiga kanvas 108x108 sehingga tampak kecil setelah sistem menyeragamkan ukuran.
- **Artefak referensi terlarang-ubah**: saat ini tidak ada (belum ada snapshot/golden test).
  Jika nanti ditambahkan (mis. Roborazzi), daftar path dan prosedur re-record wajib ditulis di §5.

## §5 Fakta Proyek

**Keadaan repo (fakta per 2026-09-12, audit ulang setelah PR #13 ter-merge):**
- Aplikasi Android ringan fungsi **WARP saja** (tunnel WireGuard ke Cloudflare), tanpa mode
  DNS, tanpa iklan/analitik/akun. UI Bahasa Indonesia.
- **Stack aktual** (dari `gradle/libs.versions.toml`, satu-satunya sumber versi): Gradle
  **9.7.1** (wrapper ter-commit, termasuk `gradle-wrapper.jar`; naik dari 8.9 lewat PR #8),
  **AGP 9.4.0** (naik dari 8.7.3; Kotlin kini **bawaan AGP ≥ 2.2.10** — versi Kotlin
  sengaja **tidak ada lagi** di katalog), JDK 17,
  compileSdk 36, **targetSdk 36** (naik dari 35 atas izin maintainer; lihat blok
  "Konsekuensi targetSdk 36" di bawah), minSdk 24.
  **Catatan kombinasi:** sejak AGP 9.4.0 syarat resminya adalah Gradle ≥ 9.6.0 dan JDK ≥ 17
  — keduanya terpenuhi (wrapper 9.7.1, CI JDK 17), sehingga kombinasi yang dulu berada di
  luar matriks resmi (Gradle 9.7.1 + AGP 8.7.3 + Kotlin 2.0.21) **sudah tidak berlaku lagi**.
  Bila kelak muncul kegagalan Gradle yang tidak berhubungan dengan kode aplikasi, curigai
  AGP 9.x lebih dulu (API-nya banyak berubah; §4 melarang versi di luar katalog).
  Dependensi runtime hanya `androidx.appcompat` **1.8.0**,
  `androidx.activity` (Activity Result API), `com.wireguard.android:tunnel` **1.0.20260102**
  (GoBackend),
  dan `androidx.security:security-crypto` (Tink, ±1 MB) — tanpa Compose/OkHttp/coroutine demi ukuran
  APK & RAM kecil. Khusus pengujian (tidak ikut ke APK): `junit` 4.13.2 dan `org.json:json`
  **20260814**
  (bawaan `android.jar` berupa rintisan di unit test JVM). `gradle.properties`:
  configuration-cache & build-cache aktif,
  `nonTransitiveRClass`. Resource hanya Bahasa Indonesia (`resourceConfigurations += "in"`).
  `android.lint`: `textReport = true` + `textOutput` ke `build/reports/lint-results-debug.txt`
  (laporan HTML tidak terbaca dari sandbox), `abortOnError = true`.
- **Identitas (ADR 002):** `applicationId` = `com.rollinkxx.velum` (debug: suffix `.debug`),
  package Kotlin `com.rollinkxx.velum`, nama aplikasi **Velum**, versi awal `0.1.0`/code 1.
- **Struktur modul `app/`** (`app/src/main/java/com/rollinkxx/velum/`, 19 berkas Kotlin):
  - `MainActivity.kt` — **hanya render**: UI satu layar (View XML) yang **dirancang muat
    satu layar tanpa menggulir** (estimasi 656 dp; ScrollView hanya cadangan untuk layar
    pendek/skala huruf besar), panel info interaktif
    (durasi/endpoint/hasil uji+DC/laju+deteksi basi), izin notifikasi Android 13+ (diminta
    hanya bila perlu, lewat Activity Result API), pintasan pengaturan VPN/Always-on,
    konfirmasi Daftar ulang, salin diagnostik, judul bergradien (`polishAppTitle()`).
    Tanpa footer: ruangnya lebih berharga untuk baris aksi.
  - `VelumController.kt` — **orkestrasi** koneksi & uji, terpisah dari Activity agar tidak
    ikut mati saat Activity dibuat ulang (rotasi/proses lahir ulang).
  - `VelumApi.kt` — registrasi/hapus registrasi ke API upstream
    (flag `warp_enabled: true`), auto-heal akun lama via GET+daftar ulang (fail-safe),
    retry registrasi sekali, parse `cdn-cgi/trace` (warp/colo/ip). HttpURLConnection + org.json.
  - `VelumUpstream.kt` — konstanta upstream terpusat (`BASE` `v0a2158`, `CLIENT_VERSION`,
    User-Agent, rentang anycast) + `isClientRejected` — satu tempat bila upstream berubah.
  - `VelumTunnel.kt` — singleton `Tunnel` untuk `GoBackend` (MTU 1280, DNS 1.1.1.1/1.0.0.1,
    AllowedIPs 0.0.0.0/0 + ::/0, keepalive 25) + `traffic()` (rx/tx/handshake), endpoint efektif hasil proba.
  - `Prefs.kt` — penyimpanan terenkripsi (`EncryptedSharedPreferences`, migrasi sekali
    dari file polos `warp`) + memo `warpEnabled`, `wasUp`, `speedEndpoint` (hasil proba 1 jam).
  - `BootReceiver.kt` — sambung ulang setelah boot bila terakhir UP & izin VPN berlaku.
  - `ReconnectMonitor.kt` — pantulan tunnel saat jaringan berganti (backoff+debounce),
    lingkup aplikasi; start/stop dari UI & boot, pulihkan sesi proses lahir ulang.
  - `EndpointProbe.kt` — proba RTT paralel kandidat anycast saat connect & saat pantulan
    (±6 dtk, cache 1 jam, fail-safe ke endpoint registrasi).
  - `StatusNotifier.kt` — notifikasi persisten status (kanal `status`, IMPORTANCE_LOW).
  - `VelumTileService.kt` — ubin pengaturan cepat (sambung/putus tanpa membuka aplikasi;
    varian `startActivityAndCollapse(PendingIntent)` di API 34+ agar bebas API usang).
  - `AppExclusionActivity.kt` — split tunneling: pilih aplikasi yang **dikecualikan** dari
    tunnel; daftar dibatasi `<queries>` peluncur (tanpa `QUERY_ALL_PACKAGES`); bilah atas
    dengan tombol **Kembali** (`onBackPressedDispatcher`, bukan `onBackPressed` usang) dan
    keterangan bila daftar aplikasi kosong.
  - `VelumInsets.kt` — padding bilah sistem untuk tampilan **edge-to-edge** yang dipaksakan
    sejak `targetSdk` 36; dipanggil dari akar layout (`@+id/root`) kedua activity. Pada
    perangkat/jendela non-edge-to-edge insets bernilai nol sehingga tidak menggandakan jarak.
  - **Berkas murni (tanpa Android framework) — semuanya teruji unit JVM:**
    `VelumFormat.kt` (parse trace, pemformatan, pemilihan endpoint),
    `VelumTestDecision.kt` (RETRY/PUBLISH/DROP — mencegah false negative "Belum lewat Velum"),
    `VelumError.kt` (klasifikasi NETWORK vs penolakan klien → pesan spesifik),
    `VelumRegistration.kt` (validasi respons `POST /reg`, port WG 2408),
    `VelumMigration.kt` (rencana migrasi data era polos, konservatif),
    `VelumDiagnostics.kt` (ringkasan gangguan **ramah privasi**: tanpa kunci/IP/token).
    Uji padanannya di `app/src/test/java/com/rollinkxx/velum/*Test.kt` (6 berkas).
  - `AndroidManifest.xml` — VpnService milik library (`GoBackend$VpnService`) di-merge
    (`tools:node="merge"`) untuk menambah `foregroundServiceType="specialUse"` + property
    subtype `vpn`; receiver boot exported; service ubin QS (`BIND_QUICK_SETTINGS_TILE`,
    ikon `@drawable/ic_launcher_tile`); `AppExclusionActivity` (not exported);
    `android:icon` + **`android:roundIcon`**; blok `<queries>` peluncur; izin
    RECEIVE_BOOT_COMPLETED & POST_NOTIFICATIONS.
  - Tema gelap murni resource (drawable shape/ripple/selector; tanpa font eksternal).
    **Ikon** (diperbarui 2026-09-12): kartu gelap bergradien + monogram "V" emas, dengan
    lapisan **monokrom** (ikon tematik Android 13+) dan varian **bulat**
    (`mipmap-anydpi-v26/ic_launcher_round.xml`). PNG legacy API 24–25 (5 densitas,
    `ic_launcher.png` + `ic_launcher_round.png`) dibangkitkan dari kanvas yang sama sehingga
    tampil identik dengan vektor adaptif; aturan kesetaraannya ada di §4.
  - **Ikon baris aksi** (`drawable/ic_back|ic_chevron|ic_refresh|ic_settings|ic_apps|ic_copy.xml`)
    digambar sendiri sebagai vektor sederhana — **dilarang** menyalin berkas dari pustaka
    ikon pihak ketiga (lisensi & ukuran); warna diatur lewat `android:tint` di layout.
  - Rilis: `signingConfigs.release` membaca env (`KEYSTORE_FILE/PASSWORD/ALIAS/KEY_PASSWORD`);
    minify+R8 aktif; `proguard-rules.pro` keep `com.wireguard.**`.
  - **Pemecahan APK per ABI** (`splits.abi`, aktif 2026-09-12): `arm64-v8a`, `armeabi-v7a`,
    `x86_64` + `isUniversalApk = true`. `x86` 32-bit sengaja dibuang. `versionCode` per
    varian di-override lewat `androidComponents.onVariants`
    (`abiCode * 1000 + versionCode`, peta `armeabi-v7a`=1, `x86_64`=2, `arm64-v8a`=3);
    universal tidak diubah sehingga nilainya terendah — varian spesifik selalu menang.
    **Konsekuensi yang mudah terlupa:** nama keluaran bukan lagi `app-debug.apk`/
    `app-release.apk`, jadi setiap path artifact/rilis WAJIB memakai pola `*.apk`.
- **Konsekuensi targetSdk 36 (diterapkan 2026-09-12, atas izin maintainer):**
  - **Edge-to-edge dipaksakan** sistem pada Android 16; opt-out tidak tersedia. Akar
    layout kedua activity diberi `@+id/root` dan padding bilah sistem dipasang lewat
    `VelumInsets.kt`. Menambah layar baru **wajib** memanggil `VelumInsets.applySystemBars`
    pada akar layoutnya, jika tidak isinya tertutup bilah status/navigasi.
  - **Predictive back dipaksakan**: `onBackPressed()` tidak lagi dipanggil dan
    `KEYCODE_BACK` tidak lagi dikirim. Di repo ini aman (tidak ada pemakaian usang);
    pintasan kembali baru memakai `onBackPressedDispatcher`. Jangan menambahkan
    `onBackPressed()` baru.
  - **Izin layanan latar depan diperketat.** Android dapat menolak `startForeground` atau
    menutup layanan VPN. `VelumError.Kind.SERVICE_BLOCKED` mengenali pola pesan tersebut
    (daftar `SERVICE_MARKERS`, termasuk rantai penyebab) dan `VelumController.messageFor`
    menampilkan `R.string.err_connect_closed` — langkah pemulihannya: periksa Always-on VPN
    & blokir koneksi tanpa VPN. Klasifikasi ini **heuristik** (menebak dari teks, bukan kode
    error), karena itu teruji unit di `VelumErrorTest`.
  - **Yang belum diuji perangkat:** seluruh tiga poin di atas hanya tervalidasi kompilasi
    oleh CI. Perilaku nyata targetSdk 36 (penolakan FGS, tampilan insets) **wajib** dicoba
    maintainer lewat APK `app-preview` sebelum dirilis.
- **16 KB page size (belum ditangani, catatan untuk maintainer):** sejak Android 15 ada
  perangkat berpaginasi memori 16 KB, dan Play (mulai November 2025, tenggat bergeser ke
  Mei 2026) menolak unggahan yang pustaka native-nya hanya selaras 4 KB. Velum mengirim
  `.so` WireGuard jadi hal ini **berpotensi** relevan walau distribusi lewat GitHub Releases
  (bukan Play). Belum diverifikasi apakah `.so` bawaan `com.wireguard.android:tunnel` sudah
  selaras 16 KB; cara memeriksa: `check_elf_alignment.sh` / APK Analyzer pada APK rilis, atau
  bump versi library bila ternyata belum. **Jangan mengubah versi dependensi tanpa perintah.**
- **CI (`.github/workflows/build.yml`) — 2 job** (dikonsolidasikan 2026-09-12 dari 4 job):
  trigger `push` semua branch (paths-ignore
  `**.md`, `docs/**`) + `workflow_dispatch`; `concurrency: cancel-in-progress` per-ref;
  `permissions: contents: read`. Semua job memakai actions/checkout@**v7** →
  setup-java@**v5** (17 temurin) → android-actions/setup-android@**v4** →
  gradle/actions/setup-gradle@**v6** → actions/upload-artifact@**v7**.
  1. `verifikasi (build, tes, lint)` (**pemblokir**) — satu job berisi seluruh verifikasi
     kode, urut: `testDebugUnitTest` (id `unit_test`) → `assembleDebug` (id `assemble`) →
     `lintDebug` (id `lint`). Alasan urutan: tugas termurah gagal lebih dulu. Semua tahap
     memakai `if: always()` sehingga satu run melaporkan seluruh masalah sekaligus, bukan
     berhenti di kegagalan pertama. **Lint tetap advisori** lewat `continue-on-error` di
     level *step* (bukan job). Anotasi: `anotasikan-tes.py` + `anotasikan-log.py` dijaga
     `if: always()` dan diberi penjaga `[ -f ... ]` karena log mungkin belum sempat ditulis.
     Artifact: `app-debug` (hanya bila sukses), `unit-test-report`, `lint-report`.
     Step summary memuat kesimpulan tiap tahap + versi Gradle/AGP terdeteksi.
     **Mengapa digabung:** tiga job lama mengulang checkout+JDK+SDK+Gradle (±1,5 menit
     masing-masing) dan memakai tiga cache Gradle terpisah; digabung, toolchain disiapkan
     sekali dan cache konfigurasi/build dipakai ulang antar tugas (hemat ±60% waktu runner).
  2. `release` (opsional; `needs: [verify]`, `if: vars.ENABLE_RELEASE_SIGNING == 'true'`):
     decode keystore dari Secrets → `assembleRelease` → artifact `app-release`.
     Yang harus diset maintainer: Secrets `SIGNING_KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
     `KEY_ALIAS`, `KEY_PASSWORD` + variable `ENABLE_RELEASE_SIGNING=true`.
  - Skrip anotasi di `.github/scripts/`: `anotasikan-log.py` (baris `e: Berkas.kt: (baris,
    kolom): pesan` → anotasi error berlokasi) dan `anotasikan-tes.py` (JUnit XML → anotasi
    per failure/error). Ada karena log CI tidak terbaca dari sandbox (lihat catatan teknis).
  - `.github/dependabot.yml`: ekosistem `gradle` (mingguan) & `github-actions` (bulanan),
    maks. 5 PR, prefix commit `build`/`ci`. Dependabot hanya membuka PR — **manusia yang
    memutuskan**, dan `gradle/libs.versions.toml` tetap satu-satunya sumber versi.
  - **Run acuan terkini (branch sesi `arena/01a09481-velum`):** 34683624375
    (`a294f2c`, **5m18s**, hijau) — perbaikan tata letak layar utama (muat satu layar,
    footer dihapus). Artifact tidak berubah ukurannya dari run sebelumnya
    (`app-preview`/`app-release` 12,21 MB · `app-debug` 26,45 MB): perubahan hanya di
    resource layout. Anotasi lint tetap sama seperti daftar di bawah (tanpa temuan baru).
  - **Run sebelumnya di branch sesi:** 34681658613
    (`4f211ee`, **5m12s**, hijau) — paket ikon baru + baris aksi + targetSdk 36 +
    pembersihan lint. Artifact: `app-release` **12,21 MB** · `app-preview` 12,21 MB ·
    `app-debug` 26,45 MB · `mapping-preview` 0,59 MB. Naik ±0,3 MB dari sebelumnya:
    ±100 KB di antaranya PNG ikon baru (ikon lama nyaris kosong: 115-412 byte karena
    hanya warna datar, ikon baru bergradien sehingga 3-19 KB per berkas x10), sisanya
    dari tata letak & dex baru. Langkah lint kini **success** (bukan lagi keluar kode 1):
    temuan `UseAppTint`/`UnusedResources`/`NestedWeights` sudah dibereskan.
  - **Run acuan sebelumnya (ujung `main`):** 34676712159 (`a6c6814`, **6m4s**, hijau) —
    run **pertama yang membangun persis ujung `main` setelah PR terakhir di-merge**;
    inilah bukti yang menjawab jebakan "PR hijau ≠ ujung `main` hijau" (lihat catatan
    teknis). Dua job: `verifikasi (build, tes, lint)` 23 step · `assembleRelease
    (bertanda tangan)` 14 step, keduanya success. Artifact: `app-release` **11,92 MB** ·
    `app-preview` 11,92 MB · `app-debug` 26,03 MB · `mapping-preview` 0,59 MB ·
    `unit-test-report` & `lint-report` 0,01 MB. Anotasi: **0 error**, 10 peringatan lint
    advisori (lihat blok advisory di bawah).
  - **Run acuan sebelumnya:** 34671312706 (`a74c1e7`, **7m19s**, hijau) — **run pertama
    dengan job rilis benar-benar berjalan**. Maintainer mengisi Secrets keystore
    2026-09-12, jadi `vars.ENABLE_RELEASE_SIGNING` kini `true` dan job `release`
    **tidak lagi di-skip** — perkirakan durasi CI ±7 menit, bukan ±5.
    Artifact: `app-release` **11,93 MB** (≈2,98 MB per ABI) · `app-preview` 11,93 MB ·
    `app-debug` 26,03 MB. Rilis vs preview hanya beda **100 byte**: keduanya identik
    kecuali tanda tangan, yang memang membuktikan preview layak jadi cerminan rilis.
    - Job rilis kini memverifikasi hasilnya dengan `apksigner`. **Jangan hapus langkah
      itu**: `signingConfig` hanya terpasang bila `KEYSTORE_FILE` terisi, sehingga
      Secret yang salah membuat Gradle tetap menghasilkan APK **tanpa tanda tangan**
      dan CI tetap hijau — kegagalan senyap yang baru ketahuan di tangan pengguna.
      Langkah itu juga menolak APK berkunci debug (kunci debug seragam di semua mesin,
      siapa pun bisa menerbitkan "pembaruan" palsu).
    - Sidik jari SHA-256 tercetak di step summary dan **wajib sama di setiap rilis**;
      berubah = pengguna lama tidak bisa memperbarui.
  - **Run acuan AGP 9:** 34669207614 (`42b94bb`, **5m08s**, hijau **percobaan pertama**)
    — **AGP 9.4.0**. Artifact: `app-debug` 26,03 MB · `app-preview` **11,93 MB** ·
    `mapping-preview` 613 KB. Ukuran praktis tidak berubah dari AGP 8.7.3
    (preview -0,16 MB, debug +0,20 MB); nilai bump ini kepatuhan, bukan performa.
    - AGP 9 **menghapus** API yang dipakai repo ini, jadi bump versi saja pasti gagal —
      itu sebab PR #5 Dependabot merah. Yang wajib ikut diubah: hapus plugin
      `org.jetbrains.kotlin.android` (Kotlin kini bawaan AGP, plugin lama ditolak),
      hapus `kotlinOptions` (ikut `compileOptions.targetCompatibility`),
      `resourceConfigurations` → `androidResources.localeFilters`, `compileSdk` ≥ 36.
    - **Versi Kotlin tidak lagi ada di katalog.** AGP membawa KGP-nya sendiri (≥ 2.2.10).
      Jangan menambahkannya kembali "supaya eksplisit" — itu membuat sumber kebenaran
      kedua yang bisa menyimpang dari KGP yang sebenarnya dipakai.
    - **`targetSdk` tetap 35 secara sengaja**, walau `compileSdk` 36. Menaikkan `targetSdk`
      mengubah perilaku runtime (izin, layanan latar depan, VPN) dan **butuh izin
      maintainer + uji perangkat**; itu keputusan produk, bukan pemeliharaan alat bangun.
    - Syarat versi AGP 9.4: Gradle ≥ 9.6.0 (wrapper di 9.7.1) dan JDK ≥ 17 (CI di 17).
  - **Run acuan varian preview:** 34668310746 (`a0d20fc`, hijau) — build pertama dengan varian
    **preview** (konfigurasi release + R8, ditandatangani kunci debug). Artifact:
    `app-debug` 25,83 MB · `app-preview` **12,09 MB** · `mapping-preview` 613 KB.
    R8 memangkas **±3,4 MB per APK** (dex+resources), sehingga preview per-ABI **±2,2 MB**
    lawan debug per-ABI ±5,6 MB — **-53%** pada total artifact.
    - Varian preview dibangun **setiap push**, disengaja: R8 hanya aktif di `release`, dan
      `release` tak bisa dipasang tanpa keystore. Tanpa preview, R8 baru dijalankan pertama
      kali saat rilis publik. **Jangan hapus step ini** untuk menghemat waktu CI (+ ~1 menit).
    - `app/proguard-rules.pro` wajib memuat keep untuk **field protobuf Tink**
      (`-keepclassmembers class * extends ...GeneratedMessageLite { <fields>; }`).
      `EncryptedSharedPreferences` di `Prefs.kt` membaca keyset secara reflektif: tanpa
      aturan ini build tetap **sukses** lalu aplikasi **crash saat runtime** hanya pada
      varian yang diperkecil. Kegagalan senyap — tidak akan tertangkap CI, hanya di perangkat.
    - Menambah komponen baru di manifest (service/receiver/activity) → tambahkan keep-nya,
      karena sistem menginstansiasi berdasarkan nama string.
    - `mapping.txt` diunggah sebagai artifact; wajib dipakai untuk membaca stack trace dari
      APK preview/rilis, kalau tidak nama kelas tampil teracak.
  - **Run acuan pemecahan ABI:** 34667447646 (`f06c4ee`, **4m02s**, hijau) — build pertama dengan
    pemecahan ABI. Artifact `app-debug` berisi **4 APK**, total 25,8 MB: universal 9,6 MB
    (setara APK tunggal sebelum pemecahan) + tiga varian ABI **rata-rata ±5,4 MB**, yaitu
    **±44% lebih kecil** dari universal untuk pengguna akhir. Angka ini varian *debug*
    (tanpa R8); varian *release* akan lebih kecil lagi karena minify+shrink aktif.
  - **Run acuan setelah konsolidasi:** 34660850896 (`f2dae7f`, **4m04s**, 19 step hijau) —
    job tunggal, artifact `app-debug` **10,08 MB** · `unit-test-report` 12,7 KB ·
    `lint-report` 17,6 KB. Sekaligus bukti pertama Gradle 9.7.1 + AGP 8.7.3 bisa dibangun.
    Anotasi: **0 error**, 10 peringatan lint advisori (saat itu: `SharedPreferences.edit`
    KTX x3, ukuran teks 10sp, adaptive icon tanpa `monochrome`, dan ikon peluncur yang
    mengisi seluruh piksel x5). Perubahan 2026-09-12 menyelesaikan tujuh di antaranya —
    tagline 11sp, lapisan `monochrome` ditambahkan, monogram "V" ditaruh di zona aman
    72x72. Hasil nyata (run 34681373449): lint **masih** memunculkan temuan baru dari
    perubahan itu sendiri — 4 error `UseAppTint` (`android:tint` harus `app:tint` di
    proyek AppCompat), 1 `UnusedResources`, 1 `NestedWeights` — semuanya diperbaiki di
    commit lanjutan. **Pelajaran:** menambah ImageView ber-`tint` atau membungkus
    `layout_weight` di dalam `layout_weight` selalu memicu lint; periksa keduanya
    sebelum push.
  - **Sisa peringatan lint (run 34681658613, sesudah pembersihan):** 7 usulan KTX
    `SharedPreferences.edit` pada `Prefs.kt` (sengaja tidak diambil — menuntut
    dependensi `androidx.core:core-ktx` hanya untuk tiga baris idiom yang sudah benar),
    `GoBackend` static field (dari library WireGuard, bukan kode repo), `allowBackup`
    usang, dan tawaran versi `androidx.activity` yang lebih baru. Tiga advisory ikon
    yang dulu muncul (10sp, monokrome, ikon mengisi seluruh piksel) **sudah hilang**.
  - **Run hijau terakhir sebelum konsolidasi:** 34658145458 (`7a89e70`, 3m56s) — run
    pertama setelah bump keempat action; **anotasi Node.js 20 hilang** di sini (TODO 23).
  - **Run hijau bersejarah:** 34562586434 (`26104f6`) · 34565410965 (`9f0adb9`) ·
    34580968135 (`5781180`, rename) · 34581202095 (`a813b2b`) · 34586619601 (`7e6b9b3`) ·
    34592495242 (`9864b9f`) · 34594076246 (`dcbe0ce`, unitTest pertama) ·
    34597848316 (`7c441f8`) · 34602359157 (`c31710e`, anotasi CI) ·
    34608952744 (`d7469c3`, identitas UI — 2m39s, terakhir sebelum PR #3 di-merge).
  - Durasi normal ≈ 2,5–4 menit. Artifact `app-debug` ≈ **10,07 MB** (4 ABI native WireGuard,
    belum minify; release memakai minify+shrink), `unit-test-report` ≈ 11 KB,
    `lint-report` ≈ 18 KB.
- Remote: `https://github.com/rollinkxx/velum.git` (di-rename dari `warp` 2026-09-11),
  default branch `main`. **Repo diubah menjadi PUBLIK oleh maintainer 2026-09-12** —
  konsekuensi: Actions gratis tanpa batas (sebelumnya privat, kuota 2.000 menit/bulan
  dengan spending limit $0), dan seluruh riwayat commit terbaca publik.
  PR #1–#4 dan #6–**#13** sudah **merged**; `main` = **`a6c6814`** (merge PR #13,
  branch `arena/01a0915f-velum`). **Nol PR terbuka, nol issue terbuka, nol release/tag** —
  langkah terbitkan rilis di `docs/rilis-github.md` belum pernah dijalankan.
- **PR Dependabot: tidak ada lagi yang terbuka.** #5 (AGP 8.7.3 → 9.4.0) **ditutup**
  atas perintah maintainer 2026-09-12, setelah isinya diterapkan lebih lengkap di
  branch sesi (`42b94bb`, CI 34669207614 hijau). Patch #5 hanya mengubah satu baris
  `agp` di katalog, padahal AGP 9 menghapus API yang dipakai repo ini — lihat blok
  "Run acuan terkini" di atas untuk daftar migrasi yang wajib menyertainya.
  Branch `dependabot/*` **sudah tidak ada** di remote (dibersihkan GitHub setelah PR
  ditutup/di-merge) — terverifikasi 2026-09-12; yang tersisa hanya `main` + lima
  `arena/*` milik sesi lama (tidak boleh disentuh, §1).
  Bila Dependabot membuka PR AGP serupa lagi, cukup rujuk commit `42b94bb`.
- Sandbox: tanpa JDK/Gradle/Android SDK, dan **jaringan keluar diblokir**
  (`services.gradle.org`, `repo1.maven.org`, `api.adoptium.net` → SSL_ERROR_SYSCALL),
  sehingga memasang toolchain sendiri pun mustahil — CI benar-benar satu-satunya jalan
  build. `gh` terautentikasi tetapi **tanpa izin `workflow_dispatch`** (HTTP 403) dan tanpa
  akses billing/permissions; satu-satunya cara memicu CI dari sandbox adalah **push**.
  Clone **dangkal** (`git log` hanya memuat 1 commit) dengan refspec fetch terbatas.
- Dokumen: `README.md` (pointer), `CONTRIBUTING.md` (pointer ke dokumen ini), `CHANGELOG.md`,
  `TODO.md`, `docs/adr/` (001 superseded, 002 identitas Velum) + indeks,
  `docs/rilis-github.md` (runbook APK rilis GitHub).
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
- `gh run watch` berfungsi dari sandbox — gunakan untuk memantau CI; yang EOF hanya
  `gh run view --log` / `gh run download`.
- (2026-09-11) **Log CI sama sekali tidak bisa dibaca dari sandbox**: `gh run view --log`,
  `gh run view --log-failed`, dan `gh run download` semuanya EOF. Satu-satunya jalan untuk
  mendiagnosis run (terutama job lint) adalah **anotasi check-run**:
  `gh api repos/<owner>/<repo>/commits/<sha>/check-runs --jq '.check_runs[] | select(.name|test("lint")) | .id'`
  lalu `gh api repos/<owner>/<repo>/check-runs/<id>/annotations`. Konsekuensi praktis:
  job yang hasilnya hanya ada di log wajib menuliskan temuannya ke `$GITHUB_STEP_SUMMARY`
  **dan** mencetaknya ke log; untuk lint, aktifkan `lint { textReport = true }`.
- (2026-09-11) `gh pr edit --title/--body` **gagal diam-diam** (kode keluar 1, hanya
  peringatan "Projects (classic) is being deprecated") dan perubahannya tidak diterapkan.
  Pakai REST API: `gh api -X PATCH repos/<owner>/<repo>/pulls/<n> -f title="..."` dan
  `-F body=@/tmp/berkas.md`. Selalu verifikasi dengan `gh pr view <n> --json title,body`.
- (2026-09-11, PR #3) **`gh pr edit --title/--body` GAGAL dari sandbox** (keluar dengan
  kode 1, hanya mencetak peringatan "Projects (classic) is being deprecated"), dan
  perubahannya **tidak diterapkan walau tanpa pesan error**. Pakai REST API sebagai
  gantinya:
  `gh api -X PATCH repos/rollinkxx/velum/pulls/<n> -f title="<judul>"` dan
  `gh api -X PATCH repos/rollinkxx/velum/pulls/<n> -F body=@/tmp/body.md` (isi panjang
  lewat berkas sementara di luar repo). Selalu verifikasi dengan
  `gh pr view <n> --json title,body`.
- (2026-09-11) Lampiran gambar yang dikirim pengguna TIDAK bisa dibaca dari sandbox:
  path `/home/user/uploads/` tidak ada. Minta pengguna menceritakan isinya.
- (2026-09-12) **Clone sandbox itu dangkal** (`git rev-parse --is-shallow-repository` →
  `true`): `git log` hanya memperlihatkan **1 commit** dan `git branch -r` hanya `origin/main`.
  Jangan menyimpulkan "riwayat hilang". Riwayat penuh dibaca lewat
  `gh api "repos/rollinkxx/velum/commits?sha=<branch-atau-sha>"`, isi commit lewat
  `gh api repos/rollinkxx/velum/commits/<sha> --jq '.files[].filename'`.
- (2026-09-11, run merah 34601913928 — commit `901e090` `docs: sinkronisasi AGENTS.md`)
  **Satu-satunya run merah yang bukan Dependabot.** Job `unitTest` gugur di langkah
  "Pengujian unit" sementara `assembleDebug` & `lint` hijau; akar masalah: `org.json` di
  `android.jar` berupa rintisan (`Stub!`) sehingga parse respons registrasi gagal saat unit
  test JVM. Diperbaiki di `c31710e` dengan `testImplementation(libs.json)`. Pelajaran ganda:
  (a) commit dokumen pun bisa memicu CI bila ter-push bersamaan dengan perubahan kode;
  (b) anotasi check-run hanya memuat "Process completed with exit code 1" — karena itulah
  `anotasikan-tes.py` dibuat.
- (2026-09-12, run 34658567073/34658584676/34658670008/34658688817) **Kuota Actions habis —
  cara membedakannya dari kegagalan kode.** Empat run merah beruntun ternyata bukan salah
  kode: repo masih privat, jatah 2.000 menit/bulan habis, spending limit default $0.
  **Tanda khas (semuanya harus cocok):** (a) run selesai dalam **±8 detik**, jauh di bawah
  durasi normal 2,5–4 menit; (b) setiap job punya **`steps: 0`** — tidak satu langkah pun
  dieksekusi; (c) semua job gugur **pada detik yang sama**, termasuk job yang biasanya
  `continue-on-error`; (d) **anotasi kosong** — tidak ada error kompilasi maupun tes gagal.
  Bandingkan dengan kegagalan kode sungguhan (run 34601913928): job gugur satu per satu di
  step bernama, disertai anotasi. Periksa dengan
  `gh api repos/<owner>/<repo>/actions/runs/<id>/jobs --jq '.jobs[] | {name,conclusion,steps:(.steps|length)}'`.
  Jangan pernah "memperbaiki" kode berdasarkan run semacam ini. Solusi: repo dijadikan
  publik (Actions gratis tanpa batas) atau spending limit dinaikkan. Catatan: run yang mati
  begini **tidak bisa** di-`gh run rerun` ("workflow file may be broken").
- (2026-09-12) Anotasi advisory tetap muncul di setiap run: **Node.js 20 deprecated** —
  `actions/checkout@v4`, `actions/upload-artifact@v4`, `android-actions/setup-android@v3`,
  `gradle/actions/setup-gradle@v4` dipaksa berjalan di Node 24. Non-pemblokir; menunggu
  keputusan maintainer atas PR Dependabot #6/#7/#10/#11 (TODO No. 23).
- (2026-09-12) **"PR Dependabot hijau" bisa menyesatkan.** Cek CI sebuah PR dijalankan di
  **base saat PR dibuat**, bukan di ujung `main` saat di-merge. Empat PR (#9, #12, #4, #8)
  sama-sama hijau di base `d873f1e`, tetapi kombinasi hasil gabungannya (Gradle 9.7.1 dari
  #8 + AGP 8.7.3 yang tidak ikut naik) **tidak pernah dibangun sekali pun**. Sebelum
  menyimpulkan `main` sehat setelah beberapa merge beruntun, pastikan ada **satu run di
  ujung `main`** — bukan menjumlahkan status PR.
- (2026-09-12) **Ikon: tidak ada sumber vektor otomatis untuk PNG legacy.** Desain ikon
  digambar dua kali — sebagai vektor (API 26+) dan sebagai kanvas raster (API 24-25).
  Kanvas raster dibuat lewat skrip sekali pakai di sandbox (`python3` + `zlib`, tanpa
  Pillow) dan **tidak ikut ke repo**; yang tersimpan hanya hasilnya. Konsekuensi: bila
  monogram/warna ikon diubah, PNG **wajib** dibuat ulang dari kanvas yang sama (atau skrip
  serupa ditulis ulang), bukan disunting satu per satu. Ketimpangan hanya terlihat di
  perangkat API 24-25.
- (2026-09-12) **`strings.xml` pernah ditulis ulang total (bukan ditambal).** Karena sandbox
  tidak bisa mengompilasi, kesalahan nama resource tidak akan ketahuan sampai CI. Sebelum
  commit, rujukan diperiksa dengan skrip: kumpulkan semua `R.<tipe>.<nama>` dari `.kt` dan
  `@<tipe>/<nama>` dari XML, lalu pastikan setiap nama ada di `res/`. **Wajib** diulang
  setiap kali berkas resource ditulis ulang — termasuk memeriksa `@color/nama` ke
  `res/color/*.xml` (color-state-list) **dan** `values/*.xml`, karena dua tempat itu mudah
  terlewat.
- (2026-09-12) **`layout_gravity="center_vertical"` pada anak ScrollView = konten
  terpotong permanen.** Gejalanya nyata di perangkat pengguna: judul "Velum" hilang
  sebagian di layar (hanya sisa huruf bagian bawah yang terlihat) dan tidak bisa
  digulir kembali. Sebabnya: saat isi lebih tinggi dari viewport, gravity pada
  *LayoutParams* anak memindahkan seluruh isi ke atas (offset negatif), sementara
  ScrollView hanya bisa menggulir dari 0 ke bawah — bagian atasnya mustahil dicapai.
  **Cara yang benar:** `android:fillViewport="true"` pada ScrollView +
  `android:gravity="center_vertical"` pada **isi** LinearLayout. Saat isi lebih pendek,
  ia dipusatkan; saat lebih tinggi, ukurannya = tinggi alaminya sehingga tidak ada
  pergeseran. **Pelajaran tambahan:** perangkat pengguna bisa punya viewport efektif
  lebih kecil dari perkiraan (skala huruf/ukuran tampilan), jadi jangan pernah
  mengandalkan "kurang-lebih muat" — ukur dengan
  `tools/est_layout.py` (semacam ini) lalu sisakan margin lega.
- (2026-09-11) **Jebakan deteksi WARP**: `Tunnel.State.UP` dari `GoBackend` hanya berarti
  antarmuka TUN selesai dibuat, BUKAN handshake selesai; dan `HttpURLConnection` memakai
  ulang soket keep-alive yang dibuat sebelum VPN aktif (Android tidak memindahkan soket
  yang sudah terbuka ke tunnel). Dampaknya: uji `cdn-cgi/trace` bisa mengembalikan
  `warp=off` meski tunnel benar-benar UP. Wajib: `Connection: close` +
  `http.keepAlive=false`, tunggu `traffic().latestHandshakeMs > 0` sebelum uji.

## §6 Protokol Android: Presisi & Efisiensi Waktu (aktif 2026-09-11)

Setiap detik pipeline CI mahal dan setiap iterasi yang gagal membuang waktu. §6 melengkapi
§1–§5 dan mengubah kebiasaan lama yang memperlambat kerja (lihat amandemen di §1).

### Prinsip efisiensi waktu

1. **Batch pertanyaan** — bila butuh informasi, tanyakan SEMUA sekaligus dalam satu pesan.
   Maksimal satu kali bertanya; tidak ada pertanyaan bertahap.
2. **Smart defaults** — info yang tidak diberikan → pakai default stabil dan sebutkan di
   awal respons. **Isi repo selalu menang atas default protokol**: `gradle/libs.versions.toml`
   adalah satu-satunya sumber kebenaran versi (§4). Default protokol (AGP 8.5.2, Gradle 8.7,
   Kotlin 2.0.0, compileSdk/targetSdk 34, minSdk 24, JDK 17, Kotlin DSL, version catalog)
   hanya dipakai bila katalog belum menetapkannya. Keadaan nyata repo: AGP 8.7.3,
   Gradle 8.9, Kotlin 2.0.21, JDK 17, compileSdk/targetSdk 35, minSdk 24.
3. **Tanpa pertanyaan yang bisa disimpulkan** — jangan tanya hal yang sudah terjawab oleh
   log error, kode yang ada, atau §5.
4. **Solusi sekali jalan** — sebelum perintah: sajikan RENCANA lengkap sekali jadi
   (tujuan, asumsi/default, berkas terdampak, risiko) agar satu putaran persetujuan
   cukup. Setelah perintah: eksekusi lengkap, jangan menyuruh pengguna "lanjut ke
   langkah berikutnya".
5. **Antisipasi masalah turunan** — sertakan pencegahannya di respons/kode yang sama.
6. **Sadari cache** — jangan merusak cache Gradle & dependensi di CI (lihat §3 langkah 7).
7. **Kerja paralel** — bila beberapa berkas harus berubah, kerjakan semuanya dalam satu
   batch. Ini persis *model paket* di §2: N commit per perubahan logis, 1 push, 1 run CI.

### Fase eksekusi

- **Fase 0 — Intake cepat:** ekstrak semua informasi dari teks, log, dan kode. Info
  non-kritis hilang → pakai default. Info kritis hilang → batch pertanyaan maksimal 1x.
- **Fase 1 — Analisis singkat:** tujuan 1 kalimat, asumsi/default yang dipakai, versi yang
  relevan, dan daftar berkas terdampak.
- **Fase 2 — Eksekusi:** semua berkas sekaligus. Bila kode dibagikan di percakapan →
  **berkas utuh** (path di header, impor lengkap, tanpa placeholder). Bila dikirim sebagai
  pekerjaan repo → wujudkan sebagai commit per perubahan logis (§2, §4).
- **Fase 3 — Optimasi CI:** pastikan JDK/Gradle/AGP selaras; `gradle/actions/setup-gradle@v4`
  sudah menangani cache Gradle & dependensi; `org.gradle.caching=true` dan
  configuration-cache aktif di `gradle.properties`. Jangan menambahkan `--parallel` tanpa
  alasan (modul tunggal: manfaatnya nihil, risiko konfigurasi-cache justru naik).
- **Fase 4 — Perbaikan dini:** sebutkan potensi masalah turunan berikut solusinya.

### Larangan mutlak

- ❌ "coba ganti…", "kalau masih error coba…" — diagnosis dulu, baru perbaiki.
- ❌ Memberi banyak opsi — berikan satu solusi terbaik, kecuali keputusan produk yang
  memang wewenang maintainer (mis. identitas aplikasi, bump versi).
- ❌ Kode parsial/placeholder saat berkas dibagikan di percakapan.
- ❌ Pertanyaan bertahap atau pertanyaan trivial yang bisa pakai default.
- ❌ API usang atau versi yang tidak ada — job `lint (advisori)` akan menandainya dan
  wajib dijaga hijau walau tidak memblokir.
- ❌ Mengubah berkas yang tidak perlu; mengulang kode yang sudah benar.

### Format respons (permintaan kode)

🎯 Tujuan (1 kalimat) · 📌 Asumsi/default · 🔍 Akar masalah (bila perbaikan bug) ·
📂 Berkas terdampak · 💻 Implementasi (berkas utuh, path di header) · ⚙️ CI/CD (bila
workflow tersentuh) · ⚠️ Heads-up (masalah turunan + solusi) · ✅ Siap dibangun.

Bila pekerjaan dikirim sebagai commit/PR (bukan dibagikan di percakapan), susunan di
atas tetap dipakai sebagai isi laporan dan body PR.

### Pohon keputusan

```
Permintaan masuk
├─ Sudah ada perintah eksplisit?
│    ├─ Info kurang & kritis? → tanya SEKALI (batch), lalu eksekusi lengkap
│    └─ Info cukup?           → eksekusi lengkap + sebutkan asumsi/default
└─ Belum ada perintah?
     ├─ Info kurang & kritis? → tanya SEKALI (batch) untuk melengkapi rencana
     └─ Sajikan RENCANA lengkap sekali jadi, lalu TUNGGU instruksi.
        Dilarang menyentuh berkas apa pun sebelum instruksi turun (§1).
```

Protokol ini aktif sejak 2026-09-11 sampai maintainer menulis "stop protocol" atau
memulai sesi baru. Bila ada aturan lain yang bertentangan dengan §6, §6 yang menang.
