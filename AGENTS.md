# AGENTS.md — Aturan Operasional Sesi Agen

Dokumen ini **WAJIB** dibaca dan dipatuhi sebelum pekerjaan apa pun di repo ini.
Jika fakta di §5 berubah (stack dipilih, CI dibuat, dsb.), perbarui dokumen ini
dalam **1 commit khusus** berjudul `docs: sinkronisasi AGENTS.md` — jangan
menumpuk perubahan aturan bersama perubahan kode.

---

## §1 Model Sesi & Branch

- **Sandbox ephemeral**: filesystem lokal tidak awet. State yang bertahan
  SATU-SATUNYA adalah yang sudah terpush ke remote.
  Prinsip kerja: **"belum push = belum kerja"**.
- **Branch terikat sesi** (pola `arena/<id>-<suffix>`). Sesi ini:
  **`arena/01a08ecf-warp`**.
  - Semua pekerjaan HANYA di branch ini. Dilarang switch ke branch lain,
    membuat cabang lain, atau push ke branch lain.
- **Awal sesi (wajib, urut):**
  1. `git branch --show-current` → pastikan `arena/01a08ecf-warp`.
  2. `git status` → working tree harus bersih sebelum mulai.
  3. Audit repo: struktur & stack (bahasa, framework, versi toolchain),
     build system, CI workflow (`.github/workflows/*` — catat step yang
     **memblokir** vs **advisory**), perintah build/test persis dari CI,
     konvensi commit (`git log --oneline -20`), konfigurasi gaya
     (editorconfig/lint/katalog dependensi), dan dokumen yang sudah ada
     (README, CONTRIBUTING, CHANGELOG, TODO, docs/).

## §2 Aturan Emas: Push ≠ PR ≠ Merge

| Aksi | Kapan | Siapa |
|---|---|---|
| Commit + push | **1 tugas = 1 perubahan logis = 1 commit**, push segera setelah gerbang §3 lulus. Dilarang menumpuk commit lokal | Agen |
| Buka PR (`gh pr create`) | Hanya setelah SEMUA tugas selesai **dan** ada konfirmasi eksplisit maintainer. Dilarang PR di tengah pengerjaan | Agen |
| Merge ke branch default | Setelah CI hijau, dari UI GitHub oleh maintainer. **Merge mengakhiri sesi** | Maintainer (manusia) |

- **Agen DILARANG merge ke branch default**, dalam kondisi apa pun.
- **Push itu mahal (kuota CI)** — dilarang trial-and-error via CI.
  - Uji lokal SEMUA yang bisa diuji, dengan perintah persis dari workflow CI (§3).
  - Yang tidak bisa diuji lokal (toolchain tidak ada di sandbox) →
    **review diff dua lapis** (sekali sebagai reviewer, sekali sebagai
    compiler/runtime) + catatan eksplisit bahwa CI adalah validasi final.
- **CI merah → JANGAN langsung push lagi.**
  1. Baca log penuh: `gh run view --log-failed`. Bila kena error EOF blob
     storage, gunakan PR comment / step summary yang ditulis workflow.
  2. Tulis diagnosis akar-masalah.
  3. Kumpulkan SEMUA fix → 1 commit → 1 push.
  4. Tidak boleh ada run merah yang tak terjelaskan.

### Urutan 5 langkah per sesi

1. Pahami tugas + cek branch & tree bersih.
2. Implementasi perubahan terkecil yang logis; jalankan gerbang §3.
3. Commit (konvensi §4) + push ke branch sesi. **Tanpa PR.**
4. Semua tugas selesai + konfirmasi maintainer → `gh pr create`
   (ringkasan, daftar verifikasi lokal, rujukan commit/TODO).
5. `gh pr checks --watch` sampai hijau; merah → diagnosis dulu, 1 push
   perbaikan per tahap; hijau → laporan + **STOP** (maintainer yang merge).
   Akhiri sesi dengan rekap di body PR: daftar commit, diagnosis run merah
   (bila ada), dan sisa pekerjaan (handoff).

### Checklist pra-push permanen

- [ ] Di branch sesi (`arena/01a08ecf-warp`) dan `git status` bersih.
- [ ] Tepat 1 perubahan logis dalam commit ini; pesan sesuai §4.
- [ ] Grep rahasia (§3) nihil: tak ada kredensial/keystore/`.env`/private key.
- [ ] File konfigurasi yang disentuh ter-parse valid (JSON/YAML/TOML/XML).
- [ ] Keseimbangan kurung/struktur untuk file yang diedit (delimiter,
      tag pembuka/penutup, fence markdown).
- [ ] Namespace/package konsisten dengan lokasi file.
- [ ] Katalog/lockfile sinkron dengan file build (bila dependensi disentuh).
- [ ] Lint/build/test lokal hijau — perintah persis dari CI, bila toolchain
      tersedia di sandbox.
- [ ] `CHANGELOG.md` `[Unreleased]` diperbarui bila perubahan terlihat
      pengguna; `TODO.md` diperbarui bila menyentuh itemnya.

## §3 Gerbang Kualitas Pra-Commit

**Fakta repo ini (audit 2026-09-11, commit `76b33c9`): BELUM ada CI** —
tidak ada `.github/workflows/`. Begitu workflow pertama ditambahkan, SALIN
perintah build/test/lint-nya ke seksi ini (dan tandai step pemblokir vs
advisory) lewat commit `docs: sinkronisasi AGENTS.md`.

Gerbang lokal yang berlaku **sekarang** untuk setiap commit:

```bash
# 1. Tree & whitespace bersih
git status
git diff --check

# 2. Grep rahasia — hasil wajib nihil
git grep -nI -E '(BEGIN [A-Z ]*PRIVATE KEY|api[_-]?key|secret|password|token)[ =:]' \
  -- . ':!AGENTS.md'

# 3. Parse konfigurasi yang disentuh (bila ada)
python3 -m json.tool <file.json> > /dev/null
python3 -c 'import tomllib,sys; tomllib.load(open(sys.argv[1],"rb"))' <file.toml>

# 4. Keseimbangan fence markdown untuk dokumen yang disentuh
grep -c '^```' <file.md>   # wajib genap
```

- **Lint/build/test proyek**: BELUM ADA (stack belum ada).
  Mitigasi: review diff dua lapis + nyatakan di body commit/PR bahwa CI
  kelak adalah validasi final.

## §4 Konvensi Repo

- **Bahasa**: commit, PR, dan dokumen memakai **Bahasa Indonesia ringkas**
  (repo baru, belum ada konvensi lain dari sejarah). Judul commit berbentuk
  `<tipe>: <deskripsi>` — tipe: `docs`, `feat`, `fix`, `chore`, `refactor`,
  `test`, `ci`, `build`. **Body menjelaskan APA & MENGAPA.**
- **Footer commit**: mengikuti ketentuan platform sesi Arena saat ini —
  tidak ada footer wajib dari platform; tanpa footer atribusi model/agen
  kecuali maintainer memintanya.
- **CHANGELOG.md**: kanonis format [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
  entri aktif di bagian `[Unreleased]` (kelompok Added/Changed/Fixed/Removed).
  README hanya pointer — tidak menulis riwayat perubahan di README.
- **TODO.md**: tabel `No. | Item | Prioritas | Status`.
  - Status `Selesai, menunggu validasi CI` → diubah menjadi
    `Selesai tervalidasi (PR #N)` setelah CI hijau.
  - Riwayat tidak dihapus; item baru = baris baru.
- **ADR (keputusan arsitektur)**: `docs/adr/NNN-judul.md` dengan seksi
  Status / Tanggal / Konteks / Keputusan / Konsekuensi, plus indeks
  `docs/adr/README.md`. ADR lama tidak ditulis ulang — status diubah
  menjadi `Superseded by NNN`.
- **Versi** (`versionName`/`versionCode` atau semver di file rilis): bump
  HANYA atas permintaan eksplisit maintainer; jangan otomatis per PR.
- **Dependensi & versi**: HANYA lewat satu sumber kebenaran repo
  (katalog/lock — mis. `gradle/libs.versions.toml`, `package.json`+lockfile,
  `pyproject.toml`). Dilarang hardcode versi di file build lain.
  Saat ini belum ada satu pun; sumber kebenaran ditetapkan saat stack
  dipilih (lewat ADR bila berdampak arsitektur).
- **Kredensial**: dilarang commit kredensial/keystore/`.env`. Bila CI kelak
  punya security scan, jalankan grep yang sama di lokal sebelum push.
- **Identitas permanen** (Android `applicationId`, nama paket
  Play/npm/PyPI): putuskan SEKALI di awal sebelum publish; perubahan setelah
  publish = aplikasi/paket baru. Cek tabrakan nama di toko/registry
  eksternal sebelum menetapkan — catat hasilnya di ADR.
- **Artefak referensi** (mis. screenshot golden/snapshot test): daftar path
  "tidak boleh berubah tanpa prosedur re-record eksplisit" dikelola di
  §5.3 beserta prosedurnya.

## §5 Fakta Proyek

Hasil audit **2026-09-11** (commit dasar `76b33c9` "Initial commit",
branch `main`).

### 5.1 Stack & struktur

- Stack/bahasa/framework/toolchain: **BELUM ADA**.
- Isi repo hanya: `README.md` (isi: `# warp`) dan `AGENTS.md`.
- Tidak ada build system, lockfile, konfigurasi lint/editorconfig,
  maupun kode sumber.
- Remote `origin`: `https://github.com/rollinkxx/warp.git`;
  branch default: `main`.

### 5.2 CI

- Workflow CI: **BELUM ADA**. Step pemblokir vs advisory dan durasi normal
  job akan dicatat di sini saat workflow pertama dibuat.
- Konsekuensi: push saat ini **tidak memicu validasi otomatis** — gerbang
  lokal §3 adalah satu-satunya pertahanan kualitas.

### 5.3 Path artefak referensi terlarang-ubah

- **BELUM ADA**. Ketika snapshot/golden pertama hadir, daftarkan path-nya
  di sini bersama prosedur re-record eksplisitnya.

### 5.4 Dokumen & identitas

- Dokumen lain (`CHANGELOG.md`, `TODO.md`, `CONTRIBUTING.md`, `docs/`):
  **BELUM ADA** — dibuat saat pertama kali dibutuhkan, mengikuti §4.
- `applicationId` / nama paket registry: **BELUM ditetapkan** — wajib ADR +
  cek tabrakan eksternal sebelum publish pertama.

### 5.5 Catatan teknis penting (jebakan terbukti)

- Repo ini hampir kosong sejak awal sesi — jangan pernah mengasumsikan
  stack, perintah build, atau CI "standar"; selalu audit dulu (§1).
- Karena CI belum ada, "CI hijau" belum bisa dijadikan bukti kualitas —
  seluruh beban validasi ada di gerbang lokal §3 sampai workflow pertama ada.
