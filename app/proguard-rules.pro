# Aturan R8 untuk varian yang diperkecil (release & preview).
#
# Prinsip: sekecil mungkin, tetapi cukup. Setiap aturan di bawah menjawab risiko
# nyata — bukan jaring pengaman asal-asalan seperti `-keep class **`, yang akan
# membatalkan sebagian besar manfaat R8.

# --- WireGuard (JNI) ---
# GoBackend memanggil kode Go lewat JNI: nama kelas & metode dirujuk dari sisi
# native, sehingga obfuskasi memutus jembatan itu saat runtime.
-keep class com.wireguard.** { *; }
-dontwarn com.wireguard.**

# --- Tink / EncryptedSharedPreferences ---
# Prefs.kt menyimpan registrasi perangkat lewat EncryptedSharedPreferences, yang
# di dalamnya memakai Tink. Tink menyusun kunci sebagai protobuf dan membaca
# field-nya secara reflektif; bila field-nya diobfuskasi/dibuang, aplikasi tetap
# lolos kompilasi lalu CRASH saat pertama kali membuka penyimpanan — kegagalan
# terburuk yang bisa dialami pengguna karena registrasi ikut hilang.
#
# Aturan ini sengaja menyasar field pada turunan GeneratedMessageLite saja,
# bukan seluruh paket Tink, supaya kelas yang benar-benar tak terpakai tetap
# boleh dibuang R8.
-keepclassmembers class * extends com.google.crypto.tink.shaded.protobuf.GeneratedMessageLite {
  <fields>;
}
-dontwarn com.google.crypto.tink.**

# --- org.json ---
# VelumApi & VelumRegistration mem-parse respons server dengan org.json. Kelasnya
# disediakan sistem Android (android.jar), jadi cukup redam peringatan referensi.
-dontwarn org.json.**

# --- Komponen Android yang dirujuk lewat manifest ---
# Activity/Service/Receiver dinstansiasi sistem berdasarkan NAMA dari manifest.
# Aturan bawaan AGP sudah menjaganya, tetapi ubin pengaturan cepat (TileService)
# dan receiver boot pernah jadi sumber kegagalan senyap di proyek lain, sehingga
# ditegaskan di sini agar tidak bergantung pada perilaku default.
-keep class com.rollinkxx.velum.VelumTileService { *; }
-keep class com.rollinkxx.velum.BootReceiver { *; }

# --- Diagnostik ---
# Simpan nomor baris & nama berkas supaya laporan crash dari APK yang diperkecil
# tetap bisa dibaca setelah dipulihkan dengan mapping.txt.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
