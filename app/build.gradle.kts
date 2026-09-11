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

dependencies {
    implementation(libs.androidx.appcompat)
    // Dipakai langsung untuk Activity Result API (izin VPN & notifikasi).
    implementation(libs.androidx.activity)
    implementation(libs.wireguard.tunnel)
    implementation(libs.androidx.security.crypto)

    // Pengujian unit murni JVM: logika VelumFormat & keputusan uji (tidak ikut ke APK).
    testImplementation(libs.junit)
}
