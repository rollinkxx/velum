# Uji Perangkat Velum — tanpa adb

Checklist pengujian di perangkat Android nyata. **Dibutuhkan** karena repo ini tidak punya
emulator — tidak di lingkungan agen, tidak di CI (AGENTS.md §11.2, §12).

Disusun untuk keadaan maintainer yang sebenarnya: **Android 14, tanpa adb, tanpa komputer**
(dinyatakan 2026-09-13). Karena itu **setiap uji di sini bisa dilakukan dari layar
perangkat saja**. Tidak ada perintah `adb`, tidak ada logcat, tidak ada `dumpsys`.

- **Alat bukti utama Anda: layar Diagnostik.** Buka aplikasi → panel Diagnostik → salin.
  Isinya kini mencakup keadaan internal yang dulu hanya ada di logcat:

  ```
  Velum 0.5.0
  Status      : Tersambung
  Endpoint    : 162.159.192.1:2408
  Handshake   : 42 detik lalu
  Durasi      : 01:23
  Trafik      : turun 1,2 MB · naik 240,3 KB
  Uji terakhir: Aktif · DC SIN · 15:25
  Dikecualikan: tidak ada
  Niat        : Hidup · aksi ke-7
  Pemantau    : aktif
  Proses      : hidup 01:23
  Boot        : 3,1 detik · berhasil · 2 jam lalu
  Catatan     : tanpa kunci privat, identitas perangkat, atau alamat IP Anda
  ```

  Empat baris terakhir adalah yang baru. Cara membacanya ada di bagian
  [Cara membaca baris diagnostik](#cara-membaca-baris-diagnostik).

- **Cara melapor paling berguna:** salin **seluruh** isi diagnostik (tombol salin di panel
  itu) dan tempel apa adanya, ditambah satu kalimat apa yang Anda lihat di layar. Jangan
  dirangkum menjadi "berhasil" atau "lancar" — ringkasan tidak bisa dipakai memutuskan apa
  pun (AGENTS.md §12 butir 3).

---

## Cara membaca baris diagnostik

| Baris | Artinya | Tanda ada masalah |
|---|---|---|
| **Niat** | Apakah tunnel *diharapkan* hidup (`Hidup`/`Mati`) dan berapa kali aksi sambung/putus terjadi (`aksi ke-N`) | `Status: Terputus` tapi `Niat: Hidup` → tunnel akan menyambung sendiri tanpa Anda minta. **Itu bug.** |
| **Pemantau** | Apakah pemantau sambung-ulang otomatis sedang aktif | `Pemantau: aktif` padahal Anda baru saja memutus manual → pemantau tidak dimatikan |
| **Proses** | Sudah berapa lama **proses aplikasi** hidup (bukan berapa lama tunnel tersambung) | Angka ini jauh lebih kecil dari lamanya Anda membiarkan aplikasi di latar → proses sempat mati dan lahir lagi, tunnel ikut mati bersamanya |
| **Boot** | Percobaan menyambung otomatis terakhir (setelah perangkat menyala atau aplikasi diperbarui): lama, hasil, dan kapan | `GAGAL`, atau durasi mendekati/melebihi `10,0 detik`, atau `belum ada percobaan` padahal Anda baru saja memulai ulang perangkat |

Angka `aksi ke-N` tidak berarti apa-apa sendirian. Yang berarti: **naik berapa kali** setelah
satu tindakan Anda. Tekan Putuskan sekali → angka naik 1. Naik 2 atau lebih = ada pelaku lain
(layar, ubin, pemantau) yang ikut bertindak.

---

## Kelompok A — status & durasi (semua V1)

| # | Tingkat | Langkah | Yang diharapkan di layar | Bila berbeda |
|---|---|---|---|---|
| A1 | V1 | Sambungkan. Putar layar 2×. Kunci layar, buka lagi. | `Durasi` terus bertambah, tidak kembali `00:00`. `Trafik` **langsung** menampilkan angka masuk akal, bukan `0 B/s` selama ~5 detik | Durasi reset = umur tunnel tidak dibaca dari proses. `0 B/s` sesaat = dasar hitungan laju tidak direset |
| A2 | V1 | Putuskan tunnel. Tekan **ubin** di panel cepat untuk menyambung, **tanpa membuka aplikasi**. Tunggu, lalu buka panel notifikasi. | Notifikasi "Tersambung" muncul walau aplikasi tidak pernah dibuka | Notifikasi hanya diposting oleh layar |
| A3 | V1 | Sambungkan. Matikan VPN dari **Pengaturan sistem → Jaringan → VPN** (bukan dari aplikasi). Buka aplikasi. | `Status: Terputus`, notifikasi hilang, `Durasi` kosong. **`Niat: Mati`** | Notifikasi menetap = status basi. `Niat: Hidup` = niat bocor |
| A4 | V1 | Sambungkan. Biarkan **30 menit** dengan layar mati, jangan buka aplikasi. Buka lagi, lihat diagnostik. | `Status: Tersambung` dan **`Proses: hidup 30:xx`** (kira-kira selama Anda meninggalkannya) | `Proses` hanya beberapa menit = proses mati di latar dan tunnel sempat putus. Ini risiko yang sengaja diambil saat deklarasi foreground service dihapus — **laporkan angkanya apa adanya** |
| A5 | V1 | Sambungkan, biarkan 5 menit, lalu buka diagnostik dua kali berjarak 1 menit. | `Proses` dan `Durasi` bertambah keduanya, selisihnya konsisten | `Durasi` bertambah tapi `Proses` reset = proses lahir ulang diam-diam |

## Kelompok B — niat pengguna lintas pelaku (V1 lewat baris Niat)

Yang diuji di sini **gejalanya**, bukan mekanismenya. Anda tidak perlu presisi milidetik:
lakukan secepat yang wajar, lalu baca baris `Niat`.

| # | Tingkat | Langkah | Yang diharapkan | Bila berbeda |
|---|---|---|---|---|
| B1 | V1 | Tekan **Putuskan** di aplikasi, lalu **segera** tekan ubin untuk menyambung. Buka diagnostik. | Keadaan akhir **konsisten**: `Status` dan `Niat` sepadan (sama-sama hidup atau sama-sama mati) | `Status: Terputus` + `Niat: Hidup` = tunnel akan menyambung sendiri → **bug, laporkan** |
| B2 | V1 | Tekan ubin untuk menyambung, lalu **secepatnya** buka aplikasi dan tekan Putuskan sebelum selesai. Tunggu 15 detik, buka diagnostik. | `Status: Terputus`, **`Niat: Mati`**, `Pemantau: mati`. Tunnel **tidak** menyambung sendiri setelah itu | Tunnel hidup lagi sendiri = aksi ubin menimpa setelah Putuskan |
| B3 | V1 | Sambungkan. Matikan Wi-Fi **dan** data, tunggu 10 detik, hidupkan lagi. Ulangi 3× cepat. Buka diagnostik tiap kali. | `Status: Tersambung` kembali. `aksi ke-N` tidak melonjak liar (naik wajar, tidak belasan) | Niat melonjak banyak = pemantau memantul berulang tanpa kendali |
| B4 | V1 | Putuskan **secara manual** dari aplikasi. Lalu matikan-hidupkan jaringan. Buka diagnostik. | Tunnel **tetap mati**: `Status: Terputus`, `Niat: Mati`, `Pemantau: mati` | `Pemantau: aktif` atau tunnel hidup lagi = niat "putus" tidak dihormati |
| B5 | V1 | Sambungkan. Matikan jaringan, biarkan **2 menit**, hidupkan lagi. Tunggu 1 menit, buka diagnostik. | `Status: Tersambung` pulih sendiri tanpa Anda menyentuh aplikasi | Tidak pulih = pemantau menyerah terlalu cepat |

## Kelompok C — pengecualian aplikasi (V1, lewat situs pemeriksa IP)

| # | Tingkat | Langkah | Yang diharapkan | Bila berbeda |
|---|---|---|---|---|
| C1 | V1 | Dalam keadaan **tersambung**: buka Pengecualian, centang **browser** Anda, Simpan. | Toast berbunyi "…menyambungkan ulang…". Tunnel putus-sambung **sekali** (~1–3 detik), lalu tersambung lagi | Tidak ada restart = perubahan tidak diterapkan. Restart berulang = ada yang memantul |
| C2 | V1 | Bukti C1 **tanpa adb**: sebelum mencentang, buka `cloudflare.com/cdn-cgi/trace` di browser dan catat baris `ip=` dan `loc=`. Sesudah mencentang + tersambung lagi, buka halaman yang sama. | `ip=` dan `loc=` **berubah** menjadi IP/lokasi asli Anda (bukan Cloudflare) — artinya browser keluar dari tunnel. Aplikasi lain yang tidak dicentang tetap lewat tunnel | Tidak berubah = pengecualian tidak sampai ke sistem |
| C3 | V1 | Buka Pengecualian, **jangan ubah apa pun**, tekan Simpan. | Toast "Daftar pengecualian disimpan." **tanpa** "menyambungkan ulang…", dan tunnel tidak putus | Ikut restart = membuang waktu tiap kali Simpan |
| C4 | V1 | Dalam keadaan **putus**: ubah daftar, Simpan, buka diagnostik. | Hanya tersimpan. `Status: Terputus`, `Niat: Mati` — aplikasi tidak menyalakan tunnel sendiri | Tunnel menyambung = niat "sedang putus" diabaikan |

## Kelompok D — penyimpanan & registrasi (V1)

| # | Tingkat | Langkah | Yang diharapkan | Bila berbeda |
|---|---|---|---|---|
| D1 | V1 | Susun daftar pengecualian (2 aplikasi). Sambungkan. Lalu **Daftar ulang**. Setelah selesai, buka Pengecualian lagi. | Kedua aplikasi **masih tercentang**. Tunnel diputus selama pendaftaran ulang | Daftar kosong = `clear()` menghapus pilihan Anda |
| D2 | V1 | Buka diagnostik, periksa ada/tidaknya baris `Peringatan`. | **Tidak ada** baris `Peringatan : penyimpanan TIDAK terenkripsi` | Ada baris itu = keystore perangkat gagal, kunci privat tersimpan tanpa enkripsi. **Laporkan segera** — dan perlu daftar ulang sekali (konsekuensi yang diketahui, TODO 78) |
| D3 | V1 | Paksa aplikasi berhenti (Pengaturan → Aplikasi → Velum → **Paksa berhenti**) **tepat saat** menekan Sambungkan/Daftar ulang. Buka lagi. | Aplikasi tetap bisa dipakai: atau tersambung penuh, atau kembali ke keadaan sebelum itu — **tidak campuran** (mis. terdaftar tapi tidak bisa menyambung) | Keadaan campuran = penulisan penyimpanan tidak atomik |

## Kelompok E — rotasi endpoint (V1)

| # | Tingkat | Langkah | Yang diharapkan | Bila berbeda |
|---|---|---|---|---|
| E1 | V1 | Dalam keadaan tersambung, putar endpoint dari layar utama. | Notifikasi hilang-muncul sebentar, `Durasi` kembali `00:00`, `Endpoint` berubah, lalu tersambung lagi | Endpoint tidak berubah tapi aplikasi mengklaim berpindah = laporan palsu |
| E2 | V1 | Putar endpoint **5× berturut-turut**, masing-masing tunggu selesai. | Semua selesai, aplikasi tetap responsif, tidak ada dialog "tidak merespons" | Macet/ANR = kunci tunnel menahan terlalu lama |
| E3 | V1 | Putar endpoint saat tunnel **putus**. Buka diagnostik. | `Status: Terputus`, dan tidak ada klaim berhasil berpindah | Aplikasi melaporkan berpindah padahal tidak = temuan B4 belum tertutup |

## Kelompok F — boot & pembaruan (V1 lewat baris Boot)

Ini kelompok yang paling penting untuk keputusan TODO 77, dan sekarang **tidak butuh
logcat sama sekali** — angkanya ada di baris `Boot`.

| # | Tingkat | Langkah | Yang diharapkan | Bila berbeda |
|---|---|---|---|---|
| F1 | V1 | Dalam keadaan tersambung, **mulai ulang perangkat**. Setelah menyala, **jangan buka aplikasi** selama 2 menit. Lalu buka diagnostik. | `Status: Tersambung`, dan baris `Boot` berisi durasi + `berhasil` + umur beberapa menit | `Boot: … GAGAL` atau `belum ada percobaan` padahal Anda baru memulai ulang = sambung ulang boot tidak jalan |
| F2 | V1 | **Catat angka durasi pada baris `Boot` dari F1.** Inilah angka keputusan itu. | Kurang dari `10,0 detik` | **`10,0 detik` atau lebih = anggaran receiver terlampaui.** Laporkan angkanya persis (mis. `14,2 detik`); dari situ TODO 77 diputuskan |
| F3 | V1 | Dalam keadaan **putus**, mulai ulang perangkat, tunggu 2 menit, buka diagnostik. | `Status: Terputus` (tunnel tidak menyambung sendiri), `Niat: Mati`, baris `Boot` **tidak** berubah menjadi percobaan baru | Tunnel menyambung sendiri = niat "putus" tidak dihormati saat boot |
| F4 | V1 | Perbarui aplikasi (pasang APK baru menimpa yang lama) saat tunnel tersambung. Tunggu 2 menit, buka diagnostik. | `Status: Tersambung` kembali tanpa Anda buka aplikasi; baris `Boot` terisi percobaan baru | Tetap putus = `MY_PACKAGE_REPLACED` tidak bekerja |
| F5 | V1 | Setelah F1/F4, perhatikan layar selama 1 menit: adakah jeda panjang, layar "Aplikasi tidak merespons", atau notifikasi sistem soal aplikasi yang menguras baterai? | Tidak ada | Ada → catat **kapan** dan **apa bunyinya** persis. Ini pengganti pemeriksaan `broadcast timeout` yang tadinya butuh adb |

## Kelompok G — izin & kegagalan (V1)

| # | Tingkat | Langkah | Yang diharapkan | Bila berbeda |
|---|---|---|---|---|
| G1 | V1 | Cabut izin notifikasi (Pengaturan → Aplikasi → Velum → Izin → Notifikasi: mati). Lalu sambungkan. | Tunnel tetap tersambung, aplikasi tidak menutup sendiri | Crash = izin dianggap wajib |
| G2 | V1 | Hapus data aplikasi (atau pasang ulang) lalu tekan Sambungkan, dan **tolak** dialog persetujuan VPN. | Pesan jelas, `Status: Terputus`, tidak ada notifikasi "Tersambung" palsu | Notifikasi/klaim tersambung padahal ditolak |
| G3 | V1 | Nyalakan **mode pesawat**, lalu tekan Sambungkan. | Kegagalan dijelaskan dengan kalimat yang bisa dimengerti, tombol tidak terkunci permanen | Tombol macet di "Menyambungkan…" selamanya |
| G4 | V1 | Masih dalam mode pesawat, tunggu 30 detik, lalu matikan mode pesawat. Jangan sentuh aplikasi. Tunggu 1 menit, buka diagnostik. | `Status: Tersambung` pulih sendiri | Tidak pulih = pemantau tidak bekerja setelah jaringan kembali |

---

## Yang TIDAK bisa Anda uji — dan jangan dicoba

Berikut ini dulu tertulis sebagai tugas Anda. Semuanya **ditarik kembali**: tanpa adb tidak
ada cara menjalankannya, dan memintanya berarti memindahkan beban yang seharusnya dipikul
agen (AGENTS.md §12, "Kewajiban mengubah V3 menjadi V1").

| Dulu diminta | Kenapa tidak lagi | Penggantinya |
|---|---|---|
| `adb shell dumpsys package … \| grep foregroundServiceType` | Butuh adb | Sudah dipastikan dari sumber: manifest tidak lagi mendeklarasikannya, dan library upstream tidak memanggil `startForeground()` sama sekali. Yang tersisa adalah **akibatnya** di runtime → diuji lewat A4/A5 (baris `Proses`) |
| `adb logcat` per-tag untuk semua kelompok | Butuh adb | Baris diagnostik `Niat`/`Pemantau`/`Proses`/`Boot` |
| `adb shell run-as … ls shared_prefs/` (memeriksa berkas penyimpanan) | Butuh adb + build debug | Baris `Peringatan` di diagnostik (D2) |
| `adb reboot`, `adb install -r` | Butuh adb | Mulai ulang perangkat lewat menu sistem (F1), pasang APK menimpa lewat pengelola berkas (F4) |
| Mengukur jendela race ~1 detik antar-thread | Bukan pengamatan manusia | Gejalanya yang diuji (B1/B2) lewat baris `Niat` |
| `grep 'broadcast timeout'` di logcat | Butuh adb | Pengamatan langsung (F5) + durasi di baris `Boot` (F2) |

**Yang tetap tidak terverifikasi oleh siapa pun** (status permanen `hanya nalar`, tercatat di
ledger): apakah kunci `@Synchronized` benar-benar menserialisasi transisi tunnel di bawah
tekanan nyata, dan apakah ada interleaving langka yang hanya muncul pada beban tertentu.
Keduanya tidak punya gejala layar yang bisa dipancing dengan sengaja. Risikonya dicatat apa
adanya di `docs/verifikasi-perangkat.md`, bukan disembunyikan di balik kata "sudah diuji".

## Cara melaporkan

Tempel untuk tiap uji: **nomor uji**, **apa yang Anda lihat** (salinan baris diagnostik +
satu kalimat), dan **vonis Anda bila mau** (`LULUS` / `GAGAL` / `tidak sesuai harapan`).
Bila sebuah uji tidak dijalankan, tulis `tidak diuji` — jangan dikosongkan, dan jangan
dianggap lulus.

Agen yang merapikannya ke format ledger; Anda tidak perlu menulis ulang apa pun.
