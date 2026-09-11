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
- CI GitHub Actions `build.yml`: `assembleDebug`, artifact `app-debug`, step summary sebagai fallback log.
- Panel status interaktif di kartu utama: titik status berdenyut halus saat tersambung
  (animasi alpha ringan, berhenti otomatis saat terputus), durasi tersambung (tiker 1 detik,
  hanya selama UP), endpoint tunnel, dan hasil uji terakhir lengkap dengan data center
  (`colo`) dan jam cek dari `cdn-cgi/trace`; uji koneksi otomatis berjalan sekali setiap
  kali tersambung.
- Job CI opsional `assembleRelease` bertanda tangan: aktif hanya bila variable
  `ENABLE_RELEASE_SIGNING=true` dan Secrets keystore (`SIGNING_KEYSTORE_BASE64`,
  `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) disiapkan maintainer; penandatanganan
  dibaca dari environment — tidak ada materi rahasia di repo.
- Penyembuhan otomatis (auto-heal) akun era lama tanpa flag WARP: saat menyambung, aplikasi
  memeriksa `GET /reg/{id}` sekali (memo `warpEnabled`); bila akun terbukti tanpa flag,
  registrasi lama dihapus dan perangkat didaftarkan ulang secara transparan. Bersifat
  fail-safe — keraguan/kegagalan jaringan tidak menyentuh akun dan tidak menghalangi
  penyambungan.
- Pintasan "Selalu aktif (pengaturan sistem)" menuju pengaturan VPN bawaan Android
  (always-on + blokir koneksi tanpa VPN dikelola sistem).
- Sambung ulang otomatis setelah boot (`BootReceiver` + izin `RECEIVE_BOOT_COMPLETED`)
  bila terakhir kali tunnel memang tersambung dan persetujuan VPN masih berlaku.
- Notifikasi persisten status koneksi (kanal `status`, IMPORTANCE_LOW, ketuk untuk membuka
  aplikasi) dengan permintaan izin `POST_NOTIFICATIONS` pada Android 13+.
- Sambung ulang otomatis saat konektivitas berubah (`ReconnectMonitor` lingkup aplikasi:
  pantulan tunnel dengan backoff 2/5/10 detik + debounce, pemulihan sesi saat proses
  lahir ulang, penjagaan sesi dari `BootReceiver` bila boot tanpa jaringan).
- Penyimpanan registrasi terenkripsi (`EncryptedSharedPreferences`, AES256-GCM) dengan
  migrasi sekali dari file era polos dan fallback aman bila keystore perangkat gagal.
- Baris statistik trafik di kartu status (byte naik/turun tiap 5 detik dari backend
  WireGuard) plus deteksi tunnel basi: peringatan bila 30 detik tanpa lalu lintas dan
  handshake kedaluwarsa.
- Baris Data kini menampilkan laju kecepatan (KB/s naik/turun) dari delta statistik
  backend per 5 detik.
- Proba endpoint tercepat saat menyambung (`EndpointProbe`): mengukur RTT paralel ke
  endpoint registrasi + kandidat anycast (batas ±6 detik), memakai pemenang selama
  1 jam; selalu fail-safe ke endpoint registrasi.

### Changed
- AGENTS.md §3/§5 disinkronkan dengan stack, CI, dan temuan run pertama.
- Tampilan dipoles menjadi tema gelap elegan: latar gradasi charcoal, kartu status rounded
  dengan titik indikator, tombol utama amber ber-ripple + tombol sekunder outline, tipografi
  `sans-serif-light/medium`, status bar selaras tema; warna ikon adaptif disamakan dengan
  aksen. Seluruhnya murni resource XML bawaan — tanpa dependensi/font eksternal, tanpa
  memengaruhi performa maupun ukuran APK secara berarti.
- Optimasi hemat daya & startup (tanpa mengubah perilaku yang terlihat, kecuali laju trafik
  yang kini baru tampil pada sampel kedua):
  - Tiker durasi 1 Hz, pemantau trafik 5 detik, dan animasi denyut titik status kini
    **berhenti saat UI tak terlihat**. Sebelumnya ketiganya terus berjalan di latar
    belakang karena proses ditahan hidup oleh VpnService selama tunnel UP.
  - `Prefs` dibuka sekali per proses (`Prefs.of()`) dan pengecekan migrasi data era lama
    memakai cek keberadaan berkas, bukan membaca + mendekripsi seluruh nilai. Mengurangi
    kerja I/O di main thread saat aplikasi dibuka dan saat pemantulan tunnel.
  - Izin `POST_NOTIFICATIONS` hanya diminta bila belum diberikan.
  - `EndpointProbe` memakai pool thread daemon bersama (menganggur → mati sendiri)
    daripada membuat dan membuang sampai 8 thread setiap kali menyambung.
  - Uji trace berjalan di executor sendiri agar tidak menahan Sambungkan/Putuskan.
  - Efek samping status (tiker, notifikasi, pembatalan uji) kini tetap dijalankan saat
    ada aksi berlangsung, sehingga tak ada status yang tertinggal bila tunnel berubah
    di tengah aksi.
- Identitas aplikasi diganti dari WARP Lite menjadi **Velum**: `applicationId`/
  namespace/package `com.rollinkxx.velum`, nama tampil, tema, string status
  ("Velum aktif…"), nama sesi VPN, file preferensi, dan class internal (`VelumApi`,
  `VelumTunnel`). Alasan: WARP® adalah merek terdaftar Cloudflare untuk kategori
  software VPN dan panduan mereknya melarang pemakaian di nama aplikasi pihak ketiga
  (lihat ADR 002). Penyebutan WARP yang tersisa hanya referensial (endpoint/protokol).

### Fixed
- Registrasi perangkat kini menyertakan flag `warp_enabled: true` agar akun terdaftar dengan
  WARP penuh (paritas klien resmi). Gejala sebelumnya: `one.one.one.one/help` menampilkan
  "Using DNS over WARP: No" meski tunnel tersambung. Perangkat yang terlanjur terdaftar
  tanpa flag perlu satu kali **Daftar ulang** dari dalam aplikasi.
- `Prefs.clear()` tidak lagi menghapus memo `wasUp`, sehingga penyembuhan akun otomatis
  tidak mematikan niat sambung-ulang saat boot; aksi Daftar ulang manual kini eksplisit
  menandai `wasUp=false` seperti Putuskan.
- Baris "Uji terakhir" tidak lagi menampilkan "Belum lewat Velum" palsu sesaat setelah
  tersambung, meski status sudah "Tersambung". Dua penyebabnya: (1) uji `cdn-cgi/trace`
  ditembakkan seketika saat `State.UP`, padahal saat itu hanya antarmuka TUN yang baru
  dibuat — handshake WireGuard belum tentu selesai; (2) `HttpURLConnection` bisa memakai
  ulang soket keep-alive dari sebelum VPN aktif, dan Android tidak memindahkan soket yang
  sudah terbuka ke VPN sehingga permintaannya keluar langsung ke internet (`warp=off`).
  Perbaikan: keep-alive dimatikan (`Connection: close` + `http.keepAlive=false`), uji
  menunggu handshake yang nyata (batas 6 detik, berhenti lebih awal bila tunnel turun),
  diulang satu kali dengan soket baru bila hasilnya negatif padahal tunnel masih UP, dan
  dibatalkan bila tunnel putus di tengah uji.
