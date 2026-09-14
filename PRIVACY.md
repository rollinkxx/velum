# Privasi Velum

Velum adalah klien tunnel Cloudflare WARP. Halaman ini mendokumentasikan izin yang
diminta aplikasi, data apa yang disimpan/dikirim, dan mengapa — dalam Bahasa
Indonesia, sebagaimana seluruh dokumen repo ini.

## Izin Android

| Izin | Untuk apa | Catatan |
|---|---|---|
| `INTERNET` | Membangun tunnel WireGuard dan memanggil API registrasi | Wajib; inti aplikasi |
| `ACCESS_NETWORK_STATE` | Pemantau sambung ulang (`ReconnectMonitor`) tahu kapan jaringan berganti | Tidak dipakai untuk analitik |
| `RECEIVE_BOOT_COMPLETED` | Menyambung ulang tunnel setelah boot/perbaruan aplikasi, hanya bila terakhir memang tersambung | Dapat dicegah pengguna: putuskan sebelum mematikan perangkat |
| `POST_NOTIFICATIONS` | Notifikasi status "Tersambung" | Ditolak pun aplikasi tetap bekerja |

**`QUERY_ALL_PACKAGES` tidak dipakai — dengan sengaja.** Layar "Kecualikan aplikasi"
(split tunneling) hanya perlu daftar aplikasi yang bisa diluncurkan pengguna, dan itu
dicapai dengan blok `<queries>` (intent `MAIN`/`LAUNCHER`) di manifest: sistem hanya
memperlihatkan aplikasi peluncur. Visibilitas penuh ke semua paket tidak pernah
diperlukan, sehingga tidak ada yang perlu diberi `maxSdkVersion` atau dibatasi —
izinnya memang tidak ada. Daftar paket yang dibaca dipakai langsung oleh
`Interface.Builder.excludeApplications` WireGuard (padanannya
`VpnService.Builder.addDisallowedApplication()`); pendekatan picker sistem
(`ACTION_PICK_ACTIVITY`) sengaja tidak dipakai karena `<queries>` sudah memberi batas
yang sama dengan pengalaman multi-pilih yang lebih baik.

## Data yang disimpan di perangkat

Disimpan di `EncryptedSharedPreferences` (AES256-GCM, kunci dari Android Keystore),
tanpa fallback polos (lihat `SECURITY.md`):

- kunci privat WireGuard perangkat, token registrasi, ID perangkat;
- konfigurasi terakhir (alamat tunnel, kunci publik peer, endpoint);
- preferensi pengguna: daftar aplikasi yang dikecualikan, endpoint manual, niat
  "terakhir tersambung", dan rekaman diagnostik ringkas (hasil uji terakhir, rekaman
  upaya sambung ulang saat boot).

Tidak ada akun, email, atau profil pengguna. Tidak ada SDK analitik, iklan, atau
pelacak — satu-satunya dependensi jaringan adalah library tunnel WireGuard.

## Data yang keluar dari perangkat

| Tujuan | Kapan | Isi |
|---|---|---|
| `api.cloudflareclient.com` | Registrasi/penghapusan perangkat | Kunci PUBLIK WireGuard, model "Android", locale, UUID acak sekali pakai (bukan identitas perangkat keras), token bearer pada pemanggilan berikutnya. Host ini dipin sertifikatnya (`SECURITY.md`) |
| `www.cloudflare.com`, `one.one.one.one` (`/cdn-cgi/trace`) | Uji koneksi atas permintaan pengguna / otomatis setelah tersambung | Tidak ada yang dikirim selain permintaan GET standar; respons dipakai membaca status `warp=` |
| `cloudflare-dns.com` (DoH) | Maksimal 1×/24 jam saat menyambung | Kueri DNS `A` untuk `engage.cloudflareclient.com` via HTTPS — untuk menyegarkan kandidat endpoint |
| PoP WARP (`162.159.x.x`, `188.114.x.x`) | Selama tunnel tersambung | Seluruh lalu lintas terenkapsulasi WireGuard — inilah layanan yang memang dipilih pengguna |

## Ringkasan diagnostik ("Salin diagnostik")

Disusun agar aman ditempel ke laporan gangguan: **tanpa** kunci privat, token, ID
perangkat, dan alamat IP pengguna. Baris `Endpoint` memuat alamat IP PoP anycast
Cloudflare (dipakai bersama banyak pelanggan), ditambah keadaan internal teknis
(boolean/angka/durasi). Detailnya dijaga oleh `VelumDiagnosticsTest`.

## Log

Pada build rilis/preview, log level sensitif (`d`/`i`) no-op total lewat gerbang
`BuildConfig.DEBUG` di `VelumLog`, dan R8 membuang `Log.d`/`Log.v` sebagai lapis
kedua. Log yang memuat alamat IP endpoint hanya ada di level sensitif itu.
