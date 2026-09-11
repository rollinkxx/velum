# Rilis GitHub (APK bertanda tangan)

Distribusi Velum saat ini hanya via GitHub Releases (tanpa Play Store).
Job CI `assembleRelease (bertanda tangan)` membangun APK rilis bila Secrets dan
variable di bawah disiapkan. Tanpa itu job di-skip (bukan gagal).

## 1. Buat keystore sekali saja

Di mesin maintainer (butuh JDK):

```sh
keytool -genkeypair -v -keystore velum-release.keystore \
  -alias velum -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 velum-release.keystore > velum-release.keystore.b64
```

> Linux memakai `base64 -w0`; di macOS pakai `base64 -i`.
> **Cadangkan `velum-release.keystore` + password di tempat aman.**
> Keystore hilang = aplikasi tidak bisa di-update (harus publish ulang sebagai app baru).

## 2. Set Secrets + variable repo

```sh
gh secret set SIGNING_KEYSTORE_BASE64 < velum-release.keystore.b64
gh secret set KEYSTORE_PASSWORD   # password keystore
gh secret set KEY_ALIAS           # isi: velum
gh secret set KEY_PASSWORD        # password key (biasanya sama)
gh variable set ENABLE_RELEASE_SIGNING --body true
```

Hapus file `.b64` setelah diunggah; jangan commit keystore ke repo.

## 3. Picu build rilis

Set variable tidak memicu workflow — picu manual:

```sh
gh workflow run build --ref main
gh run watch --exit-status
```

Atau dorong commit kode apa pun (job rilis ikut berjalan setelah `assembleDebug` hijau).

## 4. Ambil & verifikasi APK

```sh
gh run download <id-run> -n app-release
apksigner verify --print-certs app-release.apk
```

`apksigner` ada di Android SDK build-tools. Alternatif cepat: pasang APK di
perangkat dan pastikan `applicationId` `com.rollinkxx.velum` + versi sesuai.

## 5. Terbitkan Release

```sh
gh release create v0.1.0 app-release.apk \
  --title "Velum 0.1.0" \
  --notes "Catatan rilis: salin dari CHANGELOG bagian rilis terkait."
```

Versi (`versionName`/`versionCode`) hanya di-bump atas perintah eksplisit
(lihat AGENTS.md §4); sesuaikan tag dengan versi di `app/build.gradle.kts`.
