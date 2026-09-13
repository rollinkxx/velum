# Ledger Verifikasi Perangkat

Catatan **hasil** uji perangkat. Checklist-nya (langkah + hasil yang diharapkan) ada di
[`uji-perangkat.md`](uji-perangkat.md); berkas ini adalah buku besar hasilnya, supaya
laporan maintainer tidak hilang di percakapan dan status di `TODO.md` bisa dinaikkan
berdasarkan bukti yang bisa ditunjuk.

Diatur oleh AGENTS.md §12.

## Batasan yang membentuk ledger ini

Dinyatakan maintainer 2026-09-13: perangkat uji **Android 14**, **tanpa adb** (tidak ada
komputer untuk logcat/dumpsys). Karena itu:

- **Bukti yang sah = salinan layar diagnostik + satu kalimat pengamatan.** Logcat **tidak**
  diminta dan tidak boleh dijadikan syarat.
- Setiap uji bertanda tingkat: **V1** (terlihat di layar), **V2** (butuh perintah — tidak
  dipakai di checklist ini), **V3** (teknis dalam — tidak pernah diserahkan ke maintainer).
- Uji yang tidak punya padanan V1 **tidak dicatat sebagai utang maintainer**, melainkan
  sebagai `hanya nalar` di bagian bawah berkas ini.

## Aturan pengisian

1. **Satu baris per uji yang dijalankan.** Uji yang tidak dijalankan ditulis `tidak diuji`
   — jangan dikosongkan, jangan dianggap lulus.
2. Kolom **Hasil sebenarnya** memuat apa yang teramati: salinan baris diagnostik apa adanya
   (angka detik, `Niat`, `Pemantau`, `Proses`, `Boot`) dan/atau apa yang terlihat di layar.
   Ringkasan seperti "lancar" atau "OK" **tidak sah** dan akan dikembalikan.
3. Kolom **Vonis** hanya tiga nilai: `LULUS`, `GAGAL`, `TIDAK SESUAI HARAPAN` (terakhir ini
   untuk hasil yang tidak gagal tapi menyimpang dari checklist — misalnya berhasil tapi
   memakan 14 detik).
4. Bila `GAGAL` atau `TIDAK SESUAI HARAPAN`: agen yang menerjemahkan gejala menjadi dugaan
   penyebab dan membuka item TODO — bukan maintainer.
5. Tanggal format `YYYY-MM-DD`.

## Status saat ini

**Sebagian uji sudah dijalankan** (kelompok H, branch `arena/01a098b1-velum`,
laporan maintainer 2026-09-13): H1/H2/H3/H4 `LULUS`, H5 `TIDAK SESUAI HARAPAN`
(menunggu uji ulang). Kelompok A–G (29 uji) masih menunggu perangkat.

| Kelompok | Uji | Tingkat | Menutup utang di |
|---|---|---|---|
| A — status & durasi | A1–A5 | V1 | TODO 56, 67, 68, 71 |
| B — niat pengguna lintas pelaku | B1–B5 | V1 (lewat baris `Niat`/`Pemantau`) | TODO 67, 71, 74 |
| C — pengecualian aplikasi | C1–C4 | V1 (lewat `cdn-cgi/trace` di browser) | TODO 71, 74 |
| D — penyimpanan & registrasi | D1–D3 | V1 (lewat baris `Peringatan`) | TODO 68, 75 |
| E — rotasi endpoint | E1–E3 | V1 | TODO 63, 71 |
| F — boot & pembaruan | F1–F5 | V1 (lewat baris `Boot`) | TODO 71, 77 |
| G — izin & kegagalan | G1–G4 | V1 | TODO 56, 67 |

Dua uji bernilai **keputusan**, bukan sekadar lulus/gagal:

- **F2** — durasi pada baris `Boot`. Angka inilah yang memutuskan TODO 77 (`goAsync()` di
  `BootReceiver`): di bawah `10,0 detik` berarti anggaran receiver tidak terlampaui; sama
  atau lebih berarti risiko itu nyata dan perlu diubah. Sejak 2026-09-13 angka ini bisa
  dibaca di layar, jadi tidak ada alasan menggantung keputusannya.
- **A4/A5** — umur pada baris `Proses`. Ini pemeriksaan atas risiko yang **sengaja diambil**
  ketika deklarasi foreground service dihapus dari manifest: bila proses ternyata mati di
  latar, keputusan itu harus ditinjau ulang (tipe yang benar untuk VPN adalah
  `systemExempted`, sudah dicatat di manifest).

## Hasil

Isi tabel ini setiap kali uji dijalankan. Baris contoh di bawah adalah **format**, bukan
hasil — ganti atau hapus saat dipakai.

| Tanggal | Perangkat & Android | Uji | Tingkat | Hasil sebenarnya | Vonis | Tindak lanjut |
|---|---|---|---|---|---|---|
| 2026-09-13 | Android 14 (perangkat maintainer) | H1 | V1 | Maintainer: "H1 terverifikasi" — `Total` langsung tampil dan laju muncul dalam ±2 detik setelah tersambung. | LULUS | — |
| 2026-09-13 | Android 14 (perangkat maintainer) | H2 | V1 | Maintainer: laju Velum `↓ 5,1 MB/s · ↑ 192,3 KB/s` vs indikator status bar `10,8 M/s`. Tidak ada aplikasi yang dikecualikan dan unduhan stabil. Penjelasan agen (selisih = cara indikator status bar menghitung saat VPN aktif, angka Velum adalah laju tunnel sebenarnya) disetujui maintainer: "saya setuju penjelasanmu (saya anggap terverifikasi)". | LULUS | — |
| 2026-09-13 | Android 14 (perangkat maintainer) | H3 | V1 | Maintainer: "H3 terverifikasi seperti yang diharapkan" — aplikasi tercentang naik ke atas di bawah label "Dikecualikan dari tunnel". | LULUS | — |
| 2026-09-13 | Android 14 (perangkat maintainer) | H4 | V1 | Maintainer: "H4 ini juga terverifikasi" — subjudul "Selalu aktif" mengikuti keadaan sistem saat tersambung. | LULUS | — |
| 2026-09-13 | Android 14 (perangkat maintainer) | H5 | V1 | Percobaan 1: judul kecil, "Tersambung" makan tempat. Percobaan 2: maintainer menilai malah parah — seluruh isi naik ke atas sehingga bagian bawah kosong; yang diminta hanya perbaikan Title & label Tersambung. Percobaan 3: judul 80sp menempel atas & melebar, sisa isi dipusatkan di ruang tersisa, frame "Tersambung" dirapatkan. | TIDAK SESUAI HARAPAN | **Koreksi atas kesalahan agen (scope §0)** pada percobaan 2: pemindahan isi ke atas dibatalkan (gravity & spacing dikembalikan semula), hanya judul & status yang diubah. Percobaan 3 menunggu uji ulang maintainer |

## Tidak terverifikasi oleh siapa pun — status permanen `hanya nalar`

Bagian ini ada supaya kejujurannya tidak bergantung pada ingatan. Berikut klaim yang
**tidak** bisa dibuktikan oleh agen (tidak ada emulator di CI maupun sandbox) **dan tidak**
bisa dibuktikan maintainer (tidak ada adb, tidak ada gejala layar yang bisa dipancing
sengaja). Semuanya tetap dipakai sebagai dasar desain, dengan risiko yang dinyatakan:

| Klaim | Dasar yang ada | Risiko bila ternyata salah | Kenapa tidak bisa diuji |
|---|---|---|---|
| `@Synchronized` pada `up`/`down`/`restart`/`refreshState` benar-benar menserialisasi transisi tunnel di bawah tekanan nyata | Sumber library (`GoBackend.java` tag `1.0.20260102`: `setStateInternal` dipanggil sinkron di dalam metode tersinkronisasi) + kompilasi 3 varian | Dua transisi saling menyela → tunnel dalam keadaan campuran; gejala yang mungkin terlihat adalah B1/B2 gagal | Butuh penekanan waktu yang presisi antar-thread; tidak bisa dipancing dari layar |
| Tidak ada interleaving langka lain di luar dua skenario yang ditutup generasi niat | Penalaran atas semua jalur penulisan `wasUp` (layar, ubin, boot, pemantau) + uji unit keputusan murni | Niat bocor dalam kombinasi yang belum terpikirkan → tunnel menyambung/mati sendiri; **gejalanya akan terlihat** di baris `Niat` bila terjadi | Ruang kombinasinya terlalu besar untuk diuji manual; yang bisa dilakukan adalah mengamati gejalanya lewat B1–B5 |
| `traffic()` yang sengaja tidak ikut dikunci tidak pernah mengembalikan angka yang menyesatkan | Pembacaan jalur pemanggilannya (`awaitHandshake`, tiker UI) | Laju trafik sesaat tidak akurat; tidak memengaruhi sambungan | Butuh pengukuran konkuren, bukan pengamatan layar |
| Pemulihan dari fallback penyimpanan polos tidak kehilangan data pada perangkat yang keystore-nya rusak permanen | Kode `Prefs.open()` + uji unit format | Pengguna perlu daftar ulang sekali (sudah dinyatakan di TODO 78) | Butuh perangkat dengan keystore rusak — tidak tersedia |

Bila suatu hari ada di antaranya yang terbukti salah lewat gejala di layar, barisnya dipindah
ke tabel **Hasil** dengan vonis `GAGAL` dan dibuka item TODO — bukan dihapus dari bagian ini.
