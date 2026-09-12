# Ledger Verifikasi Perangkat

Catatan **hasil** uji perangkat. Checklist-nya (langkah + hasil yang diharapkan) ada di
[`uji-perangkat.md`](uji-perangkat.md); berkas ini adalah buku besar hasilnya, supaya
jawaban maintainer tidak hilang di percakapan dan status di `TODO.md` bisa dinaikkan
berdasarkan bukti yang bisa ditunjuk.

Diatur oleh AGENTS.md §12.

## Aturan pengisian

1. **Satu baris per uji yang dijalankan**, bukan per sesi. Uji yang tidak dijalankan
   ditulis `tidak diuji` — jangan dikosongkan, jangan dianggap lulus.
2. Kolom **Hasil sebenarnya** wajib memuat apa yang teramati, termasuk angka (detik,
   byte, jumlah percobaan) dan kutipan logcat apa adanya. Ringkasan seperti "lancar"
   atau "OK" **tidak sah** dan akan dikembalikan.
3. Kolom **Vonis** hanya boleh tiga nilai: `LULUS`, `GAGAL`, `TIDAK SESUAI HARAPAN`
   (terakhir ini untuk hasil yang tidak gagal tapi menyimpang dari yang ditulis di
   checklist — misalnya berhasil tapi memakan 14 detik).
4. Bila `GAGAL` atau `TIDAK SESUAI HARAPAN`: tuliskan nomor TODO/temuan yang dibuka,
   jangan memperbaiki diam-diam.
5. Tanggal memakai format `YYYY-MM-DD` (UTC atau WIB, sebutkan bila relevan).

## Status saat ini

**Belum ada satu pun uji yang dijalankan.** Seluruh 30 uji di `uji-perangkat.md`
berstatus menunggu perangkat. Konsekuensinya, menurut §12: semua perbaikan yang
menyangkut perilaku runtime di branch `arena/01a09664-velum` berstatus
*terbukti kompilasi + unit test JVM + nalar*, **bukan** *terverifikasi di perangkat*.

| Kelompok | Jumlah uji | Status | Menutup utang di |
|---|---|---|---|
| A — status & kepemilikan | 6 | menunggu perangkat | TODO 56, 67, 68, 71 |
| B — niat pengguna lintas pelaku | 5 | menunggu perangkat | TODO 67, 71, 74 |
| C — pengecualian aplikasi | 4 | menunggu perangkat | TODO 71, 74 |
| D — penyimpanan & registrasi | 4 | menunggu perangkat | TODO 68, 75 |
| E — rotasi endpoint | 3 | menunggu perangkat | TODO 63, 71 |
| F — boot & pembaruan | 5 | menunggu perangkat | TODO 71, 77 |
| G — izin & kegagalan | 4 | menunggu perangkat | TODO 56, 67 |

Dua uji bernilai keputusan, bukan hanya lulus/gagal:

- **F2** (berapa detik dari layar kunci sampai notifikasi "Tersambung") dan **F5**
  (ada/tidaknya peringatan `broadcast timeout`) menentukan apakah `BootReceiver` perlu
  diubah dari `goAsync()` — lihat TODO 77. Kedua pilihan punya risiko nyata, jadi
  keputusannya menunggu angka, bukan penalaran.
- **A4** dan **A6** (daya tahan proses di latar tanpa foreground service) adalah
  pemeriksaan atas risiko yang sengaja diambil saat deklarasi FGS dihapus dari manifest.

## Hasil

Isi tabel ini setiap kali uji dijalankan. Baris contoh di bawah adalah **format**, bukan
hasil — hapus atau ganti saat dipakai.

| Tanggal | Perangkat & versi Android | Uji | Hasil sebenarnya | Vonis | Tindak lanjut |
|---|---|---|---|---|---|
| _contoh_ | _Pixel 7, Android 15_ | _F2_ | _Notifikasi "Tersambung" muncul 14 detik setelah layar kunci. Logcat: `VelumBoot: menunggu VpnService…` lalu jeda 9 detik sebelum `up()` selesai. Tidak ada peringatan broadcast timeout._ | _TIDAK SESUAI HARAPAN_ | _TODO 77: angka >10 dtk, `goAsync()` perlu diputuskan_ |
| | | | | | |
