import com.android.build.api.variant.FilterConfiguration.FilterType.ABI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.rollinkxx.velum"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.rollinkxx.velum"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
        // Hanya bahasa Indonesia; resource bahasa lain dari library dibuang agar APK kecil.
        resourceConfigurations += listOf("in")
    }

    signingConfigs {
        create("release") {
            // Hanya terisi di CI rilis lewat environment; lokal sengaja kosong
            // sehingga build debug tidak terdampak.
            val keystorePath = System.getenv("KEYSTORE_FILE")
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    // Pemecahan APK per arsitektur. Isi APK ini didominasi pustaka native WireGuard
    // (satu `.so` per ABI, ±2 MB masing-masing) — bukan kode Kotlin yang hanya ±1.900
    // baris. R8 tidak menyentuh `.so`, jadi memecah per ABI adalah satu-satunya cara
    // menurunkan ukuran secara berarti: perangkat hanya mengunduh arsitekturnya sendiri.
    //
    // `isUniversalApk = true` tetap dipertahankan: distribusi lewat GitHub Releases
    // (bukan Play Store) berarti pengguna memilih berkas sendiri, dan yang tidak tahu
    // arsitektur ponselnya butuh satu berkas yang pasti berjalan di mana pun.
    splits {
        abi {
            isEnable = true
            reset()
            // 64-bit wajib (Play Store & mayoritas perangkat sejak 2019); armeabi-v7a
            // menjaga perangkat lama minSdk 24; x86_64 untuk emulator & Chromebook.
            // `x86` 32-bit sengaja dibuang: praktis tidak ada perangkat nyata memakainya.
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (System.getenv("KEYSTORE_FILE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    // Laporan teks dibutuhkan CI: laporan HTML tidak bisa dibaca dari log,
    // dan artifact tidak bisa diunduh dari sandbox agen (lihat AGENTS.md §5).
    lint {
        textReport = true
        textOutput = file("build/reports/lint-results-debug.txt")
        abortOnError = true
        warningsAsErrors = false
    }

    buildFeatures {
        buildConfig = false
        viewBinding = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes += setOf("META-INF/*.version", "kotlin/**", "DebugProbesKt.bin")
    }
}

// Setiap APK hasil pemecahan WAJIB punya versionCode berbeda. Bila dibiarkan sama,
// perangkat menolak memasang APK arsitektur lain sebagai pembaruan ("versi sama"),
// dan toko aplikasi tidak bisa memilih berkas yang tepat.
//
// Rumus: abiCode * 1000 + versionCode dasar. APK universal sengaja TIDAK diubah
// sehingga versionCode-nya paling rendah — itu yang diinginkan, agar APK spesifik
// arsitektur selalu lebih diutamakan daripada yang universal.
val abiCodes = mapOf("armeabi-v7a" to 1, "x86_64" to 2, "arm64-v8a" to 3)

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val abi = output.filters.find { it.filterType == ABI }?.identifier
            val base = abiCodes[abi]
            if (base != null) {
                output.versionCode.set(base * 1000 + (output.versionCode.get() ?: 0))
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    // Dipakai langsung untuk Activity Result API (izin VPN & notifikasi).
    implementation(libs.androidx.activity)
    implementation(libs.wireguard.tunnel)
    implementation(libs.androidx.security.crypto)

    // Pengujian unit murni JVM: logika VelumFormat & keputusan uji (tidak ikut ke APK).
    testImplementation(libs.junit)
    testImplementation(libs.json)
}
