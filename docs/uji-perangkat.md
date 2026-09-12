# Uji Perangkat Velum

Checklist pengujian di perangkat Android nyata. **Dibutuhkan** karena repo ini tidak punya
emulator — bukan di lingkungan agen, bukan di CI (lihat AGENTS.md §11.2 dan TODO 71).
Semua yang dijanjikan di bawah ini baru terbukti *secara nalar dan kompilasi*; yang belum
terbukti adalah perilakunya di perangkat.

- **Syarat minimum:** Android 14+ (API 34+) untuk menguji penegakan tipe foreground service.
  Uji dasar (sambung/putus/rotasi) berjalan di API 24+.
- **Versi yang diuji:** `arena/01a09664-velum` @ `c4da5df` + paket perbaikan kedua.
- **Penting:** uji dengan APK `debug` bila ingin membaca logcat tanpa perangkat root
  (proses `:preview` juga bisa, tapi R8 membuat sebagian nama di log sudah terubah).

## Cara merekam

```bash
# bersihkan, lalu rekam hanya tag aplikasi ini
adb logcat -c
adb logcat -v time Velum:V VelumTunnel:V VelumController:V VelumMonitor:V VelumTile:V \
                   VelumBoot:V VelumProbe:V VelumExclusion:V VelumApi:V VelumPrefs:V *:S \
  | tee velum-uji.log
```

Tag lengkap bisa dicek dengan `grep -rn 'const val TAG' app/src/main/java/`.

Untuk melihat keputusan sistem soal layanan/tunnel:

```bash
adb logcat -v time | grep -Ei 'vpn|VpnService|GoBackend|ActivityManager|ForegroundService|ANR'
```

Setiap baris yang menarik (atau **hilangnya** baris yang seharusnya ada) adalah bukti.
Tempel apa adanya ke laporan — jangan dirangkum menjadi "berhasil".

---

## Kelompok A — status & kepemilikan

| # | Langkah | Yang diharapkan | Bila berbeda |
|---|---|---|---|
| A1 | Sambungkan, putar layar 2×, lalu kunci-buka layar | Durasi tetap berjalan (tidak `00:00`); tidak ada auto-uji ulang; laju trafik **langsung** masuk akal, bukan `0 B/s` selama ~5 detik pertama | durasi reset = `upSinceElapsedMs` tidak dibaca; `0 B/s` = `resetTrafficBaseline` tidak terpanggil di `onStart` |
| A2 | Sambungkan lewat **ubin** dengan aplikasi tertutup | Notifikasi "Tersambung" muncul tanpa membuka aplikasi | `onStateChange` tidak memposting notifikasi |
| A3 | Sambungkan, lalu matikan VPN dari **Pengaturan sistem** (bukan dari aplikasi) | Notifikasi hilang; ubin kembali "Tidak aktif"; durasi reset | notifikasi basi = `hide()` tidak terpanggil |
| A4 | Sambungkan, biarkan 10 menit di latar (layar mati) | Tunnel tetap UP; durasi terus bertambah saat aplikasi dibuka lagi | proses mati = VPN tidak menahan proses (bukan FGS) |
| A5 | `adb shell dumpsys package com.rollinkxx.velum \| grep -A3 'foregroundServiceType'` | **Tidak ada** atribut itu, dan tidak ada izin `FOREGROUND_SERVICE*` di daftar `requested permissions` | manifest masih membawa deklarasi inert |
| A6 | Sambungkan, lalu `adb shell dumpsys activity services com.rollinkxx.velum` | `GoBackend$VpnService` tercatat sebagai VpnService aktif (`isForeground=false` itu **benar**) | — |

## Kelompok B — niat pengguna lintas pelaku

| # | Langkah | Yang diharapkan | Bila berbeda |
|---|---|---|---|
| B1 | Tekan **Putuskan** di aplikasi, lalu **dalam 1 detik** tekan ubin untuk menyambung | Yang menang adalah aksi terakhir; tunnel tidak "bangkit sendiri" setelahnya | generasi niat tidak diperiksa |
| B2 | Tekan ubin untuk menyambung (proba endpoint ~6 dtk), lalu tekan **Putuskan** di aplikasi sebelum selesai | Tunnel berakhir **mati**, `wasUp=false`, pemantau mati | aksi ubin menimpa setelah `down()` |
| B3 | Sambungkan, lalu **matikan Wi-Fi/data** dan hidupkan lagi 3× cepat | Tunnel kembali UP sekali, bukan berulang; tidak ada "sambung-putus" beruntun | pemantau memantul tanpa backoff |
| B4 | Putuskan secara manual, lalu matikan-hidupkan jaringan | Tunnel **tetap mati** (niat pengguna = putus). Log: pemantau menolak karena `wasUp=false` | `ReconnectMonitor.ensure` menyalakan pemantau tanpa memeriksa niat |
| B5 | Sambungkan → matikan jaringan → **tunggu > 60 detik** → hidupkan jaringan | Bounce dengan backoff terjadi; bila gagal, ada log jujur (bukan diam) | — |

## Kelompok C — pengecualian aplikasi (split tunnel)

| # | Langkah | Yang diharapkan | Bila berbeda |
|---|---|---|---|
| C1 | Dalam keadaan **tersambung**, buka Pengecualian, centang 1 aplikasi, Simpan | Toast "…menyambungkan ulang…"; tunnel putus-sambung **sekali** (~1–3 dtk); aplikasi itu langsung keluar dari tunnel | perubahan tidak berlaku = `restart()` tidak dipanggil |
| C2 | Bukti C1: `adb shell dumpsys connectivity \| grep -i vpn` atau buka aplikasi yang dikecualikan dan periksa lewat situs "what is my ip" | IP aplikasi yang dikecualikan ≠ IP tunnel | `disallowedApplications` tidak sampai ke `VpnService.Builder` |
| C3 | Simpan **tanpa mengubah** centang apa pun | Tidak ada penyambungan ulang (log: daftar tidak berubah) | pemborosan: restart tiap kali Simpan |
| C4 | Dalam keadaan **putus**, ubah daftar lalu Simpan | Hanya tersimpan; tunnel tidak dinyalakan | `save()` menyambung padahal niat pengguna = putus |

## Kelompok D — penyimpanan & registrasi

| # | Langkah | Yang diharapkan | Bila berbeda |
|---|---|---|---|
| D1 | Daftar ulang (`Prefs.clear`) saat tunnel **tersambung** dan daftar pengecualian **tidak kosong** | Tunnel diputus; pengecualian **tetap ada** setelah daftar ulang; `wasUp` mengikuti niat | pengecualian hilang = `clear()` tidak mempertahankannya |
| D2 | Paksa proses mati di tengah penyimpanan (`adb shell am force-stop` saat mendaftar) lalu buka aplikasi | Data konsisten: **semua** field registrasi baru, atau **semua** yang lama. Tidak ada campuran (kunci baru + endpoint lama) | `saveRegistration` tidak atomik |
| D3 | `adb shell run-as com.rollinkxx.velum ls shared_prefs/` (APK debug) | Ada `velum.xml`. `velum_plain.xml` **hanya** ada bila keystore perangkat gagal | `velum_plain.xml` ada padahal keystore sehat = fallback terpakai diam-diam |
| D4 | Bila D3 menemukan `velum_plain.xml` | Logcat memuat "penyimpanan polos dipakai" + layar diagnostik menandainya. **Perlu daftar ulang sekali** — itu konsekuensi yang diketahui dari pemisahan nama berkas (lihat komentar di `Prefs.open`) | fallback tidak memberi tanda apa pun |

## Kelompok E — rotasi endpoint

| # | Langkah | Yang diharapkan | Bila berbeda |
|---|---|---|---|
| E1 | Putar endpoint dari layar utama saat **tersambung** | Notifikasi hilang-muncul sebentar, durasi kembali `00:00`, tunnel UP lagi dengan endpoint baru | — |
| E2 | Putar endpoint saat **tidak** terpasang/tidak terdaftar | Tidak ada perubahan preferensi yang ditulis; log jujur ("tidak ada endpoint pengganti") | `rotate()` menulis prefs di jalur gagal |
| E3 | Ulangi putar endpoint 5× berturut-turut | Tidak ada kebocoran (tiap putaran selesai sebelum berikutnya); tidak ada ANR | kunci `@Synchronized` menahan terlalu lama |

## Kelompok F — boot & pembaruan

| # | Langkah | Yang diharapkan | Bila berbeda |
|---|---|---|---|
| F1 | Dalam keadaan tersambung, `adb reboot`; jangan buka aplikasi | Tunnel menyambung sendiri setelah boot; notifikasi ada | `BootReceiver` tidak menuntaskan `up()` dalam anggaran `goAsync()` |
| F2 | **Catat waktunya:** berapa detik dari layar kunci muncul sampai notifikasi "Tersambung"? | — | **Angka ini yang dibutuhkan** untuk memutuskan temuan D6 (risiko `goAsync` vs proses dibunuh). Tulis apa adanya, termasuk bila > 10 detik atau bila gagal |
| F3 | Dalam keadaan **putus**, `adb reboot` | Tunnel tetap mati; pemantau tidak menyalakannya | `wasUp` salah tersimpan |
| F4 | `adb install -r app-debug.apk` saat tersambung (memicu `MY_PACKAGE_REPLACED`) | Tunnel kembali UP tanpa dibuka manual | — |
| F5 | F1/F4 sambil merekam `adb logcat -b all \| grep -Ei 'broadcast.*timeout\|exceed\|Background execution'` | Tidak ada peringatan sistem soal receiver melebihi batas | ada = D6 terbukti nyata, bukan teoretis |

## Kelompok G — izin & kegagalan

| # | Langkah | Yang diharapkan | Bila berbeda |
|---|---|---|---|
| G1 | Cabut izin notifikasi (Android 13+), lalu sambungkan | Tunnel tetap UP; tidak ada crash; tidak ada dialog sistem yang macet | — |
| G2 | Tolak dialog persetujuan VPN saat pertama menyambung | Pesan jelas, tidak ada tunnel setengah jadi, tidak ada notifikasi palsu | — |
| G3 | Matikan jaringan sepenuhnya lalu tekan Sambungkan | Kegagalan dijelaskan (`VelumError`), tombol tidak terkunci selamanya | tombol mati = state tidak dikembalikan |
| G4 | Mode pesawat → sambungkan → matikan mode pesawat | Pemantau memulihkan tunnel tanpa sentuhan pengguna | — |

---

## Cara melaporkan

Untuk tiap baris: **nomor uji**, **hasil sebenarnya** (termasuk angka detik dan kutipan
logcat), dan **perangkat/versi Android**. Contoh yang berguna:

> F2 — Pixel 7, Android 15. Notifikasi "Tersambung" muncul 14 detik setelah layar kunci.
> Logcat: `VelumBoot: menunggu VpnService…` lalu jeda 9 detik sebelum `up()` selesai.
> Tidak ada peringatan broadcast timeout.

Yang **tidak** berguna: "semua lancar". Bila sebuah uji tidak dijalankan, tulis
`tidak diuji` — jangan dikosongkan, dan jangan dianggap lulus (AGENTS.md §11).

Temuan F2 dan F5 secara khusus menentukan apakah `BootReceiver` perlu diubah dari
`goAsync()` — keputusan itu sengaja tidak diambil dari sandbox karena kedua pilihan
mempunyai risiko nyata (lihat komentar di `BootReceiver.kt`).
